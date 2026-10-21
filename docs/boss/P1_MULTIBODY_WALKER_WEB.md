# P1 — GitHub Pages Create × Simulated 六足真实多体机械 Boss

入口：https://g2066203208-beep.github.io/mc-studio/rpms/boss.html
旧版 P0 单刚体四腿对照：https://g2066203208-beep.github.io/mc-studio/rpms/boss-p0.html

## 真正的物理机构

- 1 个有质量、有惯性且自由漂移的主机身 + 6 根上腿 + 6 根下腿：共 13 个独立 Rapier 动态刚体。
- 6 个髋关节 + 6 个膝关节：共 12 个 Rapier Revolute 真正的旋转约束，分别有限位，碰撞、质量、重力与地面接触由 WebAssembly 处理。
- 腿的真实尺寸：上节长 1.65 格、下节长 1.75 格。中心机身宽约 5.6 格，长约 5.3 格，六只独立金属脚各有真实方块碰撞体。
- 真实电机：按当前角度和目标角度计算 PD 伺服器转矩；对铰链两侧动态刚体施加等大反向角冲量，电机有硬转矩上限。没有直接设置 RigidBody 世界坐标或修改动画姿态冒充受力。
- 控制：站立、两组三足交替抬腿、前进步态实验、断电（真实失去电机保持力），撞击、断腿。拆一腿时真实解除 2 个关节约束，四脚一体式假腿已经被废弃。
- 动力：Create 风格马达 RPM 影响步态相位；浏览器齿轮箱选 1:1 / 2:1 / 4:1，近似 90% 效率，降低输出 RPM、增大允许的实际伺服转矩。不是原版 Create 全部 SU 网络。

## 模型与原版资源

- 马达、大小齿轮、横向传动轴、机壳、Simulated 风格髋膝旋转轴承直接参与构建大型六足机械兽的可见外观。Three.js 将每一节零件挂在独立 Rapier RigidBody 的当前姿态上。
- 初次打开立刻使用可见原创机械预览；后台直接从官方 Modrinth CDN 下载用户浏览器内的 Create 6.0.10 / Aeronautics 1.3.2 JAR，解析原始 static blockstates/models/PNG 后替换预览静态模块。
- 不将上游 All Rights Reserved 纹理或 JAR 再分发在公开仓库。

## 严格验收

1. 真正的 Chrome WebGL+WASM GameTest 构建 13 个真实动态刚体、12 个真正关节。
2. 没有外部手工锚点的重力站立：六个脚部真实落地，躯干受脚底碰撞反作用力支撑。
3. 交替步态改变实际测得的物理髋膝角度，不只更新控制器目标值。
4. 拆一条腿：12→10 个真正关节、6→5 个有用支撑脚；其余断开的腿成为独立真实重力碎块。
5. 实际网页渲染至少 13 个动态刚体绑定的可视组，机械零件可见并随每帧物理运行，含 Chrome 桌面/移动端截图。
6. 单独官方 JAR E2E 测试，要求自动载入真实 Create 齿轮、马达和 Simulated 旋转轴承静态模型贴图。

## 尚未完成

真实 Minecraft Java/NeoForge、Create KineticNetwork 原版应力和传动、Sable 完整 SubLevel 物理接口并没有移植进 GitHub Pages；现阶段网页使用真实 Rapier 刚体与扭矩约束，但马达和齿比为自研物理输入。多关节 IK、长期稳定行走、自动越障、武器攻击、Boss AI、多玩家同步仍需逐阶段开发。不能把 P1 部分机械测试通过说成整个航空学 Boss 完工。
