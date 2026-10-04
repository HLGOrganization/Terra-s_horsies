# Terra's horsies [TFC x icy's horses]（大地上的马驹们）

把 [TerraFirmaCraft](https://modrinth.com/mod/terrafirmacraft)（TFC）和
[Icy's Better Horses](https://github.com/Icys-Better-Horses)（Icy）连起来的
**Minecraft 1.20.1 / Forge** 附属模组。

- 模组 ID：`terras_horsies`（1.0.0 之前为 `tfc_icys_horses`）
- 显示名：**Terra's horsies [TFC x icy's horses]**，中文名 **大地上的马驹们**
- 构建产物：`build/libs/terras-horsies-1.0.0.jar`
- 依赖：TFC `1.20.1-3.2.26`（必需）、Icy's Better Horses `2.0.6`（必需）、
  [Jade](https://modrinth.com/mod/jade) `11.x`（**可选**，用于第 7 节的悬停信息）、
  [More Attributes](https://github.com/HLGOrganization/More-Attributes) `1.0.2`（**可选**，
  用于第 14 节的负重）、AstikorCarts TFC `1.1.8.3`（**可选**，同上）

### ⚠️ 从 `tfc_icys_horses` 升级上来

模组 ID 改了，所以升级时**必须**做下面几件事，否则会加载两份或丢掉你调过的配置：

1. **删掉 `mods/` 里的旧 jar**（`tfc-icys-horses-*.jar`）。新旧两个 modId 会同时加载，
   两套 mixin 争抢同一批目标方法。
2. **把配置文件改名**：`config/tfc_icys_horses-common.toml` → `config/terras_horsies-common.toml`。
   Forge 是按 modId 生成文件名的，改名后你之前调的数值就都还在。
3. 资源命名空间跟着换：`#tfc_icys_horses:horse_food` → `#terras_horsies:horse_food`
   （`#tfc:horse_food` 里的转发已同步更新）。

**刻意没有改** 的东西 —— 它们要么对玩家不可见，要么改了会破坏存档或 refmap：

| 保留项 | 原因 |
|--------|------|
| Java 包名 `com.tfcicys.horses`、类名 `TFCICYS*` / `TfcIcysHorses` | 内部标识，重命名会牵动全部源文件与 `refmap`，玩家看不到 |
| mixin 成员前缀 `tfcicys$` | 同上 |
| 马匹 NBT 里持久化的 `tfcicys_sire_wear` | **存档数据键**，改了会让已有马匹的父系衰老记录失效 |
| 工程目录名 `tfc-icys-horses/`、Gradle 工程名以外的路径 | 与分发的 jar 无关 |

---

## 功能一览

| # | 功能 | 实现位置 |
|---|------|----------|
| 1 | Icy 的马匹功能覆盖 TFC 的**马、驴、骡** | `data/icys-better-horses/tags/entity_types/horses.json` |
| 2 | Icy 马匹拥有 TFC 同款**亲密度** | `mixin/BhBreedHorseFamiliarityMixin.java` |
| 3 | 新增 **`horse_food`** 标签，马匹可食用带此标签的食物 | `data/terras_horsies/tags/items/horse_food.json` |
| 4 | TFC 与 Icy 的**性别判断统一**，从根上避免同性/错配 | `GenderSyncHandler.java` + 上述 mixin |
| 5 | 马匹可食用 TFC 食物；**喂食分两套**：驯服后血量未满吃东西只回血（不给亲密度），血满后的每日正餐才给亲密度 | `mixin/AbstractHorseFoodMixin.java`、`mixin/BhBreedHorseFamiliarityMixin.java`（见第 5 节） |
| 6 | Icy 母马拥有 TFC 同款**怀孕期**，不再瞬间产仔 | `mixin/BhBreedHorseFamiliarityMixin.java` |
| 7 | **Jade** 悬停显示 TFC 同款详细信息 | `compat/IcyHorseJadePlugin.java` |
| 8 | Icy 全部 15 种马的**掉落物与 TFC 马一致** | `IcyHorseLootHandler.java` |
| 9 | **衰老值线性降低马匹速度**，100% 时降为 70% | `mixin/BhBreedHorseFamiliarityMixin.java` |
| 10 | 马匹可与 TFC 的**驴**交配（双向） | `mixin/BhBreedHorseFamiliarityMixin.java` |
| 11 | 马匹会被**手持食物吸引** | `mixin/BhBreedHorseFamiliarityMixin.java` |
| 12 | 移除 TFC 马车的**击倒／过载／力竭**效果 | `mixin/SupplyCartOverburdenMixin.java`、`mixin/AnimalCartOverburdenMixin.java` |
| 13 | 马车货物按 **More Attributes 重量 ×60%** 继承给拉车者，另加 512 固定自重 | `load/LoadManager.java` |
| 14 | 生物默认负重上限 **1000**，Icy 五类马按 900/1600/1800/2000/4800 覆盖，全部可配置 | `TFCICYSConfig.java`、`load/HorseCategory.java` |
| 15 | 玩家骑乘时坐骑继承 **350**（挽马 300，可双载）**加上骑手自身负重** | `load/LoadManager.java` |
| 16 | 马匹牵引马车时继承整车重量，**挽马额外降至 40%**（对玩家无效） | `load/LoadManager.java` |
| 17 | 衰老 100% 时**负重上限降为 60%** | `load/LoadManager.java` |
| 18 | TFC **9 种金属马铠**拥有 Icy 风格外观（8 品种 × 9 金属 = 72 张贴图，纯资源、无代码） | `assets/icys-better-horses/textures/entity/horse/*/armor/tfc/` |
| 19 | Icy 15 个品种接入 TFC 的 **fauna 气候生成**（温度／降雨／森林密度） | KubeJS 脚本，模组本体不参与（见第 16 节） |
| 20 | Icy 马匹的战斗行为：**不无故攻击他人**，只在**自身被打**或**主人被打**时出手（替主人报仇是 Icy 原版行为，**默认保留**）；反击时长按 **TFC 亲密度**分档、报仇固定 **5 秒**，两者都**续时**（敌人继续动手就延长，停手才倒计时）；**野马**血量不足或附近有马匹死亡时会**逃跑** | `mixin/BhHorseCombatAlertDefendMixin.java`、`mixin/HorseCombatRetaliateMixin.java`、`mixin/DefendOwnerGoalAggroMixin.java`、`mixin/AbstractHorseDeathFrightMixin.java`、`mixin/AbstractHorseThreatMixin.java`、`ai/FleeFromThreatGoal.java`（见第 17 节） |
| 21 | Icy 马匹**穿着任意马铠时免疫仙人掌刺伤**；**野马被仙人掌扎到会挪开一点**（短距离、脱离那丛即停，含其他模组的仙人掌） | `mixin/AbstractHorseCactusMixin.java`、`util/CactusBlocks.java`（见第 18 节） |
| 22 | **骑乘驯服的概率改由 TFC 亲密度决定**（0→1%／6→5%／18→20%／35→90%／>35→100%），并与 **More Attributes** 的「力量+技巧」联动加成 | `mixin/BhBreedHorseFamiliarityMixin.java`（见第 19 节） |
| 23 | **年龄统一由 TFC 的生日决定**；野外生成补生日戳（避免刷出来的马全是幼年）；马匹面板的预览实体不再一律显示为幼驹 | `mixin/BhBreedHorseFamiliarityMixin.java`（见第 20 节） |

---

## 1. 让 Icy 的功能覆盖 TFC 的马科动物

Icy 用实体类型标签 `icys-better-horses:horses` 决定哪些实体归它管理
（`BhHorseKind.MANAGED`）。我们只需往这个标签里补充 TFC 的三个实体：

`src/main/resources/data/icys-better-horses/tags/entity_types/horses.json`

```json
{
  "replace": false,
  "values": ["tfc:horse", "tfc:donkey", "tfc:mule"]
}
```

这是 Icy 官方文档指定的附属接入点（见 `_refs/icys-forge/guide/addons.md`），
不需要改动 Icy 的任何代码。

---

## 2. 给 Icy 马匹加上 TFC 亲密度

TFC 的牲畜都实现接口 `TFCAnimalProperties`。我们的 mixin 让 Icy 的
马匹基类 `BhBreedHorse` 也实现它——准确地说，是实现它的**最派生**子接口
`HorseProperties`：

```java
@Mixin(BhBreedHorse.class)
public abstract class BhBreedHorseFamiliarityMixin extends Horse implements HorseProperties
```

继承链是 `HorseProperties extends MammalProperties extends TFCAnimalProperties`，
所以实现最派生的那个就同时满足三层。这不只是图省事：**TFC 自己的 Jade 集成正是按
这三层做 `instanceof` 判断的**（见第 7 节），少了任何一层都会缺显示项。
`MammalProperties` 额外带来怀孕机制，`HorseProperties` 额外带来"可否骑乘"。

**为什么选 `BhBreedHorse` 而不是 `AbstractHorse`**：绑定到 `AbstractHorse`
会同时给原版马和 TFC 自己的 `TFCHorse`/`TFCChestedHorse` 加上亲密度，而
TFC 的动物已经实现了它——它们会在自己的 `tick()` 里再跑一次
`tickAnimalData()`，导致**每日衰减翻倍**。锁定 Icy 的共享基类，改动就恰好落在该落的地方。

### 数值与原版 TFC 完全一致

只实现 `TFCAnimalProperties` 要求的 9 个抽象成员，其余全部复用 TFC 的默认方法，
所以数值是**构造上相同**的，而不是抄一遍：

| 行为 | 数值 | 来源 |
|------|------|------|
| 每次喂食 | `+0.06` | TFC 默认 `eatFood` |
| 喂食频率 | 每个 TFC 日最多一次 | TFC 默认 `eatFood` |
| 每日衰减 | `-0.02` | TFC 默认 `tickAnimalData` |
| 衰减下限 | 低于 `familiarityDecayLimit`（0.3）才衰减 | `TFCConfig.SERVER` |
| 成年上限 | `horseConfig` 的 `getAdultFamiliarityCap()`（0.35） | TFC 自己的马匹配置 |

`animalConfig()` 直接返回 `TFCConfig.SERVER.horseConfig.inner()`，也就是说
**你在 TFC 配置里改马匹数值，Icy 的马会立刻跟着变**。

### 一个必须避开的 Java 陷阱

`AbstractHorse`（经由 `Horse`）有一个**具体**的 `isFood(ItemStack)` 方法，
它会**遮蔽**接口的 default 方法。TFC 自己在每个动物类里都显式覆写 `isFood`
就是为了绕开这点。所以 mixin 里也显式覆写：

```java
@Override
public boolean isFood(ItemStack stack) {
    if (super.isFood(stack)) {
        return true;
    }
    return (eatsRottenFood() || !FoodCapability.isRotten(stack)) && stack.is(getFoodTag());
}
```

`getAgeType()` 也做了覆写，返回 `isBaby() ? CHILD : ADULT`，目的是**绕开 TFC
按 TFC 历法推进的年龄/老龄机制**，避免它从 Icy 手里接管成长逻辑（亲密度不受影响）。

---

## 3. `horse_food` 标签

TFC 的猪吃 `tfc:pig_food`，牛吃 `tfc:cow_food`……马匹对应的标签是
`tfc:horse_food`。本模组把它对外开放：

`data/terras_horsies/tags/items/horse_food.json`（**给整合包作者用的入口**）

```json
{
  "replace": false,
  "values": ["#tfc:foods/grains", "#tfc:foods/fruits"]
}
```

`data/tfc/tags/items/horse_food.json`（**单向桥接**）

```json
{
  "replace": false,
  "values": [{"id": "#terras_horsies:horse_food", "required": false}]
}
```

> ⚠️ **这个桥接必须是单向的。** 如果让 `tfc:horse_food` 反过来引用自己，
> 就构成循环标签，数据包加载时会直接崩溃。
>
> 另外，`data/tfc/...` 是**覆写 TFC 自己的数据**。用 `"replace": false` +
> `"required": false` 保证只在 TFC 原标签上追加，且即使 TFC 改了标签名也不会
> 因为缺项而报错。

奇怪的食物（腐肉之类）由 `FoodCapability.isRotten` 把关，与 TFC 一致。

---

## 4. 性别统一

TFC 和 Icy **各有一套独立的性别系统**，两边都会随机分配。这可能造成：

> 一只 TFC 公驴被 Icy 随机判成雌性 → Icy 侧认为它可以和 Icy 的公马繁殖。

修法是让两边**永远一致**：在实体进入世界时同步一次性别。

`GenderSyncHandler.java` 监听 `EntityJoinLevelEvent`（该事件在 Icy 的
`readAdditionalSaveData` 恢复存档和 `finalizeSpawn` 随机分配**之后**触发，
因此这里是最终裁决点）：

- 实体是 `BhBreedHorse` → 以 **Icy 的性别为准**，写回 TFC
- 实体是 TFC 的马科动物 → 以 **TFC 的性别为准**，写回 Icy

性别一致之后，**同性禁配由 TFC 原生逻辑自动生效**，无需额外拦截：

```java
// TFCChestedHorse.java:166
otherAnimal instanceof TFCAnimalProperties other
    && this.getGender() != other.getGender()
    && this.isReadyToMate() && other.isReadyToMate()
```

### 与跨物种繁殖的关系

让 Icy 马匹实现 `TFCAnimalProperties` 后，TFC 的 `canMate` 会接受它
（`TFCHorse.java:206` 的判断是 `otherAnimal instanceof Horse`，Icy 的马确实是
`Horse` 子类）。

> **注意**：本模组**有意开启**了马 × TFC 驴的交配，实现方式见 **§12**。
> 下面这段记录的是开启之前的情形，保留下来是为了说明两侧各自有哪些同种检查需要绕过。

开启之前，配对会被两侧各自的同种检查挡住：

- **TFC 一侧**：`BreedBehavior` 在两处都要求同种——
  `animal.getType() == target.getType()`（`:104` 与 `:112`）。
- **Icy 一侧**：走原版 `AnimalMakeLove`，它用
  `getEntitiesOfClass(this.animal.getClass(), ...)` 精确筛选运行时类。

§12 逐一处理了这些检查点。性别一致（本节）仍然是前提——**同性依然不会配对**。

---

## 5. 马匹可食用 TFC 食物

### 5.1 让 tag 生效

`AbstractHorseFoodMixin` 往 `isFood` 的返回值上 OR 了 `TFCTags.Items.HORSE_FOOD`：

```java
@Inject(method = "isFood(Lnet/minecraft/world/item/ItemStack;)Z",
        at = @At("RETURN"), cancellable = true)
```

用 `RETURN` 而不是 `HEAD`，是为了**保留原版判断**（苹果、金苹果等照常有效），
只在原版返回 false 时才补上 TFC 的食物，因此和别的模组兼容。

> 对 TFC 自己的马科动物这个注入是**惰性**的：`TFCHorse.isFood` 等覆写了
> `isFood` 且不调用 `super`，所以它们走不到这里（它们本来就有 TFC 的食物逻辑）。

tag 内容（`data/terras_horsies/tags/items/horse_food.json`）：

```json
{ "replace": false, "values": ["#tfc:foods/grains", "#tfc:foods/fruits"] }
```

`data/tfc/tags/items/horse_food.json` 再把 `#terras_horsies:horse_food` 转发过去，
所以整合包可以直接往 `tfc:horse_food` 里加东西，也可以覆盖我们那份。

### 5.2 ⚠️ 喂食是两套互不影响的机制

这是 `BhBreedHorseFamiliarityMixin.mobInteract` 的核心设计。**它们不能合并**，
因为 TFC 的 `eatFood` 只回 1 点血（见 5.3）：

| | 回血吃东西 | 每日正餐 |
| --- | --- | --- |
| 触发 | **已驯服** 且 血量未满 | 血量满 且 `isHungry()`（今天还没喂过） |
| 回血 | 按 5.4 的算法（干草块 20、TFC 谷物按饥饿值换算） | **只回 1 点**（TFC 的 `eatFood` 写死 `heal(1f)`） |
| 亲密度 | **不给** | +0.06（受上限与幼年规则约束） |
| `lastFed` | **不动** → 不消耗当天额度 | 设为今天 → 当天不能再吃 |
| 求偶爱心 | **不冒** | 不冒 |
| 实现 | `tfcicys$eatToHeal`（自己写的） | TFC 的 `eatFood` |

优先级就是上表的顺序：**血没满 → 走回血；血满了才轮到正餐。**

为什么必须把回血排在前面：TFC 的 `eatFood` 只回 1 点血，所以一只受伤的马喂一整组干草块
却几乎不回血 —— 这正是这个功能要修的东西。血满了以后再喂才是加亲密度的那一顿。

第三条分支（血满 + 今天已喂过）返回 `InteractionResult.FAIL`，食物不会被扣掉。
这也和 TFC 自己「不饿就 `return InteractionResult.FAIL`」的处理一致。

### 5.3 为什么不直接调用原版的 `handleEating`

原版 `Horse.mobInteract` 对食物会走
`fedFood → AbstractHorse.handleEating`（SRG `m_30580_` → `m_5994_`），
它才是原版「按食物回血」的地方，回血表是从字节码里逐条核对出来的：

| 物品 | 回血 |
| --- | --- |
| 糖 `f_42501_` | 1 |
| 小麦 `f_42405_` | 2 |
| 苹果 `f_42410_` | 3 |
| 金胡萝卜 `f_42677_` | 4 |
| 金苹果 / 附魔金苹果 `f_42436_` / `f_42437_` | 10 |
| 干草块 `f_42129_` | 20 |

**但 `handleEating` 有两个问题，不能直接用：**

1. **表里只有上面 6 种原版物品。** 我们的 horse_food tag 里全是 TFC 食物
   （`#tfc:foods/grains` + `#tfc:foods/fruits`），一个都不在表里 → 算出 `f = 0` →
   **不回血**。可是 `fedFood` 无论回不回血都会 `shrink(1)`，
   结果就是「TFC 谷物被吃掉但一点血都不回」。
2. **它会调 `setInLove`**（`this.isTamed() && this.getAge() == 0 && this.canFallInLove()` 时）。
   于是「马血满 + 今天已经喂过」时玩家会看到**冒爱心但按 TFC 规则
   （`isReadyToMate()`）根本不怀孕**的假象，而且那一下食物是被真扣掉的。

所以 `tfcicys$eatToHeal` 自己实现，**既不调 `eatFood` 也不调 `handleEating`**。

构建后核对过：`BhBreedHorseFamiliarityMixin` 的字节码里
`setInLove` / `m_27595_` / `setLastFed` 出现 **0 次**，`eatFood` 只出现 1 次（正餐那一支）。

### 5.4 回血量怎么算

```
heal = max(minHeal, max(原版表固定值, TFC 饥饿值 × healPerHunger))
```

两个来源取**较大值**：

- **原版表的固定值**：干草块 20 点是有意义的强度，不能被 TFC 的饥饿值换算拉低。
- **TFC 的饥饿值 × 系数**：只用于「只在 tag 里、原版表里没有」的 TFC 食物。
  饥饿值就是食物悬停里显示的那个数（TFC 谷物一般是 2~4）。

取大值意味着**接了 TFC 只会更强、不会更弱**：万一某物品两边都命中
（比如苹果可能同时在 `#tfc:foods/fruits` 里），也不会比原版回得少。

最后和 `minHeal`（默认 1）取大值，兜住饥饿值查不到或为 0 的食物 ——
否则又会出现「物品被吃掉但一点血都没回」。

反馈照抄 TFC 的 `eatFood`：5 颗物品粒子 + `eatingSound`，
所以两套机制的视听表现一致，玩家分不出来（本来也不需要分）。
碗类食物会把碗还回去、有 `craftingRemainingItem` 的也会还给玩家，与 TFC 的 `eatFood` 一致。

### 5.5 配置

`config/terras_horsies-common.toml` 的 `[feeding]`：

```toml
[feeding]
	# 已驯服的 Icy 马匹血量未满时，喂它东西可以回血。
	healByEating = true
	# TFC 食物的回血量 = 它的饥饿值 × 本系数。默认 2.0。
	healPerHunger = 2.0
	# 任何 horse_food tag 里的食物至少回这么多血。默认 1。
	minHeal = 1.0
```

「已驯服」的判定复用 `TFCICYSConfig.isWildHorse`（见 17.4 的判定表）：
Icy 归属，或 TFC 亲密度 ≥ 0.15。**野马维持 TFC 原本的行为**
（正餐 +0.06 亲密度 + 回 1 血），只有驯服后的马才有回血吃东西这一套。

---

## 6. 怀孕期

**问题**：TFC 在 `TFCAnimalProperties.getBreedOffspring` 里调用 `onFertilized`
来启动怀孕。但 `BhBreedHorse` **覆写了 `getBreedOffspring`**（做自己的毛色与属性继承），
走不到那个 default；而 Icy 用的是原版 `AnimalMakeLove` 而非 TFC 的 brain 驱动
`BreedBehavior`，也没有任何环节会触发它。

结果就是：**小马在配种完成的瞬间直接出现**，母马从不进入怀孕状态，
Jade 的怀孕行也就永远不可能显示。

**修法**：让 `getBreedOffspring` **返回 `null`**——这正是 TFC 自己的做法。

```java
@Inject(
    method = "getBreedOffspring(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/AgeableMob;)Lnet/minecraft/world/entity/AgeableMob;",
    at = @At("HEAD"), cancellable = true
)
```

对 **Icy 的母马**：

1. 调用 `mammal.onFertilized(male)` —— 标记受精、记录 `pregnantTime`、写入基因标签
2. `cir.setReturnValue(null)` —— 让原版 `if (ageablemob != null)` 整块跳过，取消立即产仔
3. 之后由 TFC 的 `MammalProperties.tickAnimalData` 在
   `pregnantTime + gestationDays` 到期时调用 `birthChildren()` 接生

> **为什么不是取消 `spawnChildFromBreeding`。** 那样会连带跳过
> `finalizeSpawnChildFromBreeding`，把 Icy 设置子嗣性别与发放成就的逻辑一起掐掉。
> 详见 §9.3.3。

`partner != this` 是区分两条路径的关键：原版配对传的是配偶，而
`MammalProperties.birthChildren()` 传的是 `ageable`（母马自己）。
只有后者才需要继续走 Icy 真正的实现。

**数值**（`ServerConfig.java:667` 的 `MammalConfig.build(builder, "horse", 0.35, 80, 60, false, 19, 1)`）：

| 参数 | 值 | 含义 |
|---|---|---|
| `familiarityCap` | 0.35 | 成年亲密度上限 |
| `adulthoodDays` | 80 | 小马长成所需 TFC 天数 |
| `uses` | 60 | 衰老阈值，见 §11 |
| `gestationDays` | **19** | 怀孕天数 |
| `childCount` | 1 | 每胎数量 |

改 TFC 配置会同步生效（本模组一律通过 `getMammalConfig()` 等接口取值，不硬编码）。

---

## 7. Jade 兼容

TFC 的实体 tooltip 是**按具体类注册**的：

```java
// EntityTooltips.register
registry.register("animal",        ANIMAL, TFCAnimal.class);
registry.register("horse",         ANIMAL, TFCHorse.class);
registry.register("chested_horse", ANIMAL, TFCChestedHorse.class);
```

Icy 的马不匹配其中任何一个类，所以 Jade 根本不会调用 `ANIMAL`。
但 `ANIMAL` 的方法体**全部是 `instanceof` 判断**，因此缺的只是"注册"这一步。

`IcyHorseJadePlugin` 把 **TFC 自己的 `EntityTooltips.ANIMAL`** 注册到
Icy 的马匹基类上：

```java
registry.registerEntityComponent(new IEntityComponentProvider() {
    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor access, IPluginConfig config) {
        EntityTooltips.ANIMAL.display(access.getLevel(), access.getEntity(), tooltip::add);
    }
    @Override
    public ResourceLocation getUid() {
        return new ResourceLocation(TfcIcysHorses.MOD_ID, "icy_horse");
    }
}, BhBreedHorse.class);
```

**没有重写任何一行显示逻辑**，直接引用 TFC 的常量，因此翻译、配色、格式
与 TFC 完全一致，也不会随 TFC 更新而过时。

配合第 2 节的 `HorseProperties` 实现，Icy 马得到的显示项与 TFC 牲畜相同：

| 显示项 | 由哪个 `instanceof` 解锁 |
|---|---|
| 性别（含受精标记） | `TFCAnimalProperties` |
| 亲密度（含灰/白/红分级） | `TFCAnimalProperties` |
| 体型 / 可否繁殖 / 衰老程度 | `TFCAnimalProperties` |
| 怀孕状态 + 待产时间 | `MammalProperties` |
| 可否骑乘 | `HorseProperties` |

`TFCHorse` / `TFCChestedHorse` 两个分支对 Icy 马正确跳过（毛色与驮箱物品是
TFC 实体特有的）。

Jade 是**可选**依赖：`mods.toml` 里声明为 `mandatory=false, side=CLIENT`，
`@WailaPlugin` 由 Jade 自己发现，没装 Jade 时本模组照常工作。

### 7.1 出错时不让 Jade 报「本模组」的红字

Jade 会抓住 provider 抛出的任何 `Throwable`，再用**注册该 provider 的模组名**拼出那行红字：

```
assets/jade/lang/zh_cn.json    jade.error = <发生错误，请反馈至%s>
```

干活的是 `snownee.jade.util.WailaExceptionHandler.handleErr`，堆栈写到两处：

| 位置 | 内容 |
|---|---|
| `logs/JadeErrorOutput.txt` | 带时间戳的完整堆栈（UTF-8、追加模式）。**反馈问题时优先给这个** |
| `logs/latest.log` | `Caught unhandled exception : [<provider>] <消息>` + `See JadeErrorOutput.txt for more information` + 堆栈 |

`[<provider>]` 里的 `$1` 是 Icy 马的 provider、`$2` 是 TFC 马科的负重 provider —— 一眼看出是谁挂了。

**归属陷阱**：`EntityTooltips.ANIMAL` 是 **TFC 的**代码，但它跑在**我们的** provider 里。
它抛异常时红字照样算在本模组头上，看着像我们写错了。
（TFC 自己在 `TFCHorse` 上的注册没这个问题，因为那时 provider 就是 TFC 的。）

所以 `appendAnimalLines` / `appendLoadLine` 各自包了一层 `try/catch`：

- 失败时用自己的 logger 打一条 **ERROR**，带诊断串（见下）；
- **只报告第一次**：provider 是每帧悬停都在跑的，持续异常会每帧刷一条、瞬间淹没日志；
- 降级：TFC 那部分失败显示 `TFC 信息读取失败（详见日志）`，负重行失败显示
  `负重: 读取失败（详见日志）`，**都不再往游戏里抛红字**。

诊断串的构成：

```
实体类名 / 类型=命名空间:路径 / 品种= / 类别= / 乘客数= / 被骑= / 已驯服=
```

带**实体类名**是有意的：这类错误往往只在**某个模组的某个实体**的某个特殊状态下出现，
只写一句「显示失败」根本定位不了 —— 类名会直接指出是哪个模组。诊断串自身也包了
`try/catch`，免得它在 catch 块里再抛一次、把真正的异常盖掉。

### 7.2 一个真实排查案例：`NoClassDefFoundError: IcyHorseJadePlugin$3`

**症状**：悬停马匹时负重行变成红字 `<发生错误，请反馈至TFCxIcy'sBetterHorses>`，
`logs/JadeErrorOutput.txt` 里是：

```
java.lang.NoClassDefFoundError: com/tfcicys/horses/compat/IcyHorseJadePlugin$3
    at ...IcyHorseJadePlugin.categoryName(IcyHorseJadePlugin.java:146)
    at ...IcyHorseJadePlugin.appendLoadLine(IcyHorseJadePlugin.java:126)
Caused by: java.lang.ClassNotFoundException: ...IcyHorseJadePlugin$3
```

**`$3` 是什么**：`categoryName` 里那个 `switch (category)` 是**枚举 switch 表达式**，
javac 会为它生成一个合成类 `IcyHorseJadePlugin$3`，
里面只有一个 `static final int[] $SwitchMap$...$HorseCategory` 查表数组
（已 javap 核对：6 个枚举常量各映射到 1..6）。
它不是给 `@Unique` 或 mixin 用的，纯粹是编译器产物。

**为什么读不到**：这个类**不在启动时加载**，而是**第一次调用 `categoryName` 时懒加载**。
而 `mods\terras-horsies-1.0.0.jar` 在**游戏运行期间**被覆盖过 ——
Forge 的 `ModuleClassLoader` / `SecureJar` 在启动时缓存了 jar 的中央目录，
就地覆盖后条目偏移全部失配，于是懒加载那一次读到了错误位置。
**已经加载过的类（`$1`、`$2`、本体）不受影响**，所以表现是「注册正常、某一刻突然开始报错」。

**结论**：这不是代码缺陷，`$3` 确实在 jar 里（945 B）。**完全重启游戏即可恢复**；
本模组这边则该报错已被 7.1 的 `try/catch` 兜住，不会再出红字。

**教训**：**不要在游戏运行时覆盖 `mods/` 里的 jar** —— 见「构建」一节的原子替换做法。

---

## 8. 关闭 Icy 的两处界面显示

按需求关掉了 Icy 的两个显示：马背包界面右侧的**药水效果栏**，以及骑乘时**重复的饥饿值条**。

### 8.1 骑乘时的饥饿值条

**问题**：骑在马上时屏幕下方会出现**第二条饥饿值**，与 TFC 自己的饥饿条重复。

**来源**（`BhHorseHud.java:34`）：

```java
public static void hungerOnHorseback(GuiGraphics gfx, int width, int height) {
    ...
    gui.renderFood(width, height, gfx);   // 又画一次食物条
}
```

**修法**（`mixin/BhHorseHudMixin.java`）：HEAD 处直接取消，于是这一条永远不画。

```java
@Inject(method = "hungerOnHorseback(Lnet/minecraft/client/gui/GuiGraphics;II)V",
        at = @At("HEAD"), cancellable = true, remap = false)
```

只动这一个方法——Icy 的**经验条**（`experience`）和**马匹属性面板**（`render`）保持原样。

### 8.2 马背包界面的药水效果栏

**问题**：打开马背包时，界面右侧会列出玩家当前的所有药水效果。

**来源**：Icy 自己的 `HorseInventoryScreenMixin` 往 `render` 的 TAIL 追加了调用：

```java
@Inject(method = "render", at = @At("TAIL"))
private void bh_drawEffects(GuiGraphics gfx, int i, int j, float f, CallbackInfo ci) {
    BhInventoryEffects.render(this, gfx, this.font, i, j);
}
```

那是 **Icy 的 mixin 类，无法从外部撤销**。但它只是委托，所以直接取消**被委托的方法**即可（`mixin/BhInventoryEffectsMixin.java`）。
马背包本身、装备面板、属性行、羁绊星都照常显示。

### 8.3 两个必须注意的点

**① 必须加 `remap = false`。**

`hungerOnHorseback` 和 `BhInventoryEffects.render` 是 **Icy 新增的方法**，不是 vanilla 方法的覆写，
因此 SRG 映射表里**没有它们**，注解处理器会直接报：

```
Unable to locate obfuscation mapping for @Inject target hungerOnHorseback
```

而模组自己写的方法名**在运行时不被混淆**，所以字面名在任何环境都正确。已对生产 jar 反编译实证：

```
public static void hungerOnHorseback(net.minecraft.client.gui.GuiGraphics, int, int);
public static void render(AbstractContainerScreen<?>, GuiGraphics, Font, int, int);
```

对比之下，`BhBreedHorseFamiliarityMixin` 里的 `defineSynchedData` 等**是 vanilla 方法的覆写**，
有 SRG 映射，所以**不能**加 `remap = false`。

**② 这两个 mixin 必须写在 `client` 数组里。**

它们的目标类引用了客户端专用类型（`GuiGraphics`、`ForgeGui`），
放进 `mixins` 数组会让专用服务端在加载时崩溃。

**③ handler 的 `static` 修饰符必须与目标方法一致。**

`hungerOnHorseback` 和 `BhInventoryEffects.render` 都是 `public static`，所以 handler 也必须是
`private static`。写成实例方法会在**运行时的 APPLY 阶段**崩溃：

```
InvalidInjectionException: 'static' modifier of handler method does not match target
in icy/betterhorses/net/client/BhHorseHud::tfcicys$disableHungerOverlay
```

**这一条特别值得记住**：它**不会**在编译期、也不会在 mixin 的 prepare 阶段被发现，
只有在目标类真正被加载、注入被应用时才报错。而另外四个 mixin 的目标方法
（`isFood`、`spawnChildFromBreeding`、`defineSynchedData` 等）**都是实例方法**，
它们的 handler 必须保持实例——**只能逐一对齐，不能统一写**。

---

## 9. 繁殖：与 TFC 相同，不需要金苹果

### 9.1 崩溃修复

**现象**：给两匹 Icy 马喂金苹果、它们靠近时服务端崩溃。

**原因**（与金苹果无关）：

```
IncompatibleClassChangeError: Method 'boolean BhBreedHorse.checkExtraBreedConditions(TFCAnimalProperties)'
    must be Methodref constant
	at BhBreedHorse.checkExtraBreedConditions(BhBreedHorse.java:766)
	at EntityHelpers.findFemaleMate(EntityHelpers.java:180)
	at HorseProperties.tickAnimalData(HorseProperties.java:127)
	at BhBreedHorse.tick(BhBreedHorse.java:755)
```

**注意崩溃发生在 `tick` 里、不在喂食时** —— 走的是 `HorseProperties.tickAnimalData`
的自动配对路径。原写法是照抄 TFC 自己的 `TFCHorse`：

```java
return HorseProperties.super.checkExtraBreedConditions(otherAnimal) && otherAnimal instanceof BhBreedHorse;
```

**TFC 这么写并正常工作**，但它编译成：

```
invokespecial InterfaceMethod HorseProperties.checkExtraBreedConditions:(...)Z
```

这在**普通类**里合法（编译期就声明了 `implements HorseProperties`），
**但本方法是 Mixin 合并进 `BhBreedHorse` 的**，运行时就链接失败。
TFC 能工作、我们不能，差别就在这里。

**修法**：内联 TFC 的 default 实现。这不是近似——TFC 的默认实现
（`TFCAnimalProperties.java:487-490`）原文就是：

```java
/**
 * Used to check if breeding is possible without actually needing to be in love
 */
default boolean checkExtraBreedConditions(TFCAnimalProperties other)
{
    return true;
}
```

且 `MammalProperties` / `HorseProperties` **都没有覆写它**（已对运行时实际使用的
3.2.20 jar 逐一核对）。所以原表达式等价于 `true && instanceof`，就是那个 `instanceof` 判断。

现在字节码是干净的：

```
0: aload_1
1: instanceof    BhBreedHorse
4: ireturn
```

**并已审计全部 mixin class：不再有任何指向接口方法的 `invokespecial`。**

### 9.2 为什么不需要金苹果

**TFC 自己就不用金苹果。** 它靠 `HorseProperties.tickAnimalData` 里的
"legacy breeding behavior"（`:120-128`）：

```java
if (!getEntity().level().isClientSide() && getGender() == Gender.MALE && isReadyToMate())
{
    EntityHelpers.findFemaleMate((Animal & TFCAnimalProperties) this);
}
```

而 `findFemaleMate` 的配对动作**本身就是 `setInLove`**（`EntityHelpers.java:182-183`）：

```java
femaleAnimal.setInLove(null);
maleAnimal.setInLove(null);
```

**本模组的 Icy 马走的就是这条 default 链**（mixin 刻意没有覆写 `tickAnimalData()`），
所以**喂普通 TFC 食物就能繁殖**，金苹果只是碰巧也能用。

**触发条件**（与 TFC 逐条相同）：

| 条件 | 来源 |
|---|---|
| 成年 | `getAgeType() == ADULT` |
| 亲密度 ≥ **0.3** | `READY_TO_MATE_FAMILIARITY` |
| 未怀孕 | `!isFertilized()` |
| **刚喂过**（不饿） | `!isHungry()` |
| 交配冷却已过 | `getMated() + TICKS_IN_DAY <= calendar` |

亲密度每次喂食 **+0.06**、上限 **0.35**，所以大约**喂 5 次**即可到达 0.3
（TFC 限制每个 TFC 日只能喂一次，因此是 5 个 TFC 日）。

**公马在 tick 时扫描 8 格内的母马**，双方条件都满足就自动进入求爱并配对——
全程不需要金苹果。这也是"不同性别食用食物后即可交配"的实现方式：
`findFemaleMate` 只筛选 `Gender.FEMALE`，性别判断天然生效。

> 金苹果仍然可用，只是不再必需：它被 `isFood` 接受，而 `eatFood` 对非 TFC 食物也是
> 安全的——`stack.getCapability(FoodCapability.CAPABILITY).ifPresent(...)` 会跳过
> 没有 TFC 食物能力的物品，不会抛异常。

---

## 9.3 与 Icy 自带遗传机制的兼容

Icy 有自己一整套繁殖遗传，与 TFC 的亲密度/怀孕是**两套独立系统**。改造时必须确保
不互相踩踏，以下三点是实际踩到过的坑。

### 9.3.1 Icy 遗传了什么（与 TFC 无交集）

`BhBreedHorse.getBreedOffspring`（Icy `:170-193`）里做四件事：

| Icy | TFC `setBabyTraits` |
|---|---|
| `bh_setBreedKey` 品种 | `setGender` 性别 |
| `bh_setMixedBreed` 混血标志 | `setBirthDay` 生日 |
| `bhInheritCoat` 毛色 | `setFamiliarity` 亲密度 |
| `bhInheritStats` 血/速/跳 | `setGeneticSize` 体型 |

**零交集**，且我们的注入点在 `RETURN`（Icy 之后），所以互不干扰。

### 9.3.2 不能调用 `setBabyTraits`，否则覆盖 Icy 的属性遗传

TFC 的 `setBabyTraits` 会链进 `HorseProperties.applyGenes`，其中
`:109-111` 会写入 `MAX_HEALTH` / `MOVEMENT_SPEED` / `JUMP_STRENGTH`——
而 Icy 的 `bhInheritStats` 算的正是这三项。因为我们在 `RETURN` 注入，
一旦委托调用就会把 Icy 刚算好的属性**覆盖成 TFC 的公式**。

因此改为**内联 TFC 那三行纯记账**（`TFCAnimalProperties.setBabyTraits:457-459`）：

```java
baby.setGender(Gender.valueOf(getEntity().getRandom().nextBoolean()));
baby.setBirthDay(Calendars.SERVER.getTotalDays());
baby.setFamiliarity(getFamiliarity() < 0.9F ? getFamiliarity() / 2.0F : getFamiliarity() * 0.9F);
```

### 9.3.3 不能用"取消 `spawnChildFromBreeding`"来实现怀孕

早期版本在 `Animal.spawnChildFromBreeding` 的 HEAD 处 `ci.cancel()`，**这是错的**，
会连带跳过 `finalizeSpawnChildFromBreeding`，而 Icy 在那里做两件事：

- `AnimalMixin:91` roll 子嗣的 **Icy 性别**（`bh_setGender`）。跳过之后
  `AbstractHorseMixin:450` 的默认值 `MALE` 生效，**所有子嗣都会是公马**。
- `bh_awardFoal` 触发 Icy 的繁殖成就。

**TFC 自己的做法是让 `getBreedOffspring` 返回 `null`**，使 vanilla 的
`if (ageablemob != null)` 整块跳过，caller 继续运行。其注释原文即
*"Cancel default vanilla behaviour (immediately spawns children of this animal)"*。
本项目现在照此实现，见 `tfcicys$tfcBreeding`。

`MammalProperties.birthChildren()` 是唯一传 `other == this` 的调用方，
因此 `partner != this` 正好区分"配对"与"产仔"两条路径。

---

## 10. 掉落物与 TFC 一致

Icy 的 15 种马各自有一张掉落表（`data/icys-better-horses/loot_tables/entities/`），
但**15 张内容完全相同**（已用 MD5 比对 2.0.6 的 jar 确认），且都只是一个引用：

```json
{"pools":[{"rolls":1,"entries":[
  {"type":"minecraft:loot_table","name":"minecraft:entities/horse"}]}]}
```

也就是说 Icy 马掉的是**原版**马的皮革和骨头，而 TFC 的马、驴、骡掉的是
`tfc:food/horse_meat` + `tfc:medium_raw_hide` + 骨头。

### 10.1 为什么用事件而不是数据包覆盖

重新提供一份 `data/icys-better-horses/loot_tables/entities/<马>.json` 会让
**同一条路径在资源栈上出现两份**，谁生效纯看 mod 加载顺序——任何一次顺序变动
都会静默失效。`LootTableLoadEvent` 则是确定的：每张表加载时触发一次，写进去的表就是生效的表。

### 10.2 为什么不复制 TFC 的 JSON

改为引用 `tfc:entities/horse`，好处是 TFC 以后调整掉落我们自动跟随，且**不复制任何 TFC 数据**。

表里的 `tfc:animal_yield` 数量函数对 Icy 马**开箱即用**：它用
`instanceof TFCAnimalProperties` 判定（`AnimalYieldProvider:32`），而
`BhBreedHorseFamiliarityMixin` 已经让 Icy 马实现了该接口；万一判定失败也会
退回最小值而不是出错。产量随 `getGeneticSize()` 与 `getFamiliarity()` 缩放，
而 TFC 同步数据的体型默认值是 **16**（`TFCAnimalProperties:255`），
正落在 TFC 自然的 4–18 区间内，所以未繁殖过的 Icy 马掉落量也正常。

> **注意**：必须显式 `setParamSet(LootContextParamSets.ENTITY)`。
> `LootTable.Builder` 默认是 `EMPTY`，会让 `tfc:animal_yield` 读不到
> `LootContextParams.THIS_ENTITY`，产量退化。

`horse_cart`（马车）虽在同一命名空间与目录下，但属于 `MobCategory.MISC`，**不在改动范围内**。

---

## 11. 衰老值降低速度

### 11.1 什么是"衰老值"

TFC 用 `getUses()` 记录动物被"使用"的程度。马身上只有两个来源：

- 母马每产一胎 **+10**（`MammalProperties:49`）
- 公马每次配种 **+5**（`TFCAnimalProperties:452`，注释写着 *wear out the male*）

**衰老值 = `getUses() / getUsesToElderly()`**，马匹的阈值是 **60**（`horseConfig` 的 `uses`）。
TFC 本来就把这个百分比显示给玩家（Jade 的 `tfc.jade.animal_wear`，
见 `EntityTooltips:124-128`），但**它原本对速度没有任何影响**。

### 11.2 原版 TFC 的行为 vs 本模组

TFC 的衰老惩罚是**二值**的：`getUses()` 超过阈值后把 `oldDay` 设为 1~5 天后
（`TFCAnimalProperties:152-155`），到期 `getAgeType()` 变成 `Age.OLD`，
于是 `HorseProperties:130-137` 挂上 `OLD_AGE_MODIFIER`
（`-0.5 MULTIPLY_TOTAL`）——**一过线速度直接砍半**，与显示的衰老百分比无关。

本模组改为**线性**：衰老值直接驱动速度惩罚。

| 衰老值 | 速度保留 |
|---|---|
| 0% | 100% |
| 25% | 92.5% |
| 50% | 85% |
| 100% | **70%** |

因为 Minecraft 对 `MULTIPLY_TOTAL` 的合成规则是 `value *= 1 + sum(amounts)`，
所以修饰符数值取 `-0.3 × 衰老值` 即可。

### 11.3 必须移除 TFC 自己的修饰符

如果两者共存，满衰老时速度会变成 `0.7 × 0.5 = 35%`，与预期的 70% 不符。
因此每轮先 `removeModifier(HorseProperties.OLD_AGE_MODIFIER)` 再挂上本模组的。

移除必须**每个周期重做**：只要动物处于 `Age.OLD`，
`HorseProperties.tickAnimalData` 每 20 tick 都会把自己的修饰符加回来。
调用顺序是 `tickAnimalData()` **之后**才执行本逻辑，因此每次都能覆盖掉它。

本模组的修饰符用固定 UUID
（`b7f4c1a2-3d5e-4f6a-8b9c-0d1e2f3a4b5c`）标识，
这样数值变化时能先移除上一轮的、再挂新的，不会随周期累积。

### 11.4 不影响 Icy 的遗传速度

只操作 **transient modifier**。Icy 通过 `setBaseValue` 写入继承来的基础速度
（`bhInheritStats` / `BhBreedHorse:57-78`），完全不受影响——
所以小马的遗传速度不变，只有运行时惩罚随衰老缩放。

Icy 的马确实走 `Attributes.MOVEMENT_SPEED`：基础值由 `BhBreedHorse` 的 `setBase` 设置，
各种能力（`Endurance`、`TopEnd`、`ArchetypePerks`、`CartRig` 等）用
`BhHorseAttributes.apply(..., MOVEMENT_SPEED, ...)` 挂修饰符，
连它的 HUD 与信息屏显示的速度都是从 `getAttributeValue(MOVEMENT_SPEED)` 读的。
所以这个惩罚会真实生效，**并且在 Icy 的速度显示里能直接看到数值下降**。

> 只作用于 Icy 的马。TFC 自家的马仍走它原来的二值机制。

### 11.5 衰老还会削弱后代（遗传速度衰减）

衰老不只让老马本身变慢，还会让它**生出的子嗣更弱**。

**为什么必须专门实现。** Icy 的属性继承走 `BhBreedHorse.mix`：

```java
return (a.getAttributeBaseValue(attr) + b.getAttributeBaseValue(attr) + rolled) / 3.0D;
```

用的是 **`getAttributeBaseValue`（基础值）**，而基础值**刻意不包含修饰符**——
所以 §11.3 那个 transient 减速对遗传完全不可见。一匹被衰老拖到 70% 速度的马，
照样能生出速度满值的后代。必须把衰减写进子嗣的 **baseValue**，
下一代才会通过同一个 `mix` 读到它，衰老才能真正沿血统传下去。

**幅度**：`新基础速度 = 原基础速度 × (1 - 0.15 × 衰老值)`，
即父母满衰老时子嗣为**原本应得速度的 85%**。比 §11.2 的 30% 温和，
因为这一项会**跨代累积**（用户可调参数：`MAX_INHERITED_SPEED_LOSS`）。

**取双方衰老值中较大的那个**。取平均会让"一方完全衰老、另一方全新"
被稀释到几乎没有，而"这匹马老了，它的后代就弱"在只有一方老时也应当成立。

**公马的衰老值怎么传过来。** 产仔发生在配对后 19 天，而
`MammalProperties.birthChildren()` 传给 `getBreedOffspring` 的是母马自己
（`partner == this`），那时**公马早已拿不到了**。因此改在**配对时**把公马的衰老值
写进 TFC 的 genes 标签：

```java
genes.putFloat("tfcicys_sire_wear", tfcicys$wearOf(male));
```

genes 本来就会被 `MammalProperties.saveCommonAnimalData` 持久化
（`MammalProperties:131-139`），所以这**不需要任何额外的存档代码**，
怀孕期间重启世界也不会丢。每次怀孕 `onFertilized` 都会用 `createGenes`
重建一个新的 genes 标签，旧值因此自动清除，不会有残留。

> Icy 的 `ArchType.clampSpeed` 仍然约束最终值，所以血统会朝该品种的速度下限
> 退化，而不是趋近于零。

---

## 12. 与 TFC 的驴交配

TFC **有意设计了马×驴**：`TFCHorse:124` 与 `TFCDonkey:37` 都会写
`tag.putBoolean("isMule", ...)`，`getEntityTypeForBaby` 再据此产出骡子。
但它的配对路径实际上被**三道同类检查**堵死，我们逐一打通。

### 12.1 `canMate`：vanilla 要求严格同类

`Animal.canMate` 里有一句 `other.getClass() == this.getClass()`，
这从构造上就禁止了马配驴。TFC 的对策是**整个覆写掉**（`TFCHorse:198-200`），
本项目照做：

```java
return otherAnimal instanceof TFCAnimalProperties other
    && this.getGender() != other.getGender()
    && this.isReadyToMate() && other.isReadyToMate()
    && checkExtraBreedConditions(other);
```

> ⚠️ 覆写会**遮蔽** Icy 注入到 `Horse.canMate` 的同性检查
> （`EquineBreedingMixin`），所以 `getGender() != other.getGender()`
> 必须内联写进来，不能省略。

### 12.2 `checkExtraBreedConditions`：原先只认 Icy 马

```java
// 修复前 —— 把 TFC 的驴和 TFC 的马全部排除
return otherAnimal instanceof BhBreedHorse;
```

这是本次两个报错的直接原因。正确规则是 TFC 的写法：
**"一匹马，或一头 TFC 的驴"**——其中 `Horse` 是 vanilla 基类，
同时涵盖 `BhBreedHorse` 与 `TFCHorse`。再叠加 TFC 真正的繁殖前提
`TFCChestedHorse.vanillaParentingCheck`（未被骑乘、未骑乘他人、已驯服、非幼年）。

### 12.3 `BreedGoal`：默认按自身 class 找对象

即使 `canMate` 放行，仍然配不成：vanilla `Animal.registerGoals` 用的是
**两参数**构造 `new BreedGoal(this, 1.0)`，而它内部把
`partnerClass = animal.getClass()`，`getFreePartner` 又按
`getEntitiesOfClass(partnerClass, ...)` 搜索——**永远看不到驴**。

该 goal 有一个可指定对象类的三参数构造，因此替换掉它：

```java
EntityHelpers.removeGoalOfClass(this.goalSelector, BreedGoal.class);
this.goalSelector.addGoal(2, new BreedGoal(this, 1.0D, Animal.class));
```

放宽成 `Animal.class` 只是扩大**候选范围**，是否合法仍由 `canMate` 裁决。
按 class 而非优先级移除，可避免依赖 vanilla 选用的优先级。

### 12.4 谁先靠近决定了母方是否怀孕

vanilla `BreedGoal.tick` 在**先凑近的那一方**身上调用
`spawnChildFromBreeding`，于是 `getBreedOffspring` 里的 `this`
可能是公方也可能是母方。TFC 自身只处理 `this` 是母方的情形——
这对 TFC 足够，因为它的 brain `BreedBehavior` 是在**目标（永远是母方）**
上调用 `getBreedOffspring`；而 Icy 马走的是 vanilla `BreedGoal`，
公马先到时母马就永远不受精。

因此改为**方向无关**：判定哪一方是母的，再让那一方 `onFertilized(male)`。

> 公马的衰老值也随之写到**母方**的 genes 里，
> 所以母方是 TFC 的驴时同样能携带（§11.5）。

### 12.5 后代是什么

- **Icy 母马 × TFC 公驴** → 走 Icy 的 `getBreedOffspring`，产出 **Icy 马幼驹**
- **TFC 母驴 × Icy 公马** → 走 TFC 的 `getBreedOffspring`，产出 **小驴**

严格说马×驴应当产骡子。TFC 靠 `isMule = maleProperties instanceof TFCHorse`
判定，而 Icy 公马是 `BhBreedHorse`，因此这个标志为 false。这是有意保留的取舍：
让 Icy 马继续按自己的品种/毛色/属性遗传产仔，不强行改写 TFC 的骡子逻辑。

---

## 13. 马匹被手持食物吸引

TFC 在自己的马身上装了诱惑 goal（`TFCHorse:186`）：

```java
new TemptGoal(this, 1.25f, Ingredient.of(getFoodTag()), false)
```

而 Icy 马继承 vanilla `Horse`，其 goal 集合里**根本没有诱惑行为**，
所以拿着食物对它没有任何作用。本项目补上同一个 goal。

**为什么写成覆写而不是 `@Inject`**：`registerGoals` 声明在 `Mob` 上，
不是 `BhBreedHorse` 自己声明的方法，而 Mixin 只能注入目标类**实际声明**
的方法。写成 `@Override` 是可行的，因为 `Mob` 的构造函数会**虚调用**
`registerGoals()`，这段代码在构造期就会执行，与 TFC 的方式一致。

速度系数 `1.25` 与 `canScare = false` 沿用 TFC。判据是 `getFoodTag()`，
也就是 `isFood` 接受的那个 `tfc:horse_food` 标签——
**能被喂的东西，就是马会主动走向的东西**。

---

## 14. 负重系统：More Attributes × AstikorCarts TFC

这一节把两个模组接了起来：
[More Attributes](https://github.com/HLGOrganization/More-Attributes)（负重）与
AstikorCarts TFC（马车）。两者都是**可选依赖**，缺席时本节功能整体静默关闭。

### 14.1 移除马车的「击倒」效果

TFC 马车在 `pulledTick()` 里按三个阈值施加 TFC 状态效果：

```java
double laden = countOverburdened();
if (laden > COMMON.pinnedThreshold.get()) {
    player.addEffect(new MobEffectInstance(TFCEffects.PINNED.get(), 25, 0, false, false));  // ← 击倒
} else if (laden > COMMON.overburdenedThreshold.get()) {
    player.addEffect(Helpers.getOverburdened(false));                                        // ← 过载
} else if (laden > COMMON.exhaustedThreshold.get()) {
    player.addEffect(Helpers.getExhausted(false));                                           // ← 力竭
}
```

三者是同一套判定，且只对 `TFCFoodData` 的玩家生效。按需求**三个全部移除**，
让 TFC 的过载体系整体退场、把话语权交给 More Attributes。

**做法是让 `countOverburdened()` 返回 0**，而不是取消 `pulledTick()`：

- `pulledTick()` 的第一行就是 `super.pulledTick()`，整段取消会连带破坏
  AstikorCarts 的车轮物理与乘客更新。
- 三个分支都以「返回值 > 阈值」为条件，钉死成 0 就自然全部进不去。
- 这个返回值在类内只被 `pulledTick` 使用，改动不外溢。

补给车与动物车**各自独立实现**了同名方法，所以需要两个 mixin。

### 14.2 马车负重继承

拉车者继承到的重量为：

```
马车重量 = 512（固定自重，不减免） + 货物重量 × 系数
```

- **货物重量**逐格用 More Attributes 的 `ItemUtils.getWeight(stack)` 求和。
  之所以直接调它而不是重写一遍公式，是为了让车厢里的物品与玩家背包里的同一件物品
  **永远算出同一个数**。它的公式是
  `ItemWeights.getOrDefault(id, 64 / Weight.stackSize) × count`，再乘 TFC 的
  `Size` 系数（NORMAL×2 / LARGE×3 / VERY_LARGE×4 / HUGE×8），并递归累加背包内容。
- **系数默认 0.6**，即按需求减轻 40%。
- **固定自重 512** 即使空车也全额继承，且**不参与任何减免**——包括挽马那一档。

### 14.3 马车 × 挽马

挽马拉车时整车系数降到 **0.4**（只承受 40%），**这一档对玩家无效**：
玩家亲自拉车时始终用 0.6。

顺带一提，「哪匹马在拉哪辆车」这件事 AstikorCarts 只在马车一侧存了 `pulling` 字段，
马身上没有反向引用。若每 tick 扫附近实体来反查，开销随实体数线性上升。
本项目改为在 `AbstractDrawnEntity.setPulling()` 里记一笔，
用 `WeakHashMap` 维护「拉车者 → 马车」索引（`load/CartPullRegistry.java`），
O(n) 扫描降成一次查表，且实体卸载后条目自动回收。

### 14.4 负重上限

| 类别 | 默认上限 | 对应品种 |
|---|---|---|
| 生物默认 | **1000** | 所有非马科生物 |
| 竞速马 `RACE` | **900** | 阿拉伯马、夸特马、纯血马 |
| 矮马 `PONY` | **1600** | 哈菲林格马、冰岛马 |
| 西部马 `WESTERN` | **1800** | 美国花马、阿帕卢萨马、摩根马 |
| 战马 `WAR` | **2000** | 安达卢西亚马、弗里斯兰马、野马 |
| 挽马 `DRAFT` | **4800** | 比利时马、克莱兹代尔马、佩尔什马、夏尔马 |
| TFC 马 | **1700** | 按西部马对待 |
| TFC 驴 | **2500** | 按矮马对待 |
| TFC 骡 | **4000** | 按挽马对待 |

类别判定见 `load/HorseCategory.java`。Icy 那一列直接来自它自己的
`data/icys-better-horses/better_horses/breed/*.json` 的 `class` 字段，是权威来源。

**为什么不用 `BreedArchetype` 枚举**：它虽然恰好就是这五类
（`RACE`/`WAR`/`WESTERN`/`DRAFT`/`PONY`/`NONE`），但 `HorseBreed` 上的
`builtInArchetype()` 是**包私有**的，而公开的 `archetype()` 返回的是注册表对象
`ArchetypeType`、不是那个枚举。按品种 ID 映射既稳定又不必依赖反射。

### 14.5 骑乘负重

玩家骑上去之后，**坐骑**承受：

```
每个乘客 350（挽马 250） + 该乘客自身的负重
```

- 挽马可载两人，所以两名无负重玩家正好是 500。
- 「乘客自身负重」直接读 More Attributes 的 `equip_load_current`，
  所以负重 560 的玩家骑竞速马（上限 900）时，马吃到 560 + 350 = 910。
- 910 / 900 = 1.011，代入减速公式得移速 ×0.994 —— 只是压一下，离「明显拖慢」还很远。

### 14.6 衰老影响上限

沿用 §11 的「衰老值」（TFC 的 `uses / usesToElderly`，也就是 Jade 显示的那个百分比），
线性折算上限：衰老 0% 时是原值，**100% 时降为 60%**。

### 14.7 超重减速：与 More Attributes 逐字一致

移速是**连续线性**而非分档常量。设 `r = 当前负重 / 上限`：

| r | 移速 |
|---|---|
| ≤ 1.0 | ×1.00 |
| 1.5 | ×0.75 |
| **2.0** | **×0.50** |
| 2.5 | ×0.25 |
| ≥ 3.0 | ×0.00（钳制下限，不会更差） |

公式为 `mult = 1 - (r - 1) / 2`。跳跃另算：`2.5 < r < 3.0` 时减半，`r ≥ 3.0` 时归零。
这与上游 `ModifierUtils.java:820-842` 的实现完全相同。

### 14.8 一个必须区分对待的地方：玩家 vs 生物

上游对两者的处理**不对称**，这决定了我们只能用两种不同的写入方式：

| | 玩家 | 生物（含马匹） |
|---|---|---|
| 上游是否接管 | **是**，每 tick `setBaseValue(背包总重)` | **完全没有** |
| 追加重量的方式 | **必须**用 `AttributeModifier(ADDITION)` | 可直接 `setBaseValue` |
| 谁施加减速 | 上游的 `rebuildModifier` | 本模组 |

More Attributes 只在 `AttributeUtils.registerPlayerAttribute` 里写了唯一一行
`event.add(EntityType.PLAYER, attr)`，全仓库再无第二个 `EntityType` 引用，
它的 `calculateLoad` / `rebuildModifier` 也都以 `Player` 为参数。
**所以马匹上 `getAttribute(EquipLoadMax)` 返回 `null`**，生物这一半必须由兼容层补齐：
由 `load/LoadAttributeRegistration.java` 在 `EntityAttributeModificationEvent` 里注册。

注册范围取**全体 `LivingEntity`**（跳过玩家，上游已注册）。原因是 AstikorCarts 的
`pull_animals` 默认是空列表，语义为「任何能穿鞍但不由物品操控的生物都能拉车」，
硬编码一份马科白名单反而会在整合包换马匹模组时漏掉。

**玩家侧刻意不调 `applyPenalty`**：上游已经读着 `EquipLoadCurrent` 挂了一份移速修饰符，
再挂一份就是同一份超重被罚两次。我们只把马车重量写进属性，减速自然由它产生。

### 14.9 关于「生物默认上限 1000」

需求里提到「和 More Attributes 的设定一样」，但核对源码后要更正一点：
**上游并没有 1000 这个值**。它历史上 `EquipLoadMax` 的默认值依次是
`1200 → 120 → 1300 → 300`，且从某次提交起被运行时公式覆盖：

```java
maxLoadAttr.setBaseValue(LevelUtils.getLevel(player, "endurance") * 100.0 + 300);
```

即**玩家**上限 = 耐力等级 × 100 + 300（裸玩家 300，内置职业耐力 10 → 1300）。
最可能被误读成 1000 的是私有方法 `getPassengerLoad` 里的 **1024**。

本模组按需求把**生物**上限固定为 1000，与玩家那套耐力公式互不干扰。

### 14.10 可配置项

全部数值都在 `config/terras_horsies-common.toml`：

```toml
[load]
    creatureDefaultLoad = 1000    # 生物默认上限
    agedLoadFactor = 0.6          # 衰老 100% 时的上限比例
    [load.horses]
        raceLoad = 900
        ponyLoad = 1600
        westernLoad = 1800
        warLoad = 2000
        draftLoad = 4800
    [load.tfc_animals]
        tfcHorseLoad = 1700
        tfcDonkeyLoad = 2500
        tfcMuleLoad = 4000
[cart]
    cartBaseLoad = 512            # 固定自重，不减免
    cartCargoFactor = 0.6         # 货物系数（减轻 40%）
    draftCartFactor = 0.4         # 挽马拉车系数
[rider]
    riderLoad = 350               # 骑手自身体重
    draftRiderLoad = 250          # 挽马的骑手自身体重
    inheritRiderBackpackLoad = true   # 是否继承骑手背包负重
```

> 上面列的是**出厂默认值**，键名与配置文件一致。
> **配置文件本身的注释是英文的**，中文说明一律留在本 README，
> 这样整合包用户在游戏目录里打开 TOML 时不会被大段中文注释挤满屏幕。

### 14.11 可选依赖的处理

三个 mixin（两个过载、一个牵引索引）的目标类来自 AstikorCarts。
AstikorCarts 缺席时，这三个 mixin 会因为找不到 `@Mixin` 目标而被跳过——
**Mixin 对此只是打一条 `WARN` 然后继续**，不会影响同配置文件里其它 mixin，
更不会影响 Icy 那部分。

这一点由实际运行日志证实（同一份日志里其它模组有 11 处同类 `WARN`，游戏照常启动）：

```
[WARN] [mixin/]: @Mixin target dev.emi.emi.screen.EmiScreenManager was not found
       jei_copy_recipe_json.mixins.json:EmiScreenManagerMixin
```

**所以不需要 `IMixinConfigPlugin`。** 这里特别记下这个坑：

> 曾经加过一个 `TFCICYSMixinPlugin`，在 `shouldApplyMixin` 里调
> `ModList.get().isLoaded(...)` 来提前摘掉这三个 mixin。结果游戏**启动即崩**——
> `shouldApplyMixin` 是在 **mixin config prepare** 阶段被调用的：
>
> ```
> FMLLoader.beforeStart(:216)
>   → MixinProcessor.prepareConfigs(:539) → MixinConfig.prepareMixins(:850)
>     → MixinInfo.<init>(:881) → readDeclaredTargets(:952) → shouldApplyMixin(:987)
> ```
>
> 这条链发生在 `FMLLoader.beforeStart` 期间，**Forge 的 `ModList` 尚未初始化**，
> `ModList.get()` 返回 `null`，于是抛 `NullPointerException`。
> Mixin 把它包成 `InvalidMixinException` / `MixinApplyError`，
> 以 `FATAL` 级别让**整个混合配置文件作废**并终止启动。
>
> 换句话说：为了防御一个并不存在的风险（配置失效），反而引入了真实的崩溃。
> **在 `IMixinConfigPlugin` 里不要碰 `ModList`。**

真正需要处理的是另一件事：代码里对 More Attributes 的引用集中在
`load/MoreAttributesApi.java`，**方法签名只用 Minecraft 类型**——
JVM 只在真正执行到那条字节码时才解析常量池，因此上游缺席时
这些类不会被加载，不会抛 `NoClassDefFoundError`。

至于 `ModList.get()`，它在运行时（事件回调里）是安全的，
但 `TfcIcysHorses.hasMoreAttributes()` 仍做了两件事：查询结果缓存
（这些判定在 `LivingTickEvent` 里按实体逐个命中），以及**在 `ModList`
尚未就绪时不缓存结果**——否则会把「暂时不知道」错记成「模组缺席」而永不恢复。

---

## 15. TFC 金属马铠的外观

给 TFC 的 9 种金属马铠配上 Icy 风格的贴图。**这一节不需要任何代码**——
Icy 的渲染层会自动发现它们，原因如下。

### 15.1 Icy 查找马铠贴图的规则

`BhTackTextures.lookup(Item)` 是一个三级回退：

```java
ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);        // tfc:metal/horse_armor/copper
ResourceLocation candidate = new ResourceLocation(
        "icys-better-horses",
        "textures/entity/horse/" + breed + "/armor/" + id.getNamespace() + "/" + id.getPath() + ".png");
if (Minecraft.getInstance().getResourceManager().getResource(candidate).isPresent()) {
    return candidate;                       // ① 找得到就用它
}
// ② 原版皮革/铁/金/钻石 → armorLeather / armorIron / armorGold / armorDiamond
// ③ 其余一律 armorGeneric
```

（拼接串取自字节码 `BootstrapMethods` 里的 recipe
`\u0001armor/\u0001/\u0001.png`，`\u0001` 依次代入上面三项。）

代入 TFC 铜马铠就是：

```
textures/entity/horse/belgian/armor/tfc/metal/horse_armor/copper.png
```

**① 分支位于最前面，所以只要贴图存在，TFC 马铠就会走它、不再落到 ② 或 ③。**
这就是本节无需代码的原因——把文件放对位置即可。

### 15.2 文件布局

`assets/icys-better-horses/textures/entity/horse/<品种>/armor/tfc/metal/horse_armor/<金属>.png`

8 个渲染分组 × 9 种金属 = **72 张**：

| 维度 | 取值 |
|---|---|
| 品种分组 | `belgian`、`friesian`、`haflinger`、`icelandic`、`medium`、`percheron`、`shire`、`small` |
| 金属 | `copper`、`bronze`、`bismuth_bronze`、`black_bronze`、`wrought_iron`、`steel`、`black_steel`、`blue_steel`、`red_steel` |

> 这 8 个是**渲染分组**而非 15 个品种。`small` 与 `medium` 是体型归类，
> 多个品种共用同一套贴图。

### 15.3 尺寸必须跟着品种走

**各品种的原版分辨率并不统一**：

| 品种 | 马铠贴图尺寸 |
|---|---|
| `icelandic` | **64×64** |
| 其余 7 个 | 128×128 |

冰岛马是矮种体型，连它的 `saddle.png` 也是 64×64。
所以本节贴图刻意**混用两种尺寸**——这是正确的，不是疏漏。
统一成 128×128 反而会让冰岛马的 UV 错位（Icy 走 GeckoLib 几何体，
UV 是像素坐标，必须与纹理尺寸匹配）。

### 15.4 前提：TFC 马铠属于原版马铠体系

这条链能成立的关键在于 TFC 的马铠就是 vanilla `HorseArmorItem` 的**直接实例**
（`Metal$ItemType.HORSE_ARMOR` 的工厂是
`new HorseArmorItem(int, ResourceLocation, Item.Properties)`）。因此：

- Icy 的 `HorseArmorItemMixin` 对它生效 → **能装进 Icy 马的装备槽**
- Icy 渲染时以 `ItemStack` 取物品再查纹理 → **命中 15.2 的贴图**

若 TFC 用的是自定义马铠类，这两点都不成立，本节就需要额外的槽位兼容代码。

### 15.5 与 TFC 自身纹理的关系

TFC 给 `HorseArmorItem` 传的纹理是
`textures/entity/animal/horse_armor/<金属>.png`。那是给**原版马**用的。
Icy 替换了马的渲染器，取纹理走的是自己的 `BhTackTextures`，
所以两者互不干扰：原版马显示 TFC 纹理，Icy 马显示本节贴图。

### 15.6 锻铁有意复用铁马铠贴图

72 张里有 **8 张**（全部 8 个品种的 `wrought_iron`）逐字节等同于
Icy 的 `armor_iron.png`。这是**有意的**：锻铁即铁，铁马铠的外观正是想要的效果，
另画一张只会产生风格断层。

排查时注意别把它当成占位图——`_tools/verify_armor.py` 已将这 8 张列入
`INTENTIONAL_IRON` 白名单，校验时会明确报告为「intentionally reuse」而非问题。

---

## 16. 气候生成：交给 KubeJS

**实现位置在脚本侧，不在模组里：**

- `kubejs/startup_scripts/Icy_Horses_Fauna_Register.js` —— 注册
- `kubejs/server_scripts/Icy_Horses_Fauna_Range.js` —— 温度 / 降雨 / 森林区间

依赖 KubeJS-TFC 插件 `kubejs_tfc`（实测 1.20.1-1.3.2）。模组本体不参与这件事。

### 16.1 为什么必须有个钩子

TFC 让生物按「温度 / 降雨 / 森林密度」刷新的数据文件是
`data/<ns>/tfc/fauna/<实体路径>.json`，读取它们的
`RegisteredDataManager.apply()` 开头是：

```java
final Entry<T> typeEntry = types.get(name);
if (typeEntry == null) {
    LOGGER.error("Ignoring {} '{}' as it was not registered.", typeName, name);
}
```

`types` 只由 `Fauna.MANAGER.register(ResourceLocation)` 填充，而 TFC 只在
`Faunas` 的静态字段列表（`Faunas.java` 第 42–98 行）里对**它自己的实体**逐个调用过。
Icy 的 15 个品种不在其中。

更关键的是：`Fauna` 只是数据。真正会去调 `getClimate().isValid(...)` 的，
是 TFC 通过 `SpawnPlacementRegisterEvent` 装在实体上的**生成放置谓词**。
两个钩子缺一不可。

### 16.2 为什么改用 KubeJS 而不是原生 data

先前试过在 `kubejs/data/icys-better-horses/tfc/fauna/*.json` 放 15 个文件、
再在模组里补 Java 钩子。这条路技术上能通，但实测区间稍一收紧马就在地图上
彻底找不到，而「数据包文件」和「脚本」的生效时机与优先级都不直观。
KubeJS-TFC 把这两件事直接暴露成事件，所以改走脚本，模组里的
`TfcFaunaCompat` 已删除，那批 fauna JSON 也已从 `kubejs/data` 移除。

### 16.3 注册侧：`TFCEvents.registerFaunas`

```js
event.replace('icys-better-horses:icelandic_horse', 'on_ground', 'motion_blocking_no_leaves');
```

签名是 `replace(实体, 生成放置类型, 高度图类型)`，对 15 个品种各调一次。
**一个调用同时做了两件事**（反编译 `RegisterFaunasEventJS.replace` 确认：
它先 `registerFauna(...)` 拿到 `Supplier<Fauna>`，再拿它构造谓词调 `register(...)`）：

1. `Fauna.MANAGER.register(id)` —— 没有它，16.4 的区间会被 `apply()` 丢掉
2. 装生成放置谓词 —— 真正会去读区间的那段代码

**高度图必须用 `motion_blocking_no_leaves`**，与 TFC 的 `registerAnimal()` 一致：
这片高度图**不计树叶**，所以树林里的刷怪点仍落在地面上。
`Biome_Spawn_Substitution_register.js` 给地面生物统一用的是 `world_surface`，
那是给树上/飞行类生物兜底的写法 —— 马不要跟，`world_surface` 会把点算到树冠顶上，
森林马（belgian / shire / friesian / morgan）会直接刷不出来。

**用 `replace` 是安全的**：反编译 `IcysBetterHorses.registerSpawnPlacements` 确认
Icy 只给**原版 `EntityType.HORSE`** 注册过生成放置（`BhHorseSpawnRules` 那套
亮度 ≤8 + 特定地表方块的规则），没碰过自家 15 个品种。

### 16.4 区间侧：`TFCEvents.data`

```js
event.fauna(climate => {
    climate.minTemp(-22);
    climate.maxTemp(-4);
    climate.minRain(80);
    climate.minForest(2);
}, fauna => { }, 'icys-better-horses:belgian_horse');
```

| lambda | 可用方法 |
| --- | --- |
| `climate` | `minTemp` / `maxTemp` / `minRain` / `maxRain` / `minForest` / `maxForest` / `fuzzy` |
| `fauna` | `chance` / `distanceBelowSeaLevel` / `solidGround` / `maxBrightness` |

**`minForest` / `maxForest` 收的是 TFC 的 `ForestType`，数字和字符串都可以**，
两者完全等价。反编译 Rhino 的 `EnumTypeWrapper.wrap(Context, Object)`
（`rhino-forge-2001.2.3-build.10`）可以看到它分两条分支：

```
入参 instanceof CharSequence → toString().toLowerCase() → 查 nameValues  (枚举常量名)
入参 instanceof Number       → intValue()              → 查 indexValues (getEnumConstants())
```

`indexValues` 就是 `getEnumConstants()`，即**声明顺序 = 序号**。
查表失败会抛 `IllegalArgumentException` 并把合法值列在异常消息里，不会静默失败。

`ForestType` 的声明顺序（javap 核对 3.2.20 的 jar）：

```
0 = NONE        1 = SPARSE      2 = EDGE        3 = NORMAL      4 = OLD_GROWTH
'none'          'sparse'        'edge'          'normal'        'old_growth'
```

（`ForestType` 没有实现 `RemappedEnumConstant`，所以 `nameValues` 的键就是
枚举常量名的小写；`getSerializedName()` 走的是 `StringRepresentable`，
也就是 JSON 里写的那套字符串，与上面一致。）

比较用的是序号，所以 `minForest(2)` = 只在 edge 及更密的林子里；
`maxForest(3)` = 最多到 normal，排除 old_growth 原始林。
本仓库的脚本统一用**数字**，与 `Biome_Spawn_Substitution.js` 的风格一致。
（TFC 自己的 `horse.json` / `donkey.json` 用 `max_forest: "edge"`，
`moose.json` / `deer.json` 用 `min_forest: "edge"` —— 取值与它们同源。）

### 16.5 温度带的设计

TFC 的年均气温范围约 **-20…+30°C**，另有 ±3 噪声；海拔每升高 1 格再减
`0.16225°C`，最高减 17.8。所以实际可用范围约 -25…+35。

#### 先看那个躲不掉的硬约束

「不想一个地区同时有太多品种」和「每个品种容易遇到」是**直接冲突**的，
因为一个地点可选的品种数有个下界：

```
某地可选品种数 ≈ 品种总数 × 带宽 ÷ 覆盖的总温度跨度
```

15 个品种铺在 `-25…+35`（60°C）上：

| 带宽 | 一个地点平均几个品种可选 |
| --- | --- |
| 8°C | 2 |
| **12°C** | **3** ← 本方案 |
| 15°C | 3.75 |
| 18°C | 4.5 |

也就是说**光靠气候分区做不到「一处只有 1~2 个品种、而且还很好找」**。
带宽压窄必然让每个品种更难遇到。

本方案的解法是**把两件事分开交给两个旋钮**：

| 目标 | 交给谁 |
| --- | --- |
| 一个地区不要太多品种 | **气候带宽**（12°C，平均 3 个品种） |
| 遇得上 | **`weight`**（`forge/biome_modifier/icy_horses/*.json`，现在 6） |
| 成群同种一起出现 | **`minCount` / `maxCount`**（现在 2 ~ 4） |

#### 现在的取值

**每带 12°C、中心相隔约 3.4°C**，一个地点平均 3 个品种、冷热两端 1~2 个。
排布按 Icy 自己的群系表来，但**注意原版群系的气候含义**：

```
极寒   snowy_plains / snowy_taiga / ice_spikes / frozen_peaks / jagged_peaks
寒凉   taiga / old_growth_spruce_taiga / old_growth_pine_taiga
温凉   windswept_hills / windswept_forest / stony_peaks / snowy_slopes
温带   forest / birch_forest / dark_forest / meadow / grove / plains
暖带   savanna / savanna_plateau / sparse_jungle / wooded_badlands
酷热   desert / badlands / eroded_badlands
```

`windswept_*` 是 1.18 之后的**温带山地**，`forest` / `birch_forest` /
`dark_forest` 也都是**温带** —— 这两类一旦被当成寒带，挽马就会全部挤在冷端。

| 品种 | 类别 | 温度 | 降雨 | 森林 |
| --- | --- | --- | --- | --- |
| icelandic 冰岛马 | 矮马 | -25 ~ -13 | ≥60 | 不限（保底） |
| belgian 比利时挽马 | **挽马** | -22 ~ -10 | ≥80 | `minForest(2)` ≥ edge |
| haflinger 哈菲林格马 | 矮马 | -18 ~ -6 | 不限 | `maxForest(3)` ≤ normal |
| clydesdale 克莱兹代尔马 | **挽马** | -15 ~ -3 | 不限 | 不限（保底） |
| friesian 弗里斯兰马 | 战马 | -11 ~ 1 | ≥100 | `minForest(2)` ≥ edge |
| shire 夏尔马 | **挽马** | -8 ~ 4 | ≥80 | `minForest(2)` ≥ edge |
| morgan 摩根马 | 西部 | -4 ~ 8 | ≥60 | `minForest(2)` ≥ edge |
| thoroughbred 纯血马 | 竞速 | -1 ~ 11 | 不限 | `maxForest(3)` ≤ normal |
| percheron 佩尔什马 | **挽马** | 2 ~ 14 | 不限 | `maxForest(3)` ≤ normal |
| andalusian 安达卢西亚马 | 战马 | 6 ~ 18 | ≤450 | `maxForest(3)` ≤ normal |
| american_paint 美国花马 | 西部 | 9 ~ 21 | 不限 | `maxForest(3)` ≤ normal |
| mustang 野马 | 战马 | 13 ~ 25 | ≤450 | `maxForest(3)` ≤ normal |
| appaloosa 阿帕卢萨马 | 西部 | 16 ~ 28 | ≤430 | `maxForest(3)` ≤ normal |
| quarter 夸特马 | 竞速 | 20 ~ 32 | 不限 | `maxForest(3)` ≤ normal |
| arabian 阿拉伯马 | 竞速 | 23 ~ 35 | ≤330 | `maxForest(3)` ≤ normal |

#### 挽马（DRAFT）的分布

Icy 的 15 个品种里**只有 4 个是挽马**：belgian / clydesdale / shire / percheron。
按原始群系刚好连成一条从寒到暖温的线：

| 挽马 | 温度 | Icy 原始群系 | 气候含义 |
| --- | --- | --- | --- |
| belgian | -22 ~ -10 | taiga / old_growth_*_taiga | 寒 |
| clydesdale | -15 ~ -3 | windswept_hills / old_growth_pine_taiga | 温凉 |
| shire | -8 ~ 4 | forest / birch_forest / dark_forest | 温带 |
| percheron | 2 ~ 14 | forest / meadow / plains | 温带偏暖 |

**合并覆盖 `-22 ~ +14`，中间无断档**，所以从寒带到暖温带都能找到挽马。

`percheron` 是有意推到暖端的：它是历史上推广到最热地区的挽马（澳洲、阿根廷、
南非大量使用），当暖带的重型马最站得住。**13°C 以上没有挽马可放** ——
Icy 没有暖带/热带的挽马品种。要热带也有，把 `percheron` 改成 `13 ~ 25` 即可。


#### 几个刻意的取舍

- **`maxForest` 用 `3`（normal）而不是 `2`（edge）。** `2` 会同时排除 normal 与
  old_growth，而暖带（20°C 以上）没有森林品种，结果就是「一片暖带林子
  一只马都没有」。想收紧就把 `3` 改成 `2`。
- **icelandic 与 clydesdale 不加森林限制**，保证任何森林密度下都至少有马可刷。
- **降雨刻意放宽，只当倾向而不是门槛。** 湿润倾向 `minRain(60~100)`，
  干旱倾向 `maxRain(330~450)`，定位中性的 6 个品种（clydesdale / percheron /
  haflinger / thoroughbred / american_paint / quarter）完全不写降雨。
  参照 TFC 自己：`horse`/`donkey` 是 130~400，`cow` 只写了 `minRain(250)`。

#### 权重走过的一段弯路（重要教训）

`weight` 管「抽签中签率」，`minCount`/`maxCount` 管「中签后出几只」，
两者是**相乘**的关系，不要一起往低调。

曾经把 `weight` 从 2 调到 1、`minCount/maxCount` 从 2~4 调到 1~2，
同时气候过滤又把候选品种从 15 个砍到 4 个：

| | 品种数 | weight | 平均只数 | 相对量 |
| --- | --- | --- | --- | --- |
| 「太多」的时候 | 15 | 2 | 3 | **90** |
| 压低之后 | 4 | 1 | 1.5 | **6** |
| 现在 | 3 | 6 | 3 | **54** |

第二行就是「在地图上跑半天一只都没有」的状态 —— 约 1/15 的量。
现在回到 54，比「太多」低约 40%，但单个品种是成群的。

调法：

- **一只都遇不到** → 先确认在看**新生成的区块**（见 16.6），然后把
  `weight` 往上加（6 → 8 → 12）。
- **还是太多** → 先把 `maxCount` 降到 3，再降 `weight`。
- **改了要生效**：`server_scripts` 用 `/reload`；
  `kubejs/data/` 下的 JSON 改了要**重启游戏**（数据包在启动时加载）。

### 16.6 排查

**先记住这一条：Minecraft 的生物主要是「区块生成时」刷出来的。**

`NaturalSpawner.spawnMobsForChunkGeneration` 会在区块生成时按概率刷生物，
之后再靠 `NaturalSpawner.tick` 持续补充 —— 但持续补充受 **CREATURE 类别
生物上限**（原版约每玩家 10 只）限制。你在 TFC 世界里加了几十个动物品种
（见 `kubejs/data/kubejs/forge/biome_modifier/normal_creature/`），
这个上限基本一直是满的。

结论：**在老区块（已经探索过、上限已满的地方）找不到马是正常现象。**
要验证马到底刷不刷，必须去**从没生成过的区块**，或者干脆**新建一个世界**。

| 现象 | 含义 |
| --- | --- |
| `Ignoring icys-better-horses:xxx_horse as it was not registered` | `registerFaunas` 没跑到：脚本有语法错、品种 ID 拼错，或 `kubejs_tfc` 没装 |
| `Missing required fauna 'icys-better-horses:xxx_horse'. Using fallback factory.` | 注册了但 `TFCEvents.data` 里没有对应条目 → 回退成无限区间，等于不筛选 |
| 完全没有马 | 先确认 `forge/biome_modifier/icy_horses/*.json` 还在（那管「在哪刷」），本文件管「刷什么样的」，两者是与关系 |
| 日志里既没有 `Ignoring` 也没有 `Missing required` | fauna 数据确实加载成功了。这两条是唯一的失败信号，安静 = 正常 |

`server_scripts/Icy_Horses_Fauna_Range.js` 里加了两行 `console.info`，
在 `kubejs/server.log` 里能直接看到 `TFCEvents.data` 有没有触发：

```
[icy-horses] TFCEvents.data fired, writing 15 fauna entries
[icy-horses] 15 fauna entries written
```

想临时放开某个品种：把它的 `minTemp(-999)` / `maxTemp(999)`，或直接删掉它的
`event.fauna(...)` 整条（会走宽松回退），不必卸模组。

---

## 17. 马匹的战斗行为：中立 + 替主人报仇

目标：**不无故攻击他人**。触发点只有两个 —— **自身被攻击时反击**、**主人被攻击时替主人报仇**；
两者都在**距离**或**时间**任一满足后失去仇恨，而且时间是**续时**的：敌人还在动手就自动延长，
停手才开始倒计时。

> **替主人报仇是 Icy 的原版行为，需求里明确要保留，默认开启。**
> 想要「完全不主动攻击」就把 `neutral_combat.owner_defend.ownerDefend` 关掉（见 17.5）。

### 17.1 Icy 原本会攻击什么

反编译 `icys-better-horses-2.0.6` 的全部 369 个类之后，攻击行为只有两条来源：

| 行为 | 触发 | 是否自主 |
| --- | --- | --- |
| **替主人报仇** | 玩家被某生物打 → `PlayerHurtMixin` 调 `BhHorseCombatAlert.rouse` → 16 格内、你自己的、羁绊 ≥ 1 的马 → `defend(data, attacker)` 写下战斗目标 → `DefendOwnerGoal` 追击并撞击 | **是**（这就是「马会主动攻击敌人」） |
| **冲锋撞击** | 骑手主动发动冲锋能力，马撞到的东西受伤 | 否（玩家驱动） |
| 被打时踢一脚 | `HorseCombat.onHurt`：**从背后**被打时踢一记，方向要求点积 ≤ -0.3 | 否（但**只踢不追**） |

关键事实：Icy 自己**只有** `BhHorseCombatAlert.defend` 一个地方写 `bh_setCombatTarget`
（另有 `IcysBetterHorses` 在下马／换主时把它清空）。所以 Icy 的「马会主动打人」全部来自替主人报仇；
本模组补上的「被打就反击」是第二个写入方（`HorseCombatRetaliateMixin`）。

`HorseCombat.tick` 里的索敌（`targets()`）不是自主行为：它的前置条件是
`getControllingPassenger() instanceof Player` 且 `charging(player)`——
只有骑手按下冲锋时才会去找撞击目标。

### 17.2 五个 mixin + 一个 Goal

| 文件 | 目标 | 做什么 |
| --- | --- | --- |
| `BhHorseCombatAlertDefendMixin` | `BhHorseCombatAlert.defend` | **默认放行**（保留替主人报仇），并顺手记下「主人刚被谁打了」供续时用；只有 `ownerDefend = false` 时才 `HEAD` 取消 |
| `HorseCombatRetaliateMixin` | `HorseCombat.onHurt` | `HEAD` 写入 `bh_setCombatTarget(攻击者)` → 反击，并按亲密度决定要不要反击 |
| `DefendOwnerGoalAggroMixin` | `DefendOwnerGoal` | 复用 Icy 的追击能力，补上脱战条件 |
| `AbstractHorseThreatMixin` | `AbstractHorse.hurt` | `HEAD` 记下「谁打了我」，供低血量逃跑判定。**不读 Icy 的战斗目标**，原因见 17.4 |
| `AbstractHorseDeathFrightMixin` | `LivingEntity.die` | 附近有马匹死亡时，吓到周围的 Icy 马 |
| `ai/FleeFromThreatGoal` | （新 Goal） | 血量不足 / 受惊 / 挨仙人掌时逃跑，尺度（半径、速度、何时算完）由触发源给出 |

**为什么反击要挂在 `onHurt` 的 HEAD 而不是 TAIL**：`onHurt` 有多条提前 `return`
（没开 `horse_kick`、不是 Icy 品种、攻击者不是活体、是主人、方向不对……）。
`@At("TAIL")` 只命中最后一条，会漏掉「从正面被打」。中立反击不该挑方向。

**为什么不直接把 Icy 配置里的 `horse_defend` 关掉**：那个开关会让
`DefendOwnerGoal.m_8036_()` 直接返回 false，连反击要复用的追击逻辑一起废掉。
所以只掐「设目标」这一步，追击能力保留给反击用。

**骑乘时不索敌**：`horse.isVehicle()` 时直接返回。`DefendOwnerGoal` 自己也会拒绝上马的目标，
但目标写下去没人清，会一直留到玩家下马，所以干脆不写。

### 17.3 反击时长由 TFC 亲密度决定

#### 亲密度是哪个刻度

TFC 内部把亲密度存成 `float 0~1`，**Jade 上显示的是乘 100 之后的数**
（`EntityTooltips.java:99`：`String.format("%.2f", familiarity * 100)`）。
所以**你在 Jade 上看到的 `18` 就是 `0.18`**。马匹的亲密度上限是 `0.35`（即 Jade 上的 35），
每次喂食 +0.06。本模组的配置项也用 Jade 那个刻度，免得对不上。

#### 曲线

被**玩家**攻击时，反击时长按亲密度做**分段直线**插值，经过三点：

| 亲密度（Jade 刻度） | 反击时长 | 配置项 |
| --- | --- | --- |
| 0 | 30 秒 | `aggroSecondsAtZero` |
| 10 | 10 秒 | `aggroSecondsAtMid` |
| ≥ 18 | **不反击** | `pacifyFamiliarity` |

```
 30s ┤●
     │  ╲
     │   ╲
 10s ┤    ●───────╲
     │             ╲
  0s ┤              ●────────  ≥18 一律 0
     └──┬────┬────┬────────
        0   10   18
```

两段斜率不同（0~10 段每点掉 2 秒，10~18 段每点掉 1.25 秒）——这是按需求给的三点定的，
不是一条直线。中间锚点 `midFamiliarity` 也可配置。

被**其他生物**攻击时，亲密度**不参与**：亲密度衡量的是马和玩家的关系，对一只僵尸没有意义，
所以用固定值 `mobAggroSeconds`（默认 30 秒）。实现上就是 `aggroTicks()` 里
`target instanceof Player` 的分支。

「不反击」的实现是**压根不写战斗目标**（`aggroTicks(...) <= 0` 就直接 return），
比「写下去再靠 Goal 立刻放弃」干净。

#### 主人也会被反击

**这一点是刻意的，而且必须有。** 亲密度只有喂自己的马才能涨，如果主人免疫反击，
上面整条曲线就永远走不到了。所以上一版里那条
`bh_isOwned() && bh_mayHandle(...) → return` 现在由配置项 `neverRetaliateAgainstOwner`
控制，**默认 false**（即主人会被反击）。想要「驯服的动物不咬主人」那种行为就把它打开，
代价是只有别人打你的马才会被反击。

### 17.4 脱战与逃跑

脱战条件（`DefendOwnerGoal` 本来就有 32 格 + 血量 30% 两条，本模组补上时间）：

| 条件 | 默认 | 配置项 |
| --- | --- | --- |
| 距**最后一次被挑衅**超时 | 自己挨打 → 按 17.3 的曲线；替主人报仇 → **5 秒** | 见 17.3 / `ownerDefendSeconds` |
| 目标离马过远 | 16 格 | `retaliateDistance` |
| 马的血量 < 30% | 固定 | （Icy 原本的，保留 —— 这才是「别追到死」的保险） |

脱战做法与 Icy 自己一致：把 `bh_setCombatTarget` 清成 `null`。目标一清，
`canUse()` 开头就要求目标非 `null`，所以**只有再次被挑衅**才会重新进入战斗。

#### 时间是「续时」的

计时起点不是「开打那一刻」，而是**目标最后一次挑衅这匹马**的时刻。两种动作都会把起点往后推：

- 敌人还在打**主人** —— Icy 的 `rouse → defend` 每次都会记一笔；
- 敌人还在打**马自己** —— `AbstractHorse.hurt` 记的威胁记忆。

只要还在被挑衅就续时，一旦停手满一个时限才真正脱战。这样马**不会因为一次挨打就追杀到天涯**
（避免一直缠斗到死），也**不会在敌人还在动手时中途收手**。

两个来源取更新的那个，由 `FrightenedHorse.tfcicys$provokedAt(attacker)` 给出 ——
它只返回**一个时刻**，脱战判定就是一句「现在 − 那个时刻 ＞ 本场时限」，不需要额外的重置逻辑。

#### 替主人报仇：5 秒

`neutral_combat.owner_defend.ownerDefendSeconds`，默认 **5 秒**，**与亲密度无关**。

> **为什么报仇不看亲密度**：亲密度曲线衡量的是「马和玩家的关系」。马是在替主人出头，
> 如果也套曲线，一匹驯熟的马（亲密度 ≥ 18）在主人被攻击时会当场「想通」而不去报仇 ——
> 正好把要保留的功能废掉。曲线只用于「马自己被打」那条路（见 17.3）。

#### ⚠️ 下面两条逃跑规则只给野马

**已驯服的马完全不逃。**「已驯服」由 `TFCICYSConfig.isWildHorse` 判断，两个条件取**或**
（满足任意一条就算已驯服）：

| 条件 | 判定 |
| --- | --- |
| Icy 的归属 | `IHorseData.of(horse).bh_isOwned()` 为 true |
| TFC 的驯服线 | `getFamiliarity() >= HorseProperties.TAMED_FAMILIARITY`（**0.15**，即 Jade 上的 15） |

取「或」而不是「与」：只要有任何一条说明它不再是无主的野马，就不该再表现得像野生动物。
所以一匹只喂过几次、还没被 Icy 占有的马也会停止逃跑。

已驯服的低血马在挨打时的行为是 **Icy 原本的**：`DefendOwnerGoal.m_8045_` 里
`getHealth() < getMaxHealth() * 0.3f` 本就是它的 bail 条件之一，**且结尾会调
`bh_setCombatTarget(null)` 清掉目标**（已从字节码核对），所以不会出现「目标残留 →
Goal 每 tick 起停 → 导航被反复清空 → 马僵在原地」这种副作用，也不需要本模组额外清理。

#### 血量不足就逃（仅野马，包括被玩家打）

血量低于 **30%**（`fleeHealthRatio`，可配）时，马不再追击，改为**以 2.0 的速度逃离威胁源**，
无论目标是玩家还是生物。

**2.0 这个数是「最快」的依据**：Icy 自己的 `DefendOwnerGoal` 追击用 1.35、冲锋用 1.7，
原版 `PanicGoal` 用 1.2 —— 2.0 是整个模组里给马匹用过的最大值。

**威胁源 = 最后一次打它的生物／玩家**，由 `AbstractHorseThreatMixin` 在 `AbstractHorse.hurt`
的 HEAD 独立记录（`threatMemorySeconds`，默认 10 秒，继续挨打会刷新计时）。
这份记忆**不读 Icy 的 `bh_getCombatTarget`**，原因见下面这个坑。

> **踩过的坑：两个 30% 撞在一起会互相抵消。**
>
> 低血量逃跑最初读的就是 `bh_getCombatTarget`，结果一直不触发。字节码显示，
> Icy 的 `DefendOwnerGoal.canContinueToUse`（`m_8045_`）里：
>
> ```
> 77:  horse.getHealth()                       // m_21223_
> 84:  horse.getMaxHealth()                    // m_21233_
> 87:  ldc 0.3f
> 89:  fmul
> 91:  iflt 161                                // 血量 < 30% → 跳到「放弃」
> ...
> 163: IHorseData.bh_setCombatTarget(null)     // 顺手把战斗目标清了
> ```
>
> 「血量 < 30%」正好就是逃跑的触发阈值。**马刚掉到该逃的血量，触发源就在同一 tick 被抹掉**，
> 表现为「打到残血却站着不动」。
>
> 而且战斗目标本来就**可能压根不存在**：亲密度 ≥ 18 的马被玩家打时不反击（这是需求里要的），
> PvP 关掉时也不会把玩家写成目标。可「血少了要逃」不该取决于「它刚才有没有还手」。
>
> 所以改成在 `hurt` 的 HEAD 自己记一份 —— 那条路径不经过 Icy 的战斗系统，
> 不受 PvP 设置、亲密度反击曲线、是否骑乘影响。`bh_getCombatTarget` 退为**兜底**：
> Icy 自己给「替主人出头」写目标时，马被打到残血也一样该跑。

逃到 **32 格**（`escapeDistance`）算「已脱离仇恨」：清掉目标与攻击者记忆、停止逃跑。
这是唯一的收尾口 —— 不清记忆的话，「血少 + 还记着攻击者」会让 Goal 每 tick 重启一次，
马会一边原地抖动一边反复重算路径。
（已驯服的马不逃，所以 Icy 自带的「低血量清目标」对它们仍是正确的收尾，见上面 17.3 末段。）

方向选择用 **`LandRandomPos.getPosAway`**（`m_148521_`）—— 和原版 `AvoidEntityGoal` 同一个工具。
它在「离威胁点更远」的候选里挑一个**实际站得住、走得到**的位置，避免往墙里或悬崖外算坐标；
返回 `null` 时退化成直线反方向（距离 = 本次的逃跑半径，这一档是 32 格）。
每 8 tick 重算一次，所以威胁移动时马会跟着调整。
搜索半径按本次半径取（夹在 3~16 之间），让「往哪儿跑」和「跑到哪儿算完」保持自洽。

#### 附近有马匹死亡（仅野马）

`AbstractHorseDeathFrightMixin` 挂在 **`LivingEntity.die`** 上（`die` 声明在 `LivingEntity`，
`AbstractHorse` 和 `BhBreedHorse` 都没覆写它，Mixin 只能注入目标类自己声明的方法），
再用 `instanceof AbstractHorse` 收窄回马科。找到 16 格内（`horseDeathFleeRadius`）的其它存活马科，
通过 `FrightenedHorse` 接口写入逃离点和 10 秒时限（`horseDeathFleeSeconds`）。

- 「马匹死亡」接受**任意** `AbstractHorse`：原版马、TFC 的马／驴／骡、Icy 的 15 个品种，以及羊驼。
- 「被吓到的马」要求**同时**是 Icy 品种（实现了 `FrightenedHorse`）**和野马**。
  野马过滤直接写在 `getEntitiesOfClass` 的谓词里，这样不会给已驯服的马写一份永远用不上的受惊状态。
- 连续死两匹马时取「更危险的说了算」：时长取更长，地点取离马更近的。

### 17.5 配置

`config/terras_horsies-common.toml`：

```toml
[neutral_combat]
	# 启用中立化战斗改造：马在自身被攻击时主动反击，并在一段时间或距离后失去仇恨。
	# false = 完全恢复 Icy 原行为（被打只踢一脚、不会追击），本节其余参数一并失效。
	horseNeutralCombat = true

	[neutral_combat.owner_defend]
		# ── 替主人报仇（Icy 原行为，默认保留）──
		# 主人被攻击时，马会去攻击那个攻击者。默认 true。
		# false = 完全中立，只有马自己挨打时才反击。
		ownerDefend = true
		# 替主人报仇时追击多少秒。默认 5，与亲密度无关。
		# 从最后一次被挑衅算起：敌人还在打主人或打马就自动续时，停手满 5 秒才脱战。
		ownerDefendSeconds = 5.0

	# 被自己的马主攻击时完全不反击。默认 false（会被追，但亲密度越高追得越短）。
	neverRetaliateAgainstOwner = false

	[neutral_combat.player_aggro]
		# ── 被玩家攻击：反击时长由亲密度决定 ──
		# 刻度是 Jade 显示值（0~100），三点之间线性插值。
		# 出厂值：0 → 30 秒，10 → 10 秒，≥ 18 → 不反击。
		# 秒数都从「最后一次被挑衅」算起，对方继续动手就续时。
		# 亲密度 0 时，被玩家攻击后持续反击多少秒。
		aggroSecondsAtZero = 30.0
		# 曲线的中间锚点，用 Jade 显示值。默认 10。
		midFamiliarity = 10.0
		# 亲密度等于 midFamiliarity 时，持续反击多少秒。
		aggroSecondsAtMid = 10.0
		# 亲密度达到该值后被玩家攻击也不反击。默认 18。
		pacifyFamiliarity = 18.0

	[neutral_combat.mob_aggro]
		# ── 被非玩家生物攻击 ──
		# 被其他生物攻击时的反击时长（秒）。默认 30，与亲密度无关。
		# 同样从最后一次挨打算起，对方持续攻击就一直续时。
		mobAggroSeconds = 30.0

	[neutral_combat.flee]
		# ── 脱战与逃跑 ──
		# 反击时目标离开多少格后失去仇恨。默认 16。
		retaliateDistance = 16.0
		# 野马血量低于该比例时改为逃离威胁源。默认 0.30，0 = 关闭。
		# 只对野马生效；已驯服的马不会逃跑。
		fleeHealthRatio = 0.30
		# 逃跑时拉开到多少格算脱离仇恨。默认 32，应大于 retaliateDistance。
		escapeDistance = 32.0
		# 周围多少格内有马匹死亡时受惊逃跑。默认 16，0 = 关闭。只对野马生效。
		horseDeathFleeRadius = 16.0
		# 因同伴死亡而逃跑的秒数上限。默认 10。
		horseDeathFleeSeconds = 10.0
		# 野马被仙人掌扎到时挪开一点。默认 true。
		# 只挪短距离（见下面两项），脱离那丛仙人掌即结束。只对野马生效。
		fleeFromCactus = true
		# 被仙人掌扎到后最多挪开多少格。默认 4。
		cactusFleeDistance = 4.0
		# 被仙人掌扎到后的移动速度修正。默认 1.2（走）。
		cactusFleeSpeed = 1.2
		# 被仙人掌扎到后逃跑的安全上限（秒）。默认 10，通常用不到。
		cactusFleeSeconds = 10.0
		# 被打后记住「谁打我」多少秒。默认 10，0 = 不记忆。
		# 低血量逃跑据此判定逃离目标；继续挨打会刷新计时。
		threatMemorySeconds = 10.0
```

> **配置注释的定位**：`.comment(...)` 里只写「开关做什么 + 默认值」，平均 1.2 行。
> 设计论证（为什么是这个数、踩过什么坑、字节码证据）一律留在本 README，
> 免得整合包用户在一个开关上读到二十行散文。

`horseNeutralCombat = false` 会把中立化相关的 mixin 全部短路，完全恢复 Icy 原本的行为
（含它原有的 32 格 / 30% 血量脱战）。`FleeFromThreatGoal` 也会一起失效。

**但 `owner_defend` 不在这个总开关之下**：`ownerDefend` 管的是「要不要保留 Icy 的替主人报仇」，
与中立化改造是两件事。两者的组合：

| `horseNeutralCombat` | `ownerDefend` | 结果 |
| --- | --- | --- |
| `true` | `true` | **出厂默认**：会反击、会报仇，两者都有时间 / 距离脱战 |
| `true` | `false` | 完全中立：只有自己挨打才反击 |
| `false` | `true` | 完全 Icy 原行为（报仇 + 它自己的脱战条件） |
| `false` | `false` | 不报仇、不追击反击，只剩被打时踢一脚 |

唯一的例外是 `AbstractHorseThreatMixin`：它只负责把「谁打了我」记进一个字段，不读配置 ——
在它那一层还不知道这份记忆会不会被用到。没人消费时它没有任何行为影响。

**注意 `retaliateDistance` 不受野马限定** —— 它管的是「反击多远脱战」，
野马和已驯服的马都适用。只有 `fleeHealthRatio` 和 `horseDeathFleeRadius`
这两条逃跑规则限定野马。

### 17.6 不受影响的行为

- **骑手主动冲锋**（`HORSE_CHARGE`）——玩家发动的，不属于「主动攻击敌人」。
- **受惊**（`HORSE_SPOOK`）——`rouse` 里玩家正骑着时的分支走 `rollSpook`，那是害怕、不是攻击。
- **被打时踢一脚**（`HORSE_KICK`）——原样保留，现在只是多了一层追击。
- **TFC 本家的马／驴／骡**——反击相关的三个 mixin 都要求 `bh_getBreedKey() != null` 或直接针对
  Icy 的类；`FleeFromThreatGoal` 只注册在 `BhBreedHorse` 上，`FrightenedHorse` 也只有它实现。
  所以 TFC 本家的马既不会按亲密度反击，也不会因为同伴死亡而逃跑。

### 17.7 验证方式

`DefendOwnerGoalAggroMixin` 的三个注入点走的是**原版方法名**（`canUse` / `canContinueToUse` / `stop`），
目标是 Icy 的类，所以要靠 refmap 翻译。构建后检查
`build/libs/terras-horsies-1.0.0.jar` 里的 `terras_horsies.refmap.json`：

```json
"com/tfcicys/horses/mixin/DefendOwnerGoalAggroMixin":
  "canUse()Z"           -> "Licy/betterhorses/net/goal/DefendOwnerGoal;m_8036_()Z"
  "canContinueToUse()Z" -> "Licy/betterhorses/net/goal/DefendOwnerGoal;m_8045_()Z"
  "stop()V"             -> "Licy/betterhorses/net/goal/DefendOwnerGoal;m_8041_()V"
```

两条空的话，脱战逻辑不会生效（但不会崩）。另外 `@Shadow` 的 `horse` / `target`
是 Icy 自己加的字段，必须写 `remap = false`，否则注解处理器会报
「Unable to locate obfuscation mapping for @Shadow field」。

其余注入点的 refmap 对照：

```json
"com/tfcicys/horses/mixin/AbstractHorseDeathFrightMixin":
  "die(Lnet/minecraft/world/damagesource/DamageSource;)V"
    -> "Lnet/minecraft/world/entity/LivingEntity;m_6667_(Lnet/minecraft/world/damagesource/DamageSource;)V"

"com/tfcicys/horses/mixin/AbstractHorseThreatMixin":
  "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
    -> "Lnet/minecraft/world/entity/animal/horse/AbstractHorse;m_6469_(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
```

`AbstractHorseDeathFrightMixin` 挂在 `LivingEntity.die` 而不是 `AbstractHorse` 上，是因为
`die` 声明在 `LivingEntity` —— Mixin 只能注入目标类**自己声明**的方法，
`AbstractHorse` / `BhBreedHorse` 都没有覆写它。

逃跑用到的关键 SRG（构建后逐个核对过）：

| 调用 | SRG | 备注 |
| --- | --- | --- |
| `LandRandomPos.getPosAway(PathfinderMob, int, int, Vec3)` | `m_148521_` | 和原版 `AvoidEntityGoal` 同一个工具 |
| `PathNavigation.moveTo(double, double, double, double)` | `m_26519_` | 返回 boolean |
| `PathNavigation.stop()` | `m_26573_` | |
| `ServerLevel.getEntity(UUID)` | `m_8791_` | 和 Icy 的 `DefendOwnerGoal` 同一条路径 |
| `Mob.getNavigation()` | `m_21573_` | |

另外 `TFCAnimalProperties.getFamiliarity()` **必须保持未混淆**（TFC 是模组，不在 Mojang 映射里）。
构建后核对 `TFCICYSConfig.familiarityOf` 的字节码，应当是
`invokeinterface net/dries007/tfc/common/entities/livestock/TFCAnimalProperties.getFamiliarity:()F`
—— 如果这里出现了 `m_xxxxx_` 就是出错了。

---

## 18. 仙人掌：穿马铠免疫 + 野马被扎就走开

`AbstractHorseCactusMixin` 用同一次 `hurt` 拦截做两件事：

| | 行为 | 配置 |
|---|---|---|
| ① | Icy 马匹**只要马铠那一格不是空的**，仙人掌就完全伤不到它 | `armor.cactusProofArmor` |
| ② | **野马**被仙人掌扎到时，**挪开一点**离开那丛仙人掌（不是跑远） | `neutral_combat.flee.fleeFromCactus` |

两者**互相独立**，关掉一个不影响另一个。且 ② 的判定排在 ① **之前** ——
穿了马铠的马掉不了血，但「站在仙人掌里不动」本身就不该发生，所以一样让它走开。

两个行为都覆盖**其他模组的仙人掌**。

### 18.1 为什么拦在 `hurt` 而不是 `CactusBlock`

原版仙人掌的伤害路径只有一条（已从字节码核对）：

```
CactusBlock.entityInside(state, level, pos, entity)   // m_7892_
  → level.damageSources().cactus()                    // DamageSources.m_269325_
  → entity.hurt(source, 1.0F)                         // m_6469_
```

拦在 `hurt` 上能顺带覆盖**其他模组**的仙人掌 —— 只要它们的伤害类型认得出来。
如果去改 `CactusBlock`，一个自己覆写了 `entityInside` 的模组仙人掌就绕过去了。

### 18.2 怎么认定「仙人掌伤害」

三层依次尝试，任一层命中即算：

| 层 | 判断 | 覆盖什么 |
| --- | --- | --- |
| 1 | `source.is(DamageTypes.CACTUS)` | 原版类型。**所有直接复用 `damageSources().cactus()` 的模组**，包括继承 `CactusBlock` 而没覆写 `entityInside` 的 |
| 2 | `source.getMsgId()` 含 `"cactus"` | 模组自定义类型，消息 ID 带自己名字的 |
| 3 | `typeHolder().unwrapKey()` 的命名空间／路径含 `"cactus"` | `某模组:cactus_sting` 这类注册名 |

**已知覆盖不到的**：某个模组注册了名字里完全没有 `cactus` 的伤害类型，并用它自己的方块造成刺伤。
这种情况无法从伤害来源识别。要兜住只能改成按 `CactusBlock` 的位置判断，
但那样会把「站在仙人掌旁边被摔伤／被箭射」也算成刺伤，所以这里不做。

### 18.3 怎么判断「穿着马铠」

用原版 `AbstractHorse.isWearingArmor()`（SRG `m_7481_`）。字节码核对结论：

```
AbstractHorse.isWearingArmor()  →  !getItemBySlot(EquipmentSlot.CHEST).isEmpty()
Horse.getArmor()                →     getItemBySlot(EquipmentSlot.CHEST)
Horse.setArmor(stack)           →     setItemSlot(EquipmentSlot.CHEST, stack)
```

读写对称，**`isWearingArmor()` 和 `Horse.getArmor()` 是同一个表达式**，
所以这个判断对马铠是准的。（`Llama` 之所以要覆写 `isWearingArmor()`，是因为羊驼的地毯
放在自己的容器 `f_30520_` 里而不是装备槽。）

因此**不挑材质、不挑模组**：原版马铠、TFC 的九种金属马铠、其他模组塞进
`EquipmentSlot.CHEST` 的马铠，一律算数。

### 18.4 配置

`config/terras_horsies-common.toml`：

```toml
[armor]
	# 穿任意马铠时免疫仙人掌刺伤（含其他模组的类仙人掌方块）。默认 true。
	# 只对 Icy 的 15 个品种生效。
	cactusProofArmor = true

[neutral_combat.flee]
	# 野马被仙人掌扎到时挪开一点。默认 true。
	# 只挪短距离（见下面两项），脱离那丛仙人掌即结束。只对野马生效。
	fleeFromCactus = true
	# 被仙人掌扎到后最多挪开多少格。默认 4。
	cactusFleeDistance = 4.0
	# 被仙人掌扎到后的移动速度修正。默认 1.2（走）。
	cactusFleeSpeed = 1.2
	# 被仙人掌扎到后逃跑的安全上限（秒）。默认 10，通常用不到。
	cactusFleeSeconds = 10.0
```

注意 `fleeFromCactus` 挂在 `neutral_combat` **总开关之下** —— 关掉中立化改造时
它也会一并关闭。

### 18.5 作用范围

只对 Icy 的 15 个品种生效（`instanceof BhBreedHorse`）。原版马、TFC 的马／驴／骡、
羊驼都不受影响。想扩大范围就把 `AbstractHorseCactusMixin` 里那一行
`instanceof` 判断删掉，改 `AbstractHorse` 全体生效（逃跑还额外要求目标实现了
`FrightenedHorse`，目前只有 Icy 的品种有）。

免疫的取消方式用 `cir.setReturnValue(false)`，与 `isInvulnerableTo` 的效果一致：
不掉血、不出音效、不播动画。

### 18.6 逃跑：只挪一下，脱离即停

**这一档不跑远。** 仙人掌是**成丛**长的，按通用逃跑那套冲刺十几格，马很容易一头扎进
旁边另一丛里继续挨扎 —— 这是第一版的实测问题。所以三个参数都往「小」调：

| 项 | 值 | 说明 |
| --- | --- | --- |
| 半径 | `cactusFleeDistance`（4 格） | 是防呆上限，不是要跑到的目标 |
| 速度 | `cactusFleeSpeed`（1.2，走） | 冲刺会一步跨进下一丛 |
| 收尾 | **脱离那丛仙人掌就结束** | 不是「跑够距离」，见下 |

收尾判定写在 `FrightenedHorse#tfcicys$fleePoint()`：仙人掌那一档会先用
`CactusBlocks.isNear()` 扫一遍马身上和紧邻 —— **只要一株类仙人掌都没有了就直接返回
`null`**，`FleeFromThreatGoal` 随之结束。所以实际通常 1~2 格就停，
`cactusFleeDistance` 只是「怎么走都还在挨扎」时的上限。

万一真的一头扎进另一丛，下一次扎刺会**再触发一次短逃跑**，如此逐步挪出去 ——
这个过程是自纠正的，不需要一次就找对方向。

起点分两级：

1. **优先**：扫描马包围盒**外扩 1 格**里的方块，取最近的一株仙人掌，用它的方块中心当起点。
   范围这么定是有依据的：原版 `CactusBlock.entityInside` 只在方块与实体包围盒**相交**时
   才被调用，所以扎到马的那株仙人掌必定落在这个范围里；外扩 1 格是为了容忍「刚擦过、
   这一 tick 已经离开」的边界情况。有具体起点时马是**朝反方向**挪，脱离最快。
   （「是否还挨着」用的是**同一个范围**，语义因此对称：还有东西能扎它 = 真，否则 = 假。）
2. **退回**：一株都认不出来时，直接用 `horse.position()` 当起点，也就是
   「离开当前这一格」。这条路**不依赖任何方块识别**，所以名字里没有 `cactus`、
   也没继承 `CactusBlock` 的异界仙人掌一样能兜住。

「类仙人掌方块」的三层识别（实现见 `com.tfcicys.horses.util.CactusBlocks`，第 1 级覆盖最大）：

| 层 | 判断 |
| --- | --- |
| 1 | `state.is(Blocks.CACTUS)` |
| 2 | `state.getBlock() instanceof CactusBlock` —— 绝大多数模组都继承原版写 |
| 3 | 注册名的命名空间或路径含 `"cactus"` |

三层都不中也不算致命，因为有上面那个退回方案。这个工具类由 `AbstractHorseCactusMixin`
（找起点）和 `BhBreedHorseFamiliarityMixin`（判断是否已脱离）**共用**，
免得两处各写一份识别逻辑、日后慢慢走偏。

**逃跑仍受两条约束**（判定在 `FleeFromThreatGoal.canUse`，本类只预筛「是不是野马」）：

- **只给野马** —— 已驯服的马不会自动挪开（判定同 `fleeHealthRatio`，见 17.4）；
- **被骑着时不接管移动** —— 方向由玩家决定。

另外，中途再被扎会**重新计时**（`cactusFleeSeconds` 是安全上限，正常用不到），
`FleeFromThreatGoal` 每 8 tick 重算一次落点，起点跟着马的位置变，不会跑一半卡住。

### 18.7 三个触发源的尺度一览

`FleeFromThreatGoal` 是**同一个 Goal** 服务三个触发源，半径与速度由触发源通过
`FrightenedHorse` 给出（返回 ≤ 0 时用 Goal 自己的默认值）：

| 触发源 | 半径 | 速度 | 怎么收尾 |
| --- | --- | --- | --- |
| 附近有马匹死亡 | 16 格 | 2.0（冲刺） | 拉开 16 格后停下 |
| 血量不足 | 32 格 | 2.0（冲刺） | 拉开 32 格后清记忆停下 |
| 被仙人掌扎到 | 4 格 | 1.2（走） | **脱离那丛仙人掌就停** |

参数跟着「赢得逃离点的那一次调用」走，和地点保持同一套语义 ——
否则会出现「点是最危险那个、半径却是上一次的」这种自相矛盾的状态。

用同一个 Goal 而不是写三个：方向选择（`LandRandomPos.getPosAway`）、寻路、
8 tick 重算、`isVehicle()` 与野马过滤这些逻辑完全一致，只有
「多远 / 多快 / 何时算完」三个参数不同。搜索半径取本次半径并夹在 3~16 之间，
超过 16 的部分交给 `straightAway` 的退化方案（直线反推）补足。

---

## 19. 骑乘驯服的概率改由亲密度决定

**问题**：原版的骑乘驯服完全不看亲密度 —— 0 亲密度的马照样骑几次就驯服了。

### 19.1 原版的骰子长什么样

驯服逻辑全在原版 `RunAroundLikeCrazyGoal.tick()`（SRG `m_8037_`）里，已从字节码逐条核对：

```java
if (!this.horse.isTamed() && this.horse.getRandom().nextInt(50) == 0) {  // 每 tick 1/50 的尝试闸门
    Entity entity = this.horse.getPassengers().get(0);
    if (entity == null) return;
    if (entity instanceof Player player) {
        int temper    = this.horse.getTemper();       // SRG m_30624_，虚调用
        int maxTemper = this.horse.getMaxTemper();    // SRG m_7555_，原版硬编码 100
        if (maxTemper > 0 && this.horse.getRandom().nextInt(maxTemper) < temper) {
            this.horse.tameWithName(player);          // SRG m_30637_
            return;
        }
        this.horse.modifyTemper(5);                   // 失败也涨 5
    }
    this.horse.makeMad(); ...                         // 甩人 + 粒子
}
```

`getMaxTemper()` 的字节码就是 `bipush 100; ireturn`，**恒为 100**。所以

```
单次成功率 = getTemper() / 100
```

而每次失败都 `modifyTemper(5)`，`temper` 一路涨到 100 —— 于是**骑够久必定驯服**，
0 亲密度也拦不住。这正是要改的地方。

### 19.2 改法：覆写 `getTemper()`

`getTemper()` 是 `AbstractHorse` 上的 public 方法，而 `RunAroundLikeCrazyGoal` 对它的调用是
**虚调用**。所以在 `BhBreedHorseFamiliarityMixin` 里覆写它，就等于直接设定百分比：

```java
@Override
public int getTemper() {
    if (!TFCICYSConfig.tamingByFamiliarity() || isTamed()) return super.getTemper();
    if (!(getFirstPassenger() instanceof Player rider)) return super.getTemper();
    return (int) Math.round(TFCICYSConfig.tamePercent(rider, TFCICYSConfig.familiarityOf(this)));
}
```

构建后从字节码核对（覆写被 reobf 一起重映射为 `m_30624_`）：

```
m_30624_()
  TFCICYSConfig.tamingByFamiliarity()  →  isTamed(m_30614_)
  →  invokespecial Horse.m_30624_()          ← super，指向 AbstractHorse，无递归
  getFirstPassenger(m_146895_) instanceof Player
  TFCICYSConfig.familiarityOf(this) + tamePercent(Player, F)  →  Math.round
```

**为什么不去注入那个 Goal 的字节码**：

- 不用碰别人的字节码，别的模组改 `RunAroundLikeCrazyGoal` 也不冲突；
- 原版的 **1/50 尝试闸门、甩人动作、粒子、音效全部保持原样** ——
  玩家看到的还是熟悉的「骑上去被甩下来」；
- `tameWithName` 仍由原版调用，所以 Icy 的 `bh_claimHorseOnTame`（驯服瞬间认领归属）
  与原版 `CriteriaTriggers.TAME_ANIMAL` 照常触发。

**为什么可以安全覆写**：`getTemper()` 在运行时只有三个读者 —— `RunAroundLikeCrazyGoal.tick()`、
`AbstractHorse.handleEating()` 里的 `getTemper() < getMaxTemper()` 判断、以及存档写入。
已对 Icy 的 jar 做**全包字节级扫描**：它**完全不引用** `getTemper` / `getMaxTemper`
（只碰 `tameWithName` 与 `setTamed`），所以没有第三方的读取会被带偏。
而且只在「被玩家骑着 + 尚未驯服」时改值，其余一律返回原版数值 ——
空着站的马、已驯服的马、存档读写都不受影响。

### 19.3 概率曲线

刻度是 Jade 上显示的那个亲密度（0~100，也就是 TFC 内部 0~1 的 ×100），
在四个锚点之间**分段直线插值**：

| 亲密度 | 0 | 6 | 10 | 18 | 20 | 25 | 30 | 35 | > 35 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 单次成功率 | 1% | 5% | 10% | 20% | 28.2% | 48.8% | 69.4% | 90% | 100% |

三段斜率依次变陡（**0.67 / 1.25 / 4.12** 个百分点每级），低亲密度几乎白搭、
高亲密度收益陡增 —— 这是按需求的四个点定的，不是一条直线。

**尝试节奏没变**：还是每 tick 1/50 的闸门，也就是平均每 **2.5 秒**掷一次。
所以实际耗时大致是：

| 亲密度 | 0 | 6 | 18 | 35 |
| --- | --- | --- | --- | --- |
| 单次成功率 | 1% | 5% | 20% | 90% |
| 平均需要 | 约 250 秒 | 约 50 秒 | 约 12.5 秒 | 约 2.8 秒 |

马匹的亲密度上限就是 **35**（TFC 的 `familiarityCap`），所以最后一档
（> 35 → 100%）正常情况下**到不了**，实际上是最大值 90%；只有把 TFC 的亲密度上限
调高过才会生效。锚点之间是插值，而 35 → 100 这一步是**断开跳变**，
这是按需求「大于 35 必定驯服」写的。

### 19.4 More Attributes 加成

骑手的**力量（`strength`）+ 技巧（`skill`）**等级之和超过阈值时，每多 1 级加 5 个百分点，
最多加 50：

```
加成     = min(50, max(0, 力量 + 技巧 - 30) × 5)     百分点
最终概率 = clamp(曲线概率 + 加成, 0, 100)
```

两项的出厂 `baseLevel` 都是 **10**（见 MA 的 `data/more_attributes/attributes/*.json`），
也就是新角色是 **20** —— **阈值 30 意味着必须真投入 11 级**才吃得到第一档加成：
23 级时 +0，31 级时 +5，40 级时 +50。

读取走 `LevelUtils.getLevel(Player, String)`，键名就是属性 json 里的 `name` 字段。
主属性**不是原版 `Attribute`**，而是 MA 自己存在 `PlayerClassCapability` 里的「等级」，
所以不能像负重那样走 `getAttribute()`。

调用封装在 `MoreAttributesApi.mainAttributeLevel(Player, String)` 里 ——
该方法签名只出现 Minecraft 类型，MA 缺席时不会触发类加载（理由见 14 节）。
读不到等级（缺席／键名不匹配）时返回 0，也就是**加成不生效**而不是算错。

### 19.5 配置

```toml
[taming]
	tamingByFamiliarity = true          # 换成亲密度曲线；false = 恢复原版 temper 机制

[taming.curve]
	anchor2 = 6.0                       # 亲密度刻度上的锚点。锚点 1 固定为 0，所以只填后三个
	anchor3 = 18.0
	anchor4 = 35.0                      # 默认就是马匹亲密度上限
	chanceAtZero = 1.0                  # 各锚点的单次成功率（%）
	chanceAtAnchor2 = 5.0
	chanceAtAnchor3 = 20.0
	chanceAtAnchor4 = 90.0
	chanceAboveAnchor4 = 100.0          # > anchor4 时的成功率

[taming.more_attributes]
	levelThreshold = 30.0               # 力量+技巧超过多少级开始有加成
	bonusPerLevel = 5.0                 # 每超过阈值 1 级加多少个百分点
	bonusMax = 50.0                     # 本加成上限
```

### 19.6 不受影响的部分

- **原版马、TFC 的马／驴／骡、羊驼** —— 覆写只加在 `BhBreedHorse` 上。
- **Icy 自带的「创造模式右键驯服」** —— `HorseCreativeTamingMixin` /
  `AbstractChestedHorseCreativeTamingMixin` 是另一条路（要求创造模式 + 特定物品），
  完全不经过 `getTemper()`，所以仍然是瞬间驯服。
- **喂食提升 temper** —— `handleEating` 里那段 `modifyTemper` 还在跑，只是我们已经不读它，
  无副作用。

---

## 20. 年龄：谁说了算

### 20.1 三个被覆写的方法

TFC 不使用原版的 `age` 字段，它把年龄记在**生日**上。所以本模组让原版字段彻底退出：

```java
@Override public boolean isBaby() { return getAgeType() == Age.CHILD; }  // 读 TFC 的判定
@Override public void setAge(int age) { super.setAge(0); }              // 原版 age 恒为 0
@Override public int getAge() { return isBaby() ? -24000 : 0; }         // 反向假造出来
```

`getAgeType()` 是 TFC 的（`TFCAnimalProperties`）：

```java
final long adulthoodDays = totalDays - this.getBirthDay();
if (adulthoodDays > getDaysToAdulthood()) return Age.ADULT;
return Age.CHILD;                                  // 另外 oldDay 到了会是 OLD
```

也就是说：**年龄 = 当前天数 − 生日**，成年门槛取 TFC 的马配置。

### 20.2 野外生成必须补生日戳

TFC 的 `registerCommonData()` 把生日默认定义成 `0L`，而 Icy 的 `finalizeSpawn` 完全不碰它。
一只刚刷出来的马于是"生日 = 第 0 天"，`当前天数 − 0` 没超过成年天数时就被判成幼年 ——
症状是**野外刷出来的马全部是幼年**。

修法是补上 TFC 自己在 `TFCHorse.finalizeSpawn` 里做的事：

```java
@Inject(method = "finalizeSpawn(...)", at = @At("TAIL"))
private void tfcicys$initCommonAnimalDataOnSpawn(..., MobSpawnType reason, ...) {
    if (reason != MobSpawnType.BREEDING) {      // 繁殖路径的生日由 birthChildren 负责
        initCommonAnimalData(level, difficulty, reason);
    }
}
```

`initCommonAnimalData` 内部是 `setBirthDay(getRandomGrowth(...))`：**5% 幼年、95% 随机年龄的成年**，
生日通常是个**负数**（世界第 0 天之前出生），所以马匹成年并不要求世界天数超过成年门槛。

### 20.3 马匹面板里"全都是幼年"（已修）

**症状**：按马匹面板键打开界面，列表里每匹马都用幼驹模型画出来，而它们实际上是成年的。

**根因**：面板给每匹马现造一个**预览实体**来画模型
（`HorsePreviewCache.build`）：

```java
AbstractHorse horse = (AbstractHorse) type.create(mc.level);  // 裸实体，没有 TFC 数据
horse.setBaby(entry.baby());                                  // ← 唯一的年龄信号
horse.setTamed(true);
```

裸实体的生日是默认值 `0L`，于是 `isBaby()` 读到 `getAgeType()` = `当前天数 − 0`，
在世界天数还没超过马的成年天数时**恒为幼年**；`BhHorseRenderer` 又是**用 `isBaby()` 选模型**
（Icy 有 `SmallFoalModel`/`MediumFoalModel` 等一整套幼驹模型），
所以每匹马都画成幼驹。而"**全都**一样"正是关键线索 ——
所有预览实体共享同一个默认生日，必然得到同一个判定。

那个 `setBaby(entry.baby())` 本来能纠正它，但它内部走的是 `setAge(-24000)` —— 被 §20.1
的空操作吞掉了。实测反编译：`AgeableMob.setBaby(b)` = `setAge(b ? -24000 : 0)`。

**修法**：把原版的"幼年开关"翻译成 TFC 的生日戳，挂在 **`setBaby`** 而不是 `setAge` 上：

```java
@Override
public void setBaby(boolean baby) {
    super.setBaby(baby);
    if (isBaby() == baby) { return; }                  // 已经一致，别动真实马匹的生日
    final long today = getCalendar().getTotalDays();
    setBirthDay(baby ? today : today - getDaysToAdulthood() - 1L);
}
```

- **为什么不能挂在 `setAge` 上**：`AgeableMob.aiStep()` 在幼年期间**每 tick** 都调
  `setAge(getAge() + 1)`，而本类的 `getAge()` 恒为 `-24000`，于是每 tick 都会传一个略大于
  `-24000` 的负数 —— 在那里盖生日戳会让幼驹被反复"重生"，**永远长不大**。
  `setBaby` 没有这个问题：它只由"明确要求改变幼年状态"的调用方使用。
- **为什么提前返回很重要**：真实的幼驹被 `setBaby(true)`、真实的成年马被 `setBaby(false)`，
  都是在说一件已经成立的事，此时**不动生日**，马匹保留真实年龄。
- **为什么不会误伤原版繁殖**：本模组的 `getBreedOffspring` 返回 `null`，原版那条
  `spawnChildFromBreeding` 分支（唯一会调 `setBaby(true)` 的地方）整体被短路，见 §9.3.3。
- **服务端不受影响**：面板每行的"是否幼年"标志由服务端 `HorseManagement` 采集，
  取的是真实马匹的 `isBaby()`，而真实马匹的生日是有戳的 —— 所以这个 bug 只在客户端的预览实体上发作。

### 20.4 验证方式

1. 骑面板键打开马匹列表，每匹马的预览模型应当与它在世界里的实际年龄一致（成年马 = 成年模型）。
2. 用 `/summon` 或刷怪蛋生成一只 Icy 马，观察它是随机年龄的成年个体（偶尔幼年，约 5%）。
3. TFC 繁育出一只幼驹，确认它是幼年、并且会随时间正常长大（不会被反复重置成新生）。

---

## 构建

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.8.7-hotspot'
$env:GRADLE_USER_HOME = '<工作区>\.gradle-home'
cd tfc-icys-horses
.\gradlew.bat build --no-daemon
```

需要 **JDK 17**。TFC 通过 Modrinth maven 依赖，注意 Modrinth 的 maven 要求用
**版本 ID** 而不是版本号（本项目用 `tfc_version=XJqVIULL`，对应
`1.20.1-3.2.26`），写成版本号会 404。

Icy 不在 Maven 仓库上，所以放在 `libs/` 下以 `compileOnly` 引入——
仅用于编译期解析注解处理器需要的类，**不会被打包或再分发**。
Jade 同理（`libs/Jade-1.20.1-Forge-11.13.2.jar`），仅 `compileOnly`。

### 开发环境测试的坑

`runServer` / `runClient` **无法初始化 mixin**，启动时会报：

```
MixinInitialisationError: Error initialising mixin config terras_horsies.mixins.json
Caused by: IllegalArgumentException: The specified resource 'terras_horsies.mixins.json'
             was invalid or could not be read
```

这条消息**容易被误读成 JSON 写错了，其实不是**。它的真实含义是
`MixinConfig.create` 里 `getResourceAsStream(...)` 返回了 null：

```java
InputStream resource = service.getResourceAsStream(configFile);
if (resource == null) {
    throw new IllegalArgumentException("The specified resource '%s' was invalid or could not be read", configFile);
}
```

`mixin { config ... }` 传给 dev 运行的 `--mixin.config` 会被 Mixin 用
**它自己那一层的 classloader**（堆栈里显示为 `MC-BOOTSTRAP`）去解析，那一层
看不到 exploded 的 `build/resources/main` 目录。正式 jar 走的是另一条路径：
manifest 里的 `MixinConfigs` 由 `MixinPlatformAgentMinecraftForge` 在正确的层读取。
**所以这个问题只影响 dev，不影响交付的 jar。**

要在 dev 环境验证 mixin，把构建好的 jar 放进 `run/mods`，并临时注释掉
`runs.server` 里的 `mods { ... }` 块（否则目录形式与 jar 形式的同名 mod 会重复）：

```powershell
Copy-Item build\libs\terras-horsies-1.0.0.jar run\mods\
.\gradlew.bat runServer --no-daemon
```

### 更新 jar 时不要就地覆盖

**不要在游戏运行时覆盖 `mods/` 里的 jar。** 本机实测了三种做法的语义差异
（方法：先打开目标文件持有一个读句柄模拟 Forge 的 `SecureJar`，再替换文件，最后读旧句柄）：

| 做法 | 目标文件被占用时 | 旧句柄读到 | 结论 |
|---|---|---|---|
| `Copy-Item -Force` | **照样成功** | **新内容** | 就地截断重写，**危险** |
| `[System.IO.File]::Replace($tmp, $dst, $bak)` | 抛「另一个进程正在使用此文件」 | 旧内容 | 安全，但被占用时用不了 |
| `Move-Item -Force` | 抛「Cannot create a file when that file already exists」 | 旧内容 | 同上 |

也就是说：**`Copy-Item -Force` 是唯一能在游戏运行时「成功」的做法，而它恰好是最危险的那个** ——
它截断重写同一个文件，已打开句柄看到的字节因此全变了。
Forge 的 `ModuleClassLoader` / `SecureJar` 在**启动时**缓存了 jar 的中央目录，
之后按缓存偏移懒加载尚未加载的类，于是：

- 启动时**已经加载过**的类（两个 provider 匿名类、`@WailaPlugin` 本体）完全正常；
- **尚未加载**的类读到的却是新字节 —— 典型症状就是
  `NoClassDefFoundError: ...IcyHorseJadePlugin$3`（7.2 那个真实案例），
  而且往往在游戏跑了十几分钟后才突然出现，极易误判成「某个操作触发的 bug」。

**正确做法：先完全关闭游戏，再替换 jar，然后启动。**

换完 jar 不重启的话，游戏里跑的仍然是旧代码（JVM 不会重新加载已加载的类），
而且随时可能冒出上面那种由文件错位引起的**假故障** —— 排查时白白浪费一轮。

另外 dev 环境里 **Icy 自己的 `LeafPassthroughMixin` 会失败**
（`@Shadow method m_60734_ ... was not located`）：Icy 的 jar 在 `libs/` 下以
`fg.deobf(files(...))` 引入时 Gradle 无法反混淆（构建日志会提示
`Cannot deobfuscate dependency ... using obfuscated version!`），于是 dev 的
MCP 命名下它的 refmap 对不上。真实环境用 SRG 命名，不受影响。

判断本模组是否通过校验，看这几行：

```
Selecting config terras_horsies.mixins.json
Remapping refMap terras_horsies.refmap.json using ...\output.srg
Preparing terras_horsies.mixins.json (3)
Prepared 85 mixins in ... sec
```

只要这几行出现、且附近没有提到 `terras_horsies` 的 `ERROR` / `FATAL`，
就说明配置、refmap 与**全部 `@Inject` 目标方法**都通过了 Mixin 的准备阶段校验
（目标方法找不到会在这一步直接报 `Cannot find target method`）。

---

## 许可

本附属模组以 **MIT** 授权，见 `LICENSE`。

### 上游授权与合规审查

上游情况：

| 上游 | 授权 | 与本项目的关系 |
|------|------|----------------|
| TerraFirmaCraft | **EUPL-1.2**（强 copyleft） | 编译期引用 + 只调用公开 API，不复制任何代码或素材 |
| Icy's Better Horses | jar 内为 **CC0-1.0**，作者对外宣称 **ARR** | 见下节 |
| Jade | CC BY-NC-SA 4.0 | `compileOnly`，只调 API，不打包也不再分发 |
| More Attributes | MIT | 同上 |
| AstikorCarts / TFCAstikorCarts | MIT | 同上 |

本项目的 jar 里**只有自己的东西**：自己的 class、自己的贴图、数据包标签、mixin 配置与
`mods.toml`。已逐项核实：**不含任何上游的 class 文件或二进制素材**。
`libs/` 里的第三方 jar 只是构建期依赖，**不要随源码仓库一起发布**（已加进 `.gitignore`）。

### 与 Icy 授权的关系（本次审查结论）

Icy 的 jar 内 `mods.toml` 写的是 `license="CC0-1.0"`，并附带完整的 CC0 1.0 法律文本
（`LICENSE_icys_better_horses`）；但作者在对外页面声称 **All Rights Reserved**。
两者冲突时本项目**按更严格的 ARR 口径自查**，逐条结论：

- **代码 —— 干净。** 本项目不含任何 Icy 的代码。涉及 Icy 内部行为的部分
  （`getTemper`、`BhHorseCombatAlert.defend`、`DefendOwnerGoal` 等）都是**反编译阅读其行为**
  之后用本项目自己的代码重新实现的；成品 jar 里没有任何 Icy 的 class。
- **运行期 mixin —— 低风险。** mixin 在运行期改写 Icy 的字节码，但不复制、不改写磁盘上的
  Icy jar，也不分发它。这是所有附属模组的通用做法。
- **接入方式 —— 只用官方数据包标签**
  （`data/icys-better-horses/tags/entity_types/horses.json`）与公开 API。
- **素材 —— 唯一已知的灰色地带。** 第 15 节那 **72 张马铠贴图**经像素级比对，确认是 Icy 的
  `armor_iron.png`（与 `armor_generic.png` 字节相同）的**调色板替换派生作品**：
  8 个品种目录下的 `wrought_iron.png` 与 Icy 原文件**字节完全相同**；其余每张与 Icy 那张图的
  颜色映射**唯一度 100.0%、歧义像素 0**（输入色只有 18 种），也就是"把一张图的 18 个颜色
  逐一换成另外 18 个颜色"。

  这在 ARR 口径下属于禁止的派生作品，在 **CC0 口径下完全许可**。评估过三条出路
  （重新生成原创贴图 / 删掉该功能 / 依赖 CC0 授权），
  **项目所有者决定依据 jar 内 CC0-1.0 的正式授权保留现状** —— 该授权是随发行版一起给出的
  正式授权，且 CC0 一经授出不可撤销。
  **若上游后续否认 CC0、坚持 ARR**，处理方式是把这 72 张重新画一遍：
  模型 UV 的占用形状可以保留（那是与 Icy 模型互操作所必需），可见像素必须全部原创。

> TFC 的 EUPL-1.2 是强 copyleft。贴图比对确认：我们这 72 张（去重后 63 张）
> **没有任何一张与 TFC 的 4438 张贴图字节相同** —— 它们的底图是 Icy 那张，不是 TFC 的。
> TFC 只通过公开 API 与编译期依赖使用，不构成派生作品。
