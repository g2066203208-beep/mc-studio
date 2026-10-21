# P0 · GitHub Pages 原生网页真正物理 Boss（不需要下载 Mod）

地址：https://g2066203208-beep.github.io/mc-studio/rpms/boss.html

**用户要求所有交互在托管网页直接实现**。以前误将 NeoForge GameTest + Mod JAR 当作网页交付，导致用户必须下载客户端，现已纠正。独立的网页实验场集成到 RPMS 原有 3D 工作台导航与醒目入口。

## 已实现

- HTML/Three.js 动态显示 3×3 铜方块机身 + 四根两格高立柱/铁脚，17 个可见的 1×1×1 Minecraft 体素。
- 浏览器直接从 pinned 公共 CDN 载入 **@dimforge/rapier3d-compat@0.17.3** WebAssembly。在浏览器（不是在后台服务器）创建 Rapier `World`，引力 `(0,-11,0)`，固定 `1/60s` 物理步长。
- 真实动态刚体 1 个，其上附着 17 个**不同的真实立方体碰撞体**，机身 9 格密度 2，腿部 8 格密度 1.375。真实刚体质量约 29，碰撞的法向反作用力支撑起机身，摩擦与碰撞由 Rapier 求解。静态地面碰撞体顶面 y=0。
- 真正游戏物理交互：按钮拆除一条腿/全部四条腿；也可以直接点击 3D 腿部方块。不是隐藏模型，而是从物理世界调用 `world.removeCollider`，然后 `body.recomputeMassPropertiesFromColliders()` 真实更新质量/惯性/重心。可施加侧向冲量、暂停/继续、单帧步进、重置。
- 可见状态完全跟随每帧 `body.translation()` 与 `body.rotation()`；**没有运行时人工 `setTranslation`、匀速动画伪造坠落、反重力、隐藏支架或图形插值冒充物理**。
- 右侧直接显示真实 Rapier 质量、机身底部高度、所有有效碰撞体最低点与地面的间隙、真实速度与步数。机身倾斜时按实际刚体四元数投影计算高度。
- 手机和桌面响应式；独立 Chrome CI 测试与截图。

## 真正 Chrome + WASM 物理验收记录

源：`.github/workflows/rpms-browser-physics-boss.yml` / `viewer/rpms/tests/boss-browser-physics.cjs`。对真实网页（非 DOM 模拟/纯数学函数单测）检查：

1. 客户端加载 Rapier WASM，物理刚体包含 17 个实际碰撞体、质量约 29。
2. 静态地面支持四脚：经过 275 次真正 `world.step()`，机身底面停在 2.00 格附近、脚底间隙约 0、竖向速度约 0。
3. 点击拆除一条腿，4 变 3；点击拆全部，碰撞体从 17→9、真实质量约 29→18。
4. 再计算 240 帧，躯干底面由约 2.00 格真实坠落到 0 格，与地面碰撞停稳。
5. 截图归档站立、坍塌及手机视图，CI 报错则阻止合并。

首次通过运行：[39629660133](https://github.com/g2066203208-beep/mc-studio/actions/runs/39629660133)，测试实测 `bodyBottom=1.99896` → `bodyBottom=-0.00102`，`mass=28.999996` → `mass=17.999998`，不是假的位置动画。

## 与 Minecraft 原版 Sable/Simulated 的准确区别

- **相同：** 真正的 Rapier 刚体物理求解，实际重力、真实立方体碰撞、地面支撑法向冲量、摩擦、外部冲量与自动计算的质量属性。
- **不同：** 网页是 JavaScript/WASM 版本的 Rapier，**不是将 Minecraft Java/NeoForge、Sable 的 SubLevel 运行时和 Create 机器逻辑完整嵌入浏览器**。各 Mod 原版装配器、机器动力网络、关节伺服器、单元方块的原版碰撞形状、动画渲染器、多人世界没有移植。不能将 P0 实验成功宣传为真实 Minecraft 全部游戏机制已重建。
- 下一步网页开发：四条腿独立的物理刚体与四个真 revolute joints → joint motor 控制髋角与接触 → 抬腿/站立 → 2段腿各独立刚体 8 个约束 → 四足慢速步态 → 六足 Boss。所有关节必须是实际 Rapier 约束和受力，**绝不人为每帧调节腿的位置**。
- 对照的服务器原版 GameTest 仍可供验证：`docs/boss/P0_FOUR_FOOT_RIG.md`，但**用户测试网页不应要求下载 Mod、JAR、启动 NeoForge**。
