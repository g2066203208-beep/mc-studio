---
name: immersive-vehicles-modeling
description: 用真实 Immersive Vehicles/MTS 车辆资产研究并制作可运行的 Minecraft 低多边形车辆模型（尤其摩托车）；包含 Blockbench OBJ、UV/PNG、零件动画、车辆 JSON、网页真 3D 预览、来源审核和自动质检。遇到车辆建模、参考沉浸车辆、OBJ 贴图或模型导入问题时使用。
---

# 沉浸车辆风格：真实资产建模 Skill

> **目标**：交付可在网页自由旋转检视、并可逐步接入 Minecraft 的**真实三维模型**，而不是把生成图片或静态截图叫作模型。视觉参照项目：[DonBruce64/MTSOfficialPack](https://github.com/DonBruce64/MTSOfficialPack)。我们只复用**方法**，不声称作者资产归我们所有。

## 使用时序

1. **定范围**：确认 MC/NeoForge/MTS 与车辆类型、使用场景、朝向、驾驶姿势；若用户明确以沉浸车辆为参考，默认采用低多边形、清楚的机械轮廓、原版像素质感；**不要擅自决定装饰、品牌、国别与涂装**。
2. **读真实来源**：优先读取 `references/source-matrix.md`，定位同类车辆的 `.obj`、`textures/vehicles/*.png`、`jsondefs/vehicles/*.json`；区分 GitHub 源码版与 Modrinth 发布 JAR。必须记录获取日期、来源路径、SHA/版本、许可核查结果。
3. **拆结构**：读 `references/geometry-and-style.md`，先对照 3–5 辆车统计顶点、面、对象、UV、功能插槽、动画分组；形成比例与功能草图，先主轮廓再细节。
4. **设接口**：读 `references/mts-asset-contract.md`，明确车架/车壳、轮子插槽、车把/方向盘、车灯、座椅、引擎、活动支架的 **object name、pivot、UV**；游戏装配件和车身 OBJ 不是一回事。
5. **做真模型**：首选 Blockbench 建模，导出 OBJ（保留 `o` 对象分组）+ UV PNG；可用受控程序几何出初稿，但必须有**真实可选中网格**。新建资源坚持独立原创，不复制官方网格或贴图。
6. **离线检验**：运行 `python3 skills/immersive-vehicles-modeling/scripts/inspect_obj.py path/to/model.obj --texture path/to/atlas.png --definition path/to/vehicle.json --json`；处理所有 error，人工评估 warning，不为追求数字而滥加多边形。
7. **真实 3D 验收**：从仓库 `viewer/index.html` 的 Three.js 验证流程出发，支持旋转、缩放、近景、侧面、正面、俯视、线框、部件开关；**确实打开 OBJ**，不可用图片生成的三视图替代。提供相同轴向多视角检查、透明件/贴图路径检查。
8. **实际游戏验收**：接入 MTS 数据包后，逐项测试安装轮胎、方向、座位、灯光、运动学、碰撞和动画。**OBJ 能打开 ≠ 游戏可驾驶 ≠ 与 Sable 完全兼容**。
9. **交付与复盘**：提交模型源码、UV PNG、JSON、检查 JSON 报告、网页预览说明、已测试/未测试表和 Git commit/PR。项目版权及来源清楚。

## 防止再次做错的硬要求

- **用户要实际看模型 → 必须呈现可旋转的真实 3D 网页或可下载 3D 文件**；静态渲染图只作为附属检视证据。
- **结构不能假装完整**：真正 MTS 的发动机、座椅、轮子通常独立作为部件 `parts` 安装；仅载入车身 OBJ 时轮子缺失不一定是车模错误。没有完成装配的网页必须明确标注。
- **不混淆版本**：官方 GitHub 上的 Mustang 源文件统计为 3024 顶点 / 2101 面 / 37 对象；此前下载的 V29 JAR 中 Mustang 为 4065 顶点 / 2911 面 / 51 对象。两个统计都真实，但不是同一个资产版本。
- **MTS 的命名不是凭空赋予功能**：`$door_l`、`&headlights` 等先是 OBJ 对象名；动画/灯光行为由 JSON 中相应 `objectName` 和配置驱动。未经该版本引擎源码核查，不保证特定前缀为强制语法。
- **贴图必须取对目录**：曾把 `textures/item/fordmustang69_red.png` 的 32×32 物品图误当成车身贴图。模型材质应匹配 `textures/vehicles/fordmustang69_red.png`（V29 为 512×512），并实际检验 UV 显示。
- **勿自称完成**：先看实际输出并测试；没有实测时把“代码完成”“构建通过”“网页 3D 可用”“进入游戏验收”分别报告。
- 原始 Pack 的第三方资产**不直接镜像**到项目，也不抄改网格和 UV 贴图后当原创。许可证不明时先查清再考虑分发。

## 该 Skill 的参考文档

- `references/source-matrix.md`：真实来源、统计、V29 实际发布包对照及事实/推断边界。
- `references/geometry-and-style.md`：可复用造型法、UV/多边形取舍及原创摩托车阶段设计。
- `references/mts-asset-contract.md`：OBJ、PNG、车辆 JSON、插槽、动画与物理的契约。
- `references/review-and-release.md`：真 3D 检视、游戏验收和防错交付检查单。
- `scripts/inspect_obj.py`：纯标准库的 OBJ/UV/PNG/JSONC 结构检查工具。
- `tests/test_inspect_obj.py`：最小模型和边界条件单测。

## 下一次开始做摩托车时

先执行：读完上述四个参考文件 → 选择车型/骑姿与原版方块手臂是否参与 → 画 **文字比例表和机械分组设计** → Blockbench/程序建一个没有品牌、没有花哨装饰的原创二轮车 → 在真实 3D 网页展示侧面及 3/4 透视（用户先评价轮廓） → 再做 UV、贴图、MTS 功能配置和游戏测试。**严禁拿 AI 生成的摩托车宣传画当成可用 3D 资产。**
