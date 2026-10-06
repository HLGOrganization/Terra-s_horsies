# Terra's horsies [TFC x icy's horses]（大地上的马驹们）

把 [TerraFirmaCraft](https://modrinth.com/mod/terrafirmacraft)（TFC）和
[Icy's Better Horses](https://github.com/Icys-Better-Horses)（Icy）接起来的
**Minecraft 1.20.1 / Forge** 附属模组。

一句话：**让 Icy 的马真正生活在 TFC 的世界里** —— 拥有 TFC 的亲密度、性别、怀孕、年龄与掉落；
TFC 的驴和骡也能享受到 Icy 的改造；马的重量与生成气候都按 TFC 的规则走。

- 模组 ID：`terras_horsies`
- 中文名：**大地上的马驹们**
- 构建产物：`build/libs/terras-horsies-1.0.0.jar`

## 依赖

| 模组 | 版本 | 说明 |
|------|------|------|
| [TerraFirmaCraft](https://modrinth.com/mod/terrafirmacraft) | `1.20.1-3.2.26` | **必需** |
| [Icy's Better Horses](https://github.com/Icys-Better-Horses) | `2.0.6` | **必需** |
| [Jade](https://modrinth.com/mod/jade) | `11.x` | 可选 —— 悬停显示 TFC 同款马匹信息 |
| [More Attributes](https://github.com/HLGOrganization/More-Attributes) | `1.0.2` | 可选 —— 负重系统 |
| AstikorCarts TFC | `1.1.8.3` | 可选 —— 马车牵引 |

只装前两个就能正常游玩；可选模组缺席时对应功能自动关闭，不会报错。

## 这个模组做了什么

### 打通 TFC 与 Icy

- Icy 对马匹的改造（性别、年龄、掉落、喂食）**同样作用于 TFC 的马、驴、骡**。
- 两边的**性别判断统一**，从根上避免同性或错配繁殖。
- 马匹可与 TFC 的**驴双向交配**。

### TFC 亲密度

- Icy 的 15 个品种获得 TFC 同款**亲密度**：喂食增长，长期不喂则缓慢衰减。
- 喂食分两套：**血量未满时吃东西只回血**（不给亲密度），血量满后的**每日正餐**才加亲密度。
- 亲密度是繁殖与骑乘驯服的判据。

### 繁殖与成长

- 母马拥有 TFC 同款**怀孕期**，不再瞬间产仔；**不需要金苹果**。
- 年龄**统一由 TFC 的生日决定**；野外生成的马不再全是幼驹。
- 衰老会**线性降低速度**（满衰老 70%）并削弱后代。

### 吃与喂

- 新增物品标签 **`#terras_horsies:horse_food`**：带此标签的食物都能喂马、也能吸引马。
- 默认包含 TFC 的谷物与水果；可用数据包自行增删。

### 战斗

- 默认是**中立**生物：**不无故攻击他人**，只在**自身被打**或**主人被打**时出手。
- 脱战同时看**时间**与**距离**，而且时间**续时** —— 敌人继续动手就延长，停手才开始倒计时，
  不会因为一次挨打就追杀到天涯。
- 反击时长按**亲密度**分档；替主人报仇固定 5 秒（两者都续时）。
- **野马**血量不足或附近有马匹死亡时会逃跑；**穿马铠免疫仙人掌刺伤**，野马被扎会挪开一点。
- 被**非生物来源**伤害时会主动挪开：岩浆、岩浆块、火焰、甜浆果丛、掉落的铁砧／TNT……
  每次挨伤都续一次时限，**一直逃到不再受伤为止**。这一档**家养马同样生效**（烧死谁都不行），
  被骑着时让位给玩家。

### 骑乘与负重

- **骑乘驯服的概率改由亲密度决定**：0 → 1% / 6 → 5% / 18 → 20% / 35 → 90% / >35 → 必定驯服，
  并与 **More Attributes** 的「力量 + 技巧」等级联动加成。
  Icy 的 15 个品种与 **TFC 自己的马、驴、骡**都适用。
- 五类马各有负重上限（竞速 900 / 矮马 1600 / 西部 1800 / 战马 2000 / 挽马 4800）；
  骑乘时坐骑会继承**骑手自身体重与背包**，超重则按比例减速。
- 马匹牵引马车时继承整车重量，**挽马额外降至 40%**；TFC 马车的击倒／过载／力竭效果被移除。
  车厢里装货、卸货**即时反映到马身上**（不用重新挂车），空车重量也会立刻降回来。
- TFC 的**驴／骡装上箱子后，箱子里的货物也算它们的负重**（带在身上，走到哪压到哪）。
- 车厢的**物品尺寸上限已解除**：能装多重由负重说了算，不再有"太大装不进去"。
- **吹哨召回马匹时会先"放手"**：卸掉挂着的马车、请骑手与乘客下来，然后才传送。
  再也不会出现车厢跟着马直线飞过来的场面（送马回马厩同样如此）。

### 外观与信息

- TFC 的 **9 种金属马铠**拥有 Icy 风格贴图（8 品种 × 9 金属，纯资源、无代码）。
- 装 **Jade** 后悬停可看到亲密度、性别、年龄与怀孕信息。

### 气候生成

- Icy 的 15 个品种接入 TFC 的 fauna 生成规则（温度／降雨／森林密度）。
  这部分用 KubeJS 脚本实现，**模组本体不参与**，脚本见开发文档第 16 节。

## 安装

1. 装好 Minecraft **1.20.1** + **Forge 47.x**。
2. 把 TFC、Icy's Better Horses 与本模组的 jar 一起放进 `mods/`。
3. 启动即可，无需额外配置。

## 配置

配置文件：`config/terras_horsies-common.toml`（注释为英文）。

几乎所有数值都能调：负重上限、战斗时长与脱战距离、逃跑阈值、驯服概率曲线、
回血系数、仙人掌免疫开关等等。改完保存即生效，少数只在启动时读取的项需要重启。

> 若 Forge 在升级后往文件里补了新键，**保留配置文件即可**，你调过的值不会丢。

## 从 `tfc_icys_horses` 升级

模组 ID 由 `tfc_icys_horses` 改为 `terras_horsies`，升级需要做三件事：

1. 删掉 `mods/` 里的旧 jar —— 否则两个 modId 会同时加载，两套 mixin 争抢同一批目标方法。
2. 把 `config/tfc_icys_horses-common.toml` 改名成 `terras_horsies-common.toml`，你调过的值就都在。
3. 资源命名空间跟着换：`#tfc_icys_horses:horse_food` → `#terras_horsies:horse_food`。

## 构建

```powershell
$env:JAVA_HOME = '<JDK 17 路径>'
.\gradlew.bat build --no-daemon
```

需要 **JDK 17**。Icy 与 Jade 不在 Maven 仓库上，需自行把 jar 放进 `libs/` ——
它们只是**编译期依赖**，不会被打包或再分发，因此也不在版本库里。版本号与细节见[开发文档](docs/DEV_NOTES.md)。

打包后请再跑一遍注入点自检 —— 它能挡住"编译通过、进游戏启动即崩"的那类 mixin 错误
（回调类型写错、参数个数不符、漏登记 mixin）：

```powershell
python tools\check_mixin_injectors.py
```

## 授权与致谢

本附属模组以 **MIT** 授权，见 [`LICENSE`](LICENSE)。

- 感谢 **[TerraFirmaCraft](https://modrinth.com/mod/terrafirmacraft)** 与
  **[Icy's Better Horses](https://github.com/Icys-Better-Horses)** —— 没有这两个模组就没有本项目。
- 本模组的 jar 里**只有自己的代码与贴图**，不含任何上游 class 或二进制素材；
  第三方模组的 jar 仅作编译期依赖，不进仓库、也不再分发。
- 唯一的灰色地带是那 72 张马铠贴图：经像素级比对，它们是 Icy 贴图的**调色板替换派生作品**。
  完整的比对数据、授权分析与后续处理方式见[开发文档的许可一节](docs/DEV_NOTES.md#许可)。

---

**完整开发文档**（逆向结论、字节码证据、mixin 注入点、踩坑记录）：
[`docs/DEV_NOTES.md`](docs/DEV_NOTES.md)
