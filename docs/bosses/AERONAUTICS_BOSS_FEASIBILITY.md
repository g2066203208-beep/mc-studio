# 巨型物理恐龙 Boss：航空学现有方块拼装审查与缺口清单
目标环境：Minecraft 1.21.1 / NeoForge 21.1.248 / Java 21 / Sable 2.0.6 / Create Aeronautics 1.3.2
状态：设计与源码核查，未实际组装四足 Boss，未通过此版本航空学 + 附属组件的实机兼容测试。

## 核心判断

游戏里真正一块一块拼恐龙，而不是 Entity 驱动大模型。每个可动部分必须是物理 SubLevel，真实碰撞、真实质量与重力、约束连接、机械动力或可测限幅电机驱动。AI 只能发关节目标、速度和力矩，不允许每 tick 直接传送肢体方块。玩家用现有 PEAK Mod 攀爬动态块并损坏身体，伤害取自真实结构快照差异而非隐藏普通实体血量。

Create: Aeronautics 由 Sable 提供物理，Simulated 原生提供物理组装器、旋转轴承、扭力弹簧、姿态/速度/光学/激光传感器、模拟传动器、齿轮、导轨。其源码已检查：
- Simulated-Project/simulated/common/src/main/java/dev/simulated_team/simulated/content/blocks/swivel_bearing/SwivelBearingBlockEntity.java：旋转轴承建立 RotaryConstraintHandle；通过机械动力轴速度累计 targetAngleDegrees，在物理马达上应用 stiffness/damping。并非自带智能逆向动力学控制器。
- 同目录 torsion_spring/TorsionSpringBlockEntity.java：弹簧本质是可配置角度/机械输出，不等于带力量反馈的恐龙肌肉。
- gimbal_sensor/GimbalSensorBlockEntity.java：SubLevel 姿态转红石/角度数据；速度 sensor 提供速率；optical sensor 通过真实射线检测撞击方块，不能当完整 AI 看见玩家的视觉。
- Sable PhysicsPipeline.addConstraint、Rotary/Generic/FixedConfiguration、PhysicsConstraintHandle.setMotor、GenericConstraintHandle.setLimit 和 RigidBodyHandle.applyImpulse 提供实际物理接口。主分支源码与指定 2.0.6 JAR 还需二进制匹配 GameTest。

其他可选现成组件：
- Create Aeronautics: Gadgets & Gizmos（1.21.1）：Servo Bearing、Vector Bearing、Aileron Bearing（Precise 精细独立角度、红石或 ComputerCraft 控制）、Advanced Data Link、Controller。该附属当前有 2.0 预发布版本，需单独验证稳定性；不作为核心依赖前假定已经兼容。
- Create Aeronautics: Transmission & Linkage（1.21.1）：万向节、铰链、液压杆、气压调节，提供伸缩、阻尼、红石目标行程。用于腿的线性肌腱可优先试。
- Create: Avionics + CC: Tweaked（1.21.1）：读各种航空学传感器并通过脚本控制模拟传动器等；适合做单腿/二足原型调参，不直接把任意 Lua 视为 20 TPS 可稳定的实时大 Boss AI。
- 所有第三方 add-on 都需要确认依赖 Create 6.x / Aeronautics 1.3.2 / Sable 2.0.6 的二进制兼容；不直接复制第三方代码或资产。

## 怎么在游戏中拼：先一条真实可站立的腿，再四肢

局部坐标规定：恐龙头朝 +Z，尾朝 -Z，左为 -X，右为 +X，脚下为 -Y。
原型：躯干 11 × 5 × 4 方块，骨架高度 6 格，四足每足上臂 3 格 + 下臂 3 格 + 2×2 脚爪。初期规模 200–500 有效碰撞方块。最终一只 20+ 格长 Boss 的尺寸以后放大。

关键：结构按各段分别涂胶，**不可在相邻两个关节之间连续涂蜂蜜胶**，否则 Sable 把两部分合并为一个刚体，关节根本动不了。每个关节一边是底座块，一边是另一个可组装的物理子结构。必须验证 Sable 加载/拆分时 UUID 和关节的重新绑定，不能把一只巨兽的两个部件错误地组装进同一 SubLevel。

建议实体段：
- body 1（胸腹+背脊+被甲覆盖的控制中枢）。
- neck/head 2（第一阶段颈部一段、头部一段，连接真实物理旋转关节）。
- tail 2（尾根+尾梢，两段伺服让扫尾是物理运动，尾梢可拆）。
- 每条腿 3（上腿 / 小腿 / 2×2 脚掌）；四条腿 = 12；在躯干和髋的根部伺服关节、膝关节、踝关节分别用 1 DOF ServoBearing，髋转向可能需要第二个正交轴承或 2 DOF VectorBearing。
- 全套共 17 物理段，约 16 以上关节；这**不是第一轮原型要同时启用**。第一轮只启用 body + 一条双节腿 + 脚板（4 段、3 关节），实际验证单足支撑负载、连杆受力和碰撞，之后才装第二条、第四条。
- 机械动力输出：动力源（便携引擎或自研肌肉动力核心）→ 动力分配轴/齿轮→ 模拟传动器/智能伺服输入→ 髋/膝/踝关节。保证力矩和最大速度受限，动力损坏必须导致动作能力下降，而不是仍然给无限电机力矩。
- 腿末端安装耐碰撞鞋底/摩擦脚垫，四角各预留地面接触/脚底载荷传感器。原版传感器能探距离与姿态，但并不完整提供脚掌实际接触冲量与支撑面的法线；此功能要做新方块。
- 背脊装岩甲、骨突真实碰撞方块与可破坏红色器官方块。PEAK 的右键手脚抓握必须直接命中这些块的 VoxelShape，在 Boss 转弯时沿其局部坐标稳定。

## 站稳与行走：必须是反馈驱动，不能循环播放腿动画

每游戏 tick / 物理 substep：
1. 读取 body pose、线速度、角速度，髋膝踝真实关节角、角速度、脚掌实际地面接触冲量与足底摩擦状态。
2. 用触地脚掌的物理接触点构成支撑多边形，评估 COM 质心投影与支撑边缘的安全余量。若质心将越界，先减速、调髋/膝补偿或抬另一足回到可站稳的接触位置；支撑不足则确实跌倒，不触发隐藏漂浮。
3. 使用步态状态机和站立足/摆动足分离：低速采用单足逐个抬起的慢速四足波动步态；腿抬起后沿曲线经过前方安全落点；触地后再承重，下一条腿才离地。复杂坡面下踩到实际物理接触点，而不是四足假踩同一个 Y 高度。
4. 局部运动学算每只脚的目标位置，再通过 2–3 节 IK 求各关节目标角；PD 力矩控制 servo 电机到目标角（含最大角速度、扭矩、阻尼、饱和）；任何时刻控制器只能施力，不能硬改 SubLevel 世界变换。
5. 尾巴和头颈联合用来平衡（陀螺仪读取姿态，控制器对尾部加有限角度反向配重），使巨兽加速、刹车、转弯时不立即栽倒。
6. 若腿关节被玩家破坏，则移除相应约束或卸掉电机可用最大力矩、重新计算支撑多边形和可用步态；单足受伤必须跛行、剩余三足支撑；两足完全失效时有理由地趴倒。Boss 本体重心会真的下落与地面碰撞。

不能把普通旋转轴承直接设为无限驱动力：在大型质量下刚体关节可能爆振、穿模或失稳。首版驱动限力、限制频率并逐段验收高负载接触稳定性。

## 视觉与攻击：真实拼装为根，美术和效果单独负责

- 视觉：头骨、背甲、关节外皮使用自定义楼梯/斜面/骨板 block（真实 VoxelShape + 真实破坏），由原版方块构筑硬结构。关节之间允许少量仅负责过渡外观的软组织渲染，不能拿它代替碰撞和可毁方块。物理关节倾斜后自然改变真实块姿态，动作由物理结果产生。
- 眼睛：现成 OpticalSensor/Laser/Navigation/Gimbal 可做近距离障碍/姿态感知；但不存在自动识别玩家/仇恨/遮挡/战术目标的通用“怪物视觉”方块。开发新 Boss Eye：服务器定向视场角 + 实体视线遮挡查询 + 距离/优先级/记忆，输出感知数据给 Brain。
- 攻击：巨兽甩尾时由有质量、有限扭矩的物理尾体实际扫到玩家，检查真实 Sable collision/contact → 速度与冲量阈值 → 伤害/击退；头部低头由关节电机完成，咬合区自研 Jaw Joint/Impact Sensor 判定碰撞；脚下踩踏从接地位置和局部冲量决定，不能只查 10×10 区域扣血。
- 受损：ShipRepair 快照模式转换成 Boss 身体完整度；默认部位 Health = 原快照有效方块权重总和中的剩余量。新增外部积木不算回血。断掉脚垫失去摩擦，坏掉腿骨电机限力，断尾禁止甩尾，破坏脊背器官中断再生；核心毁坏才死亡。
- 敌人 AI：Boss Brain 追踪玩家、寻路、冲刺、回巢、攻击选择/前摇/后摇、冷却和分阶段战斗，低频行为决策与高频真实物理姿态控制解耦，避免 NPC PathNavigation 当成巨型体的四足导航。
- HUD Boss Bar：显示结构完整度和可选部位伤害分段；只读取服务器蓝图结果，不能凭固定 hidden HP。

## 有哪些方块现成，哪些必须补

| 子系统 | 现成情况 | 建议 |
|---|---|---|
| 物理组装、真实子空间、重量/重力/碰撞 | 航空学物理组装器 + 蜂蜜/强力胶 + Sable | 使用现成；各段分别组装，关节之间禁跨段粘合 |
| 单轴髋/膝/颈/尾关节 | 航空学旋转轴承（有实际 rotary 约束和马达） | 现成做单腿原型；精度不足时优先试 Gadgets 的 Servo Bearing |
| 多方向髋、液压肌腱 | Gadgets Vector/Aileron Bearings + Transmission & Linkage Hydraulic Rods / Universal Joints | 属于可选实验依赖，验证版本稳定性与可承载重量 |
| 动力、轴承和扭矩 | Create 动力、模拟传动器、齿轮、Portable Engine | 现成供能链，但缺 Boss 每关节实时力矩限幅/衰竭控制，补 Muscle Controller 方块 |
| 姿态和速度 | GimbalSensor、VelocitySensor、NavigationTable | 现成，可读刚体姿态/速度；控制器直接从 Sable 也可读取 |
| 地形探测 | OpticalSensor 激光和射线 | 现成距离探测，脚板需要真实 contact/normal/impulse，补 Foot Contact Pad |
| 眼睛/找玩家 | 激光/光学传感器仅覆盖部分检测场景 | 新增 Boss Eye（目标识别+FOV+遮挡+仇恨） |
| 站立、重心、步态、IK、全身协调 | 航空学没有内置恐龙四足步行大脑 | **必须新开发 Brain + Posture/Balance Controller + Gait Engine** |
| 真实命中和攻击 | 原版实体 damage + Sable collision 能提供基础 | 新增 Impact Sensor、Jaw/Claw Damage emitter、伤害速度阈值/单次去重 |
| 物理身体血条、再生 | ShipRepair #24 有结构快照和差异原型 | 开发独立 BossBlueprint/PartRegistry/Regeneration，持久化 SavedData |
| 骨甲、皮肤、爪、关节柔性外观 | 原版方块+仿形方块可做硬壳 | 新增有真实碰撞的曲面/斜面 BonePlate/SkinPlate 模型，柔性缝隙只渲染 |
| 玩家攀爬在多段肢体上 | PEAK v0.11 已能绑定单 Sable UUID 抓点 | 新增跨段安全抓握、相对速度判断、甩尾冲量与破坏抓点后释放 |
| 完整四足动力学 | Sable 关节可提供底层 | **需要实物 GameTest**；现有这些 Mod 不能直接保证大 Boss 稳定自主四足走路 |

## 专用新 Mod 计划：Physical Boss Components

做一个独立 Mod，而非修改 Create: Aeronautics 资产：
- Boss Core / Brain Block：整只物理 Boss ID、多段 UUID、AI 行为、世界存储、死亡状态、仅服务端决策。
- Muscle Actuator/Joint Servo Controller：对选定轴承或 Sable joint 设置受最大动力、最大角速度和质量约束的目标角/PD 力矩；没有动力就失去输出；暴露状态/负载。
- Foot Contact Pad：真实碰撞压力与接触法线、摩擦、脚掌是否承重和是否打滑。
- Vestibular/Balance Unit：结合 Sable 真实质心、重心与支持面，向 Brain 提供站立余量与倾覆危险，不额外添加悬浮力。
- Boss Eye Sensor：玩家 FOV + 射线遮挡/距离 + 受损失明逻辑。
- Impact Sensor + Jaw/Claw Nodes：真实质量、相对速度、接触点与伤害去重，限制攻击范围。
- Structural Nerve / Breakable Organ：身体部位身份、岩甲→器官的可毁结构与能力退化。
- BonePlate / SkinPlate / Tendon Joint Cover：真实方块 VoxelShape，不只做虚假大模型；关节装饰接缝不参与虚构碰撞。

Brain 是一套系统，不必表现为几十个重复方块。为了玩家能拆坏关键功能，核心、神经连接、驱动器和器官应当在游戏世界中真实存在且可破坏；代码只负责每个组件的控制算法与状态管理。

## 第一轮真正要拼的两个试验台

先在隔离世界搭建「单腿 3 关节承重架」：一个躯干块组、一段上腿、一段小腿、一段脚板，三处由旋转伺服连接；供电后先站稳 30 秒，进行一步前摆、实际脚板落地、在不同地形踩实，关闭供能后跪倒。四腿尚未同步前不宣称能四足站立。

第二个拼成「四足原型兽」：四套经过验证的腿，重心和接地传感器，算法先每次只抬起一足。没有隐藏重力悬浮、没有推进器伪装走路。用原版方块做身体结构和露在背部的弱点，玩家真实登背、打碎腿关节后身体跛行，打碎核心后真正倒地。

### 可验证的验收

- G0：以航空学 1.3.2 + Sable 2.0.6 +选定两个关节附属版本启动同一 NeoForge 1.21.1 游戏，核对掉包/重复类和 mixin 冲突。
- G1：原生轴承组装 2 段 Sable 结构，读出不同 UUID、相对姿态、关节真实角度；堵转、过载时不转，不以 setPose 偷改。
- G2：单腿 30 秒稳定支持负载，抬腿一次触地，断动力真实屈曲。
- G3：四足在平地行走 20 格并可停住，起步/停步不滑行乱转；再在台阶/坡地测试，记录 Physics MSPT 和碰撞异常。
- G4：玩家连续攀爬运动中各真实 SubLevel 表面并安全跨关节，砸坏抓点释放动画恢复正常。
- G5：碰撞踩踏/扫尾确实按局部碰撞生效，拆断关节禁用技能，Boss bar 结构百分比来自世界真实破损，外加的块不刷血。
- G6：多人/玩家退出/世界存档重载，关节和 Brain 安全重绑，不能泄露持久 physics handles 或在普通世界误生成 Boss 部分。

性能必须独立记录。目标是 20TPS 且物理 substeps 正常；不能以编译成功代替真实运动验收。逐段扩容到 17 个 SubLevel 前，必须有真实性能曲线。

## 源码参考与许可证

1. https://github.com/Creators-of-Aeronautics/Simulated-Project — 源码提供轴承真实 rotary constraint/motor、姿态传感器、动力系统。构件、代码和美术资产有不同许可条件，禁止复制第三方资产。
2. https://github.com/ryanhcode/sable — PhysicsPipeline/ConstraintHandle/Generic/RigidBodyHandle，游戏里的刚体底层。
3. https://modrinth.com/mod/create-aeronautics-gadgets-and-gizmos/ — 可选独立关节与控制（ARR）。
4. https://modrinth.com/mod/create-aeronautics-transmission-linkage/ — 可选液压/铰链（CC-BY-NC-4.0，须仔细审查非商业限制）。
5. https://github.com/SolAstrius/CreateAvionics — 可选 CC 可编程接口（MIT）。
6. 我们的 ShipRepair PR #24: https://github.com/g2066203208-beep/mc-studio/pull/24。
7. 我们的 PEAK v0.11 PR #22: https://github.com/g2066203208-beep/mc-studio/pull/22。

当前输出仅为**审查和构建规划**。未运行航空学与附属的实际联机拼装测试，具体是否完美支持目标版本必须再用真实 NeoForge GameTest 验证。
