# Terra's horsies [TFC x icy's horses]（大地上的马驹们）

把 [TerraFirmaCraft](https://modrinth.com/mod/terrafirmacraft)（TFC）与
[Icy's Better Horses](https://github.com/Icys-Better-Horses)（Icy）接起来的
**Minecraft 1.20.1 / Forge** 附属模组 —— **让 Icy 的马真正生活在 TFC 的世界里**。

## 它做了什么

- Icy 对马匹的改造（性别、年龄、掉落、喂食）**同样作用于 TFC 的马、驴、骡**；两边性别判断统一。
- 15 个品种获得 TFC 同款**亲密度**（喂食增长、久不喂衰减），并成为繁殖与骑乘驯服的判据。
- **怀孕期**、由 TFC 生日决定的年龄、衰老减速，取代瞬间产仔；繁殖不需要金苹果。
- 新增物品标签 **`#terras_horsies:horse_food`**：带此标签的食物可喂马，也能吸引马。
- 战斗**中立**：不无故出手，仅在自身或主人被攻击时反击；脱战同时看时间与距离且**续时**。
- 危险回避：野马血量低或同伴死亡会逃跑；**穿马铠免疫仙人掌**；受非生物伤害
  （岩浆、火焰、掉落物……）会**持续挪开直到不再受伤**。
- **负重**（可选 More Attributes）：五类马各有上限，继承骑手体重与背包、马车整车重量、
  **驴骡箱子里的货物**，超重按比例减速。
- **马车**（可选 AstikorCarts TFC）：解除车厢物品尺寸上限、移除击倒／过载／力竭，
  **吹哨召回前先卸车并请下乘客**。
- TFC 的 **9 种金属马铠**拥有 Icy 风格贴图；气候生成接入 TFC 的 fauna 规则（KubeJS 脚本）。

## 依赖

| 模组 | 版本 | |
|---|---|---|
| [TerraFirmaCraft](https://modrinth.com/mod/terrafirmacraft) | `1.20.1-3.2.x` | **必需** |
| [Icy's Better Horses](https://github.com/Icys-Better-Horses) | `2.x` | **必需**（2.0.6 / 2.1.0 均已实测） |
| [Jade](https://modrinth.com/mod/jade) | `11.x` | 可选 —— 悬停显示马匹信息 |
| [More Attributes](https://github.com/HLGOrganization/More-Attributes) | `1.0.2` | 可选 —— 负重系统 |
| AstikorCarts TFC | `1.1.8.3` | 可选 —— 马车牵引 |

只装前两个即可正常游玩；可选项缺席时对应功能自动关闭，不会报错。

## 安装

1. Minecraft **1.20.1** + **Forge 47.x**。
2. 把上述 jar 一起放进 `mods/`，启动即可。

配置在 `config/terras_horsies-common.toml`（英文注释），数值几乎都能调。
从旧的 `tfc_icys_horses` 升级、以及构建／部署方式，见[开发文档](docs/DEV_NOTES.md)。

## 授权与致谢

**MIT** 授权，见 [`LICENSE`](LICENSE)。感谢 TFC 与 Icy —— 没有它们就没有本项目。
本模组的 jar 里只有自己的代码与贴图，第三方模组仅作编译期依赖、不再分发；
贴图授权细节见[开发文档的许可一节](docs/DEV_NOTES.md#许可)。

---

**开发文档**（构建、部署、逆向结论、踩坑记录）：[`docs/DEV_NOTES.md`](docs/DEV_NOTES.md)
