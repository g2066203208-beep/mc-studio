# 实物研究：Immersive Vehicles / MTS 官方资产矩阵

本页是**已亲自读取原始 OBJ 和车辆 JSON 后记录的**证据，不是看宣传图片猜测。原作者仓库：[DonBruce64/MTSOfficialPack](https://github.com/DonBruce64/MTSOfficialPack)，路径根：
`src/main/resources/assets/mtsofficialpack/`。

## 源仓库对照（2026-10-21）

| 车辆 | OBJ 文件 | 顶点 v | UV vt | 多边形 f | 对象 o | JSON 动画对象 | 功能 parts | 碰撞组 |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| 1969 Ford Mustang | `fordmustang69.obj` | 3024 | 8380 | 2101 | 37 | 21 | 9 | 25 |
| Scout 越野车 | `scout.obj` | 1192 | 3456 | 864 | 30 | 19 | 16 | 20 |
| GMC Brigadier 卡车 | `gmcbrig.obj` | 2600 | 7972 | 1995 | 26 | 12 | 15 | 10 |
| E500 飞机 | `e500.obj` | 1128 | 3312 | 828 | 41 | 28 | 11 | 4 |
| Bell 47G 直升机 | `bell47g.obj` | 1731 | 5377 | 1427 | 20 | 10 | 14 | 3 |

此处 UV `vt` 是 OBJ 的纹理坐标条目数，**不等于** PNG 像素数，也不是唯一物理顶点数。网格从 Blockbench（Mustang OBJ 标注 `Made in Blockbench 4.10.4`）导出，源文件包含 `v` / `vt` / `vn` / `o` / `f`。各车统计不代表所有 MTS 车型的官方面数限制。

源文件（点击即是实际 OBJ/JSON）：
- [Mustang OBJ](https://github.com/DonBruce64/MTSOfficialPack/blob/master/src/main/resources/assets/mtsofficialpack/objmodels/vehicles/fordmustang69.obj) / [JSON](https://github.com/DonBruce64/MTSOfficialPack/blob/master/src/main/resources/assets/mtsofficialpack/jsondefs/vehicles/fordmustang69.json)
- [Scout OBJ](https://github.com/DonBruce64/MTSOfficialPack/blob/master/src/main/resources/assets/mtsofficialpack/objmodels/vehicles/scout.obj) / [JSON](https://github.com/DonBruce64/MTSOfficialPack/blob/master/src/main/resources/assets/mtsofficialpack/jsondefs/vehicles/scout.json)
- [GMC OBJ](https://github.com/DonBruce64/MTSOfficialPack/blob/master/src/main/resources/assets/mtsofficialpack/objmodels/vehicles/gmcbrig.obj) / [JSON](https://github.com/DonBruce64/MTSOfficialPack/blob/master/src/main/resources/assets/mtsofficialpack/jsondefs/vehicles/gmcbrig.json)
- [E500 OBJ](https://github.com/DonBruce64/MTSOfficialPack/blob/master/src/main/resources/assets/mtsofficialpack/objmodels/vehicles/e500.obj) / [JSON](https://github.com/DonBruce64/MTSOfficialPack/blob/master/src/main/resources/assets/mtsofficialpack/jsondefs/vehicles/e500.json)
- [Bell OBJ](https://github.com/DonBruce64/MTSOfficialPack/blob/master/src/main/resources/assets/mtsofficialpack/objmodels/vehicles/bell47g.obj) / [JSON](https://github.com/DonBruce64/MTSOfficialPack/blob/master/src/main/resources/assets/mtsofficialpack/jsondefs/vehicles/bell47g.json)

## 研究得到的构造事实

1. Mustang **主车型车身**模型里面可以按名字找到 `$door_l` / `$door_r`、`$hood`、`$steering_wheel`、`$pedalgas`、`$pedalbrake`、车灯对象、排气管与泥渍叠层；JSON 的 `rendering.animatedObjects` 对应这些 `objectName`，动画中心使用 `centerPoint`，类型与变量如 `rotation` + `door_l`。
2. Mustang `parts` 为 **四个 ground_wheel 插槽、两个 seat、engine_car、出售标牌及交互箱**。前后轮 Z 约 3.5/-0.1875，X 约 ±1.125；这展示同一轴向与位置约定，不应直接作为摩托车坐标。
3. Scout 的 body 只是部分组件；门、罩、踏板、方向盘、轮轴与相关可动件都能独立检视；车身比较省面（864 f），但机械识别度明确。
4. GMC 以更长的底盘、更大的驾驶室和多处轮胎安装位形成卡车轮廓，并非只把小车按比例拉长。
5. E500/直升机对舵面、旋翼、仪表、灯具有更多独立对象和动画，说明**可动部位复杂度**可能比静态网格面数更重要。不能根据它们的 f 数推断小汽车目标面数。

## 重要：同一车不同来源文件不同

真实从 Modrinth 下载并通过 SHA 检查的发布包（2026-10-20）：
- Immersive Vehicles 1.21.1-24.0.0，核心 JAR 6,131,756 bytes。
- MTS Official Pack 1.21.1-V29，资源 JAR 70,999,728 bytes，SHA256:
  `f82e67f6c0cf85f79c6b295ef874890e0dc4460204d7768a4a46354d87a0b5d8`
- 发布包内 Mustang 是 **4065 v / 11598 vt / 2911 f / 51 o**；`assets/mtsofficialpack/textures/vehicles/fordmustang69_red.png` 是 **512×512**，并非 `textures/item` 的 32×32 库存图。

这份发布包核验记录已存在项目 `research/immersive-vehicles/jar_inspection.json`；对照网页 `viewer/index.html` 可读取原 GitHub 源或导入完整 JAR。**不要混写成同一个统计**。每次正式建模前重新锁定本次资产版本与 SHA。

## 许可、可复现性与不确定项

我们确认了上游仓库路径及 Modrinth 包内容，但**未确认该上游所有模型/贴图的再分发许可证**。因此只分析结构和原创实现；不把官方模型/贴图复制到本仓库。引用包含来源链接。MTS 对某个前缀/动画变量的完整解释仍须从**目标版本引擎源码**和游戏内行为交叉验证。
