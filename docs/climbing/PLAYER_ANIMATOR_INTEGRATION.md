# PEAK 攀爬 — Player Animator 正式集成

2026-10-20。开发分支 `feat/peak-player-animator`，Draft PR #6。

## 依赖与安装

- Minecraft 1.21.1、NeoForge 21.1.248、Java 21。
- **强制单独安装** KosmX **Player Animator 2.0.4+1.21.1（NeoForge / Forge 文件）**：
  https://modrinth.com/mod/playeranimator/version/2.0.4%2B1.21.1-forge
- Sable 2.0.6 是原设计要兼容的物理模组，另行安装。普通静态 Minecraft 方块也可以用于验证攀爬。
- Mod 文件夹只保留一个 `reme_studio` JAR，旧 `0.2.0`、`0.3.0`、`0.4.0` 不得同时安装。还应在**独立测试实例/备份世界**测试，不能覆盖正式档。

## 研究与 API 使用

仓库及 API 源码：https://github.com/KosmX/minecraftPlayerAnimator
- 版本 2.0.4（2025-12 构建）允许在 Minecraft 1.21.1 NeoForge 上独立装载。MIT License。
- 使用 `PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory` 注册每位客户端玩家独立动画 `ModifierLayer<IAnimation>`。
- `PlayerAnimationRegistry.getAnimation` 在客户端资源管理器载入时解析 `assets/reme_studio/player_animations/peak_climb.animation.json`。
- 播放 `peak_hang`、`peak_ascend`、`peak_traverse`、`peak_mantle`、`peak_release` 五套原创 Gecko 关键帧，4 tick 淡入淡出。
- `KeyframeAnimationPlayer.setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL)` 要求由 Player Animator 将原版玩家手臂模型用于第一人称。这与第三人称动作来自同一个资源，不会每个视角另外维护一套固定手臂偏移。
- `ClimbMotion` 的动作分类保存在 `ClimbState.poseAction` 中（0 挂墙、1 向上、2 横移、3 翻顶），由 `ClimbNetwork.Pose` 发给附近客户端。ClientTick 根据每个玩家的动作选择并切换动画。
- 删除旧 `PeakPlayerModelMixin`、`PeakFirstPersonHandsMixin`、`VanillaGripHands` 和 REME 的旧 Mixin 配置，避免与 Player Animator 的模型转换互相覆盖。
- `PeakLedgeAssist` 检查真实墙顶上方空间，使用有碰撞的实体速度在有限 tick 内向上/向里过渡。**移动/旋转的 Sable Sub-level 暂时不使用该翻顶辅助**，需要独立碰撞验收。

## 已有的验收与未完成的验收

自动测试：
1. Java/JUnit 编译，并运行 `PeakAnimationResourcesTest`，使用 Player Animator **自己的资源解码器**核对五套动画及可播放性。
2. GameTest 6 面检测、按住左键抓握/松开、体力与 Shift 冲跃、阻挡/无遮挡翻顶状态。单个运行内目前有 4 项测试。
3. 加载真实 Sable 与 Player Animator 后运行相同 GameTests。
4. Xvfb 软件 OpenGL 下启动 NeoForge 客户端与实际模组（这里只能检查资源初始化/Mixin 故障）。

**尚未可宣称成功**：
- 人工进入独立测试世界，从第一人称确认两只手实时伸向墙面并正确显示；F5 第三人称查看左右手、身体与腿的完整动画。
- 爬上连续十格高墙、左右横移、攀上尽头的平台，而不仅仅是模拟角色速度与状态；不应穿墙或原地浮动。
- Sable 真正移动、旋转飞船上的攀爬与运动平台同步；需要专用双人客户端/服务端测试。
- 与 ParCool / 其他玩家动作 Mod 的动画图层优先级冲突。
- 工具/武器持有、原版采矿触发、玩家左右手惯用设置的协调。

仅当图形客户端亲自操控与录像复核通过后，才把 PR #6 从 Draft 改为 Ready 或尝试合并 main。编译和 GameTest 成功**不是 PEAK 完全复刻的证明**。
