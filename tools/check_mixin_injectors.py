#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""构建后静态核对所有 Mixin 注入点的回调类型。

## 为什么需要这个脚本

Mixin 对注入处理函数的签名有一条硬规则：

    目标方法有返回值  →  最后一个参数必须是 CallbackInfoReturnable<?>
    目标方法返回 void  →  最后一个参数必须是 CallbackInfo

**违反这条规则编译期毫无提示**：javac 不管，注解处理器不管，构建照样 SUCCESSFUL。
错误只在游戏启动、Mixin apply 阶段以 `InvalidInjectionException` 爆出来；
而本模组的 `terras_horsies.mixins.json` 是 `"required": true`，
出错不是跳过该 mixin 而是 FATAL 崩溃。

2026-02 就栽在这上面一次：`AbstractHorseTamingMixin` 给
`boolean tameWithName(Player)` 挂了 `CallbackInfo`，客户端启动即崩。
refmap 里那条 `m_30637_(Lnet/minecraft/world/entity/player/Player;)Z`
结尾的 `Z` 早就把答案写着了，只是没人去比对。

所以把这个比对自动化：**构建之后、交付之前**跑一遍，退出码非 0 就说明不能发。

## 判据从哪来

* 目标方法的返回值与参数个数 —— 优先取注解里写全的描述符
  （`method = "canUse()Z"`，本模组给 `remap = false` 的模组方法都是这个写法）；
  只写了名字的（`method = "hurt"`）就去构建产物里的
  `terras_horsies.refmap.json` 查，那里有 Mixin 注解处理器写下的完整 SRG 描述符。
* 两处都查不到的（既没写描述符、refmap 里也没有）**不判失败**，
  只报 SKIP —— 那说明这个注入点本身就解析不了，是另一类问题。

## 用法

    python tools/check_mixin_injectors.py
    python tools/check_mixin_injectors.py --source <mixin 源码目录> --jar <jar 路径>

默认在仓库根目录（本文件的上一级）下找 `src/main/java/com/tfcicys/horses/mixin`
与 `build/libs/terras-horsies-1.0.0.jar`。
"""

import argparse
import json
import os
import re
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
DEFAULT_SOURCE = os.path.join(ROOT, "src", "main", "java", "com", "tfcicys", "horses", "mixin")
DEFAULT_JAR = os.path.join(ROOT, "build", "libs", "terras-horsies-1.0.0.jar")
REMAP_ENTRY = "terras_horsies.refmap.json"


def strip_comments(text):
    """去掉注释，免得 javadoc 里提到的 @Inject(...) 被当成真的注解。"""
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    text = re.sub(r"//[^\n]*", "", text)
    return text


def balanced(text, start):
    """从 text[start] == '(' 开始，返回内容与右括号的下标。"""
    depth = 0
    for i in range(start, len(text)):
        if text[i] == "(":
            depth += 1
        elif text[i] == ")":
            depth -= 1
            if depth == 0:
                return text[start + 1:i], i
    return None, -1


def split_top_level(text):
    """按顶层逗号切分（不切泛型里的逗号）。"""
    out, depth, cur = [], 0, ""
    for ch in text:
        if ch in "<([":
            depth += 1
        elif ch in ">)]":
            depth -= 1
        if ch == "," and depth == 0:
            out.append(cur)
            cur = ""
        else:
            cur += ch
    if cur.strip():
        out.append(cur)
    return [p.strip() for p in out if p.strip()]


def normalize_param(text):
    """剥掉参数前面的 final 与注解，只留下类型。"""
    text = text.strip()
    text = re.sub(r"^(?:final\s+)+", "", text)
    text = re.sub(r"^(?:@[\w.]+(?:\s*\([^)]*\))?\s*)+", "", text)
    return re.sub(r"^(?:final\s+)+", "", text).strip()


def method_decl_after(text, pos):
    """注解之后第一个方法声明，返回 (名字, 参数列表) 或 None。

    方法名允许含 `$`（本模组的处理方法都叫 tfcicys$xxx），参数允许带 final。
    """
    m = re.compile(
        r"\s*(?:@[\w.]+(?:\s*\([^)]*\))?\s*)*"          # 可能还有别的注解
        r"(?:(?:public|private|protected|static|final|abstract|synchronized|native)\s+)*"
        r"[\w.<>\[\],?\s]+?\s+([\w$]+)\s*\(([^)]*)\)\s*\{"
    ).match(text, pos)
    if not m:
        return None
    params = [normalize_param(p) for p in split_top_level(m.group(2))]
    return m.group(1), [p for p in params if p]


def descriptor_arg_count(desc):
    """数出 `(Lnet/...;F[I)V` 里的参数个数。"""
    args = desc[1:desc.rindex(")")]
    n, i = 0, 0
    while i < len(args):
        ch = args[i]
        if ch == "[":
            i += 1
            continue
        if ch == "L":
            i = args.index(";", i) + 1
        else:
            i += 1
        n += 1
    return n


def descriptor_return(desc):
    return desc[desc.rindex(")") + 1:]


def load_refmap(jar):
    if not os.path.isfile(jar):
        return None, "找不到构建产物：%s" % jar
    with zipfile.ZipFile(jar) as z:
        if REMAP_ENTRY not in z.namelist():
            return None, "%s 里没有 %s" % (jar, REMAP_ENTRY)
        return json.loads(z.read(REMAP_ENTRY).decode("utf-8")), None


def refmap_lookup(refmap, name):
    """在 refmap 里按方法名找完整描述符，返回所有匹配。"""
    hits = []
    for section in ("mappings", "data"):
        node = refmap.get(section)
        if not isinstance(node, dict):
            continue
        stack = [node]
        while stack:
            cur = stack.pop()
            for k, v in cur.items():
                if isinstance(v, dict):
                    if k == name and isinstance(v, str) and "(" in v:
                        hits.append(v)
                    stack.append(v)
                elif k == name and isinstance(v, str) and "(" in v:
                    hits.append(v)
    return hits


def check_file(path, refmap):
    """返回 (problems, checked, skipped)。problems 里是 (文件, 行号, 说明)。"""
    with open(path, encoding="utf-8") as fh:
        raw = fh.read()
    text = strip_comments(raw)
    problems, checked, skipped = [], 0, 0

    for m in re.finditer(r"@Inject\s*\(", text):
        args, close = balanced(text, m.end() - 1)
        if args is None:
            continue
        mm = re.search(r'method\s*=\s*"([^"]+)"', args)
        if not mm:
            continue
        target = mm.group(1)
        cancellable = re.search(r"cancellable\s*=\s*true", args) is not None
        line = raw[:m.start()].count("\n") + 1

        decl = method_decl_after(text, close + 1)
        if not decl:
            problems.append((path, line, "找不到 %s 之后的处理方法声明" % target))
            continue
        handler, params = decl

        # 目标方法的返回值与参数个数
        desc = None
        if re.match(r"^[\w$]+\(.*\)", target):     # 描述符写法：canUse()Z
            desc = target[target.index("("):]
        else:
            hits = refmap_lookup(refmap, target)
            if hits:
                desc = hits[0][hits[0].index("("):]
        if desc is None:
            skipped += 1
            print("  SKIP  %-46s %s -> %s（refmap 无记录，无法核对）"
                  % (os.path.basename(path), target, handler))
            continue

        checked += 1
        returns_value = descriptor_return(desc) != "V"
        last = params[-1] if params else ""
        is_cir = last.startswith("CallbackInfoReturnable")
        is_ci = not is_cir and last.startswith("CallbackInfo")

        if returns_value and not is_cir:
            problems.append((path, line,
                             "%s 的目标 %s%s 有返回值，回调必须是 CallbackInfoReturnable，"
                             "现在是 %s" % (handler, target, desc, last or "(空)")))
        elif not returns_value and not is_ci:
            problems.append((path, line,
                             "%s 的目标 %s%s 返回 void，回调必须是 CallbackInfo，现在是 %s"
                             % (handler, target, desc, last or "(空)")))

        # 参数个数：目标参数 + 1 个回调
        want = descriptor_arg_count(desc) + 1
        if len(params) != want:
            problems.append((path, line,
                             "%s 的参数个数是 %d，目标 %s%s 需要 %d 个（含回调）"
                             % (handler, len(params), target, desc, want)))

        # setReturnValue 必须配 cancellable。
        # 窗口只到下一个 @Inject 为止 —— 用固定长度会扫进下一个处理方法，造成误报。
        next_at = text.find("@Inject", close)
        body = text[close:next_at if next_at > 0 else len(text)]
        if "setReturnValue" in body and not cancellable:
            problems.append((path, line,
                             "%s 调用了 setReturnValue 但注解没有 cancellable = true" % handler))

    return problems, checked, skipped


def check_registration(jar, mixin_config="terras_horsies.mixins.json"):
    """jar 里的 mixin 类与 mixins.json 的登记是否一致。

    漏登记不会报错，只是那个 mixin **永远不会被应用** —— 又是一类"编译通过、
    构建通过、进游戏毫无效果"的静默失效。反过来，登记了但类不在 jar 里，
    会因为 `"required": true` 直接崩。
    """
    problems = []
    with zipfile.ZipFile(jar) as z:
        names = z.namelist()
        if mixin_config not in names:
            return ["%s 里没有 %s" % (jar, mixin_config)]
        conf = json.loads(z.read(mixin_config).decode("utf-8"))
        registered = []
        for key in ("mixins", "client", "server"):
            registered.extend(conf.get(key, []))
        prefix = "com/tfcicys/horses/mixin/"
        present = sorted(
            n[len(prefix):-len(".class")]
            for n in names
            if n.startswith(prefix) and n.endswith(".class") and "$" not in n)

    for cls in present:
        if cls not in registered:
            problems.append("mixin 类 %s 在 jar 里，但 %s 没登记它 —— 它不会生效"
                            % (cls, mixin_config))
    for cls in registered:
        if cls not in present:
            problems.append("%s 登记了 %s，但 jar 里没有这个类 —— 启动会崩"
                            % (mixin_config, cls))
    return problems


def main():
    ap = argparse.ArgumentParser(description="静态核对 Mixin 注入点的回调类型")
    ap.add_argument("--source", default=DEFAULT_SOURCE, help="mixin 源码目录")
    ap.add_argument("--jar", default=DEFAULT_JAR, help="构建产物 jar（取里面的 refmap）")
    args = ap.parse_args()

    refmap, err = load_refmap(args.jar)
    if refmap is None:
        print("!! %s" % err)
        return 2

    files = sorted(os.path.join(args.source, f)
                   for f in os.listdir(args.source) if f.endswith(".java"))
    print("核对 %d 个源文件（refmap：%s）" % (len(files), os.path.basename(args.jar)))
    all_problems, total, skipped = [], 0, 0
    for path in files:
        problems, checked, sk = check_file(path, refmap)
        total += checked
        skipped += sk
        all_problems.extend(problems)

    reg = check_registration(args.jar)
    all_problems.extend(("mixins.json", 0, msg) for msg in reg)

    print("")
    if all_problems:
        print("发现 %d 个问题（这类问题编译期不会报，只会在游戏启动时崩或静默失效）："
              % len(all_problems))
        for path, line, msg in all_problems:
            where = "%s:%d" % (path, line) if line else path
            print("  FAIL  %s\n        %s" % (where, msg))
        return 1

    print("OK：核对 %d 个注入点，回调类型与参数个数全部匹配（跳过 %d 个无法解析的）；"
          "mixin 登记与 jar 内容一致。" % (total, skipped))
    return 0


if __name__ == "__main__":
    sys.exit(main())
