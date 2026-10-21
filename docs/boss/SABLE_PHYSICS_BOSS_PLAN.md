# RPM Boss｜真正的 Sable 物理机械 Boss：阶段设计与原版方块可行性

> 状态：**设计/源码论证，尚未完成任何可行走 Boss 的原版游戏验证**。
> 项目独立于 WorldSeam 无缝维度、NPC；基于 MC Java 1.21.1 / NeoForge、Create 6.0.10、Create Aeronautics 1.3.2（自带 Simulated / Offroad）、Sable 2.0.6。网站 `viewer/rpms/` 是可视化编辑/诊断端；**所有刚体受力和关节转动必须由 Minecraft 服务端的 Sable/Rapier 实际计算**。

## 1. 明确 Boss 玩法和造型

**目标产品：六足掘地机械巨兽（暂名 Iron Burrower）。**
主体采用 Minecraft 方块和 Create 铜、黄铜、机械轴承、齿轮箱、安山合金、机械链传动语汇，低重心、棱角明确，不使用光滑科幻人形贴模。六条机械腿以三足交替支撑步态爬行、匀速行走和转向；前方后续添加两条独立攻击机械臂（抓、砸、钻），**攻击臂不充当承重腿**。Boss 有可击毁的腿部/动力/装甲组件：损伤改变动力、支撑和步态，而不仅改变血条。

**实际开发顺序：双关节机械臂台架 → 一条腿落地承重 → 四足低速爬行 → 六足三脚架步态 → 转弯/越障 → 机械攻击臂与 Boss AI。**
选择先四足而不是直接六足，是为了尽早发现关节角度、SubLevel 持久化、脚底摩擦及实际碰撞问题；最终优先六足而不是双足，因为三足交替支撑更容易提供稳定支撑面。早期阶段**不使用隐藏传送、不冻结刚体冒充站立、不以 Three.js 动画冒充游戏物理**。

### 最小六足结构（不含武器）
- 一具中央物理刚体，六根上腿、六根下腿；理想结构为 **13 个 Sable 刚体、12 个真正旋转约束**，每腿髋俯仰 + 膝俯仰 2 DOF。脚垫暂属下腿端，保持最少刚体数。
- 六腿三脚架：A=左前+右中+左后；B=右前+左中+右后；接触、抬脚、摆动、落脚阶段交替。速度、地形高度和载荷决定步频。平坦步态通过后，再考虑各腿增加髋偏航（每腿可增至 3 DOF，总体最高 18 个关节）、绕障和转向。
- 先以一块测试地板、低矮长方体躯干、细长上下腿和大脚垫完成真实物理，绝不一开始堆几百个装饰方块扰乱重心。
- 能源优先选原版 `simulated:red_portable_engine` 或 Create 发动源，齿轮箱/轴/齿轮/红石控制；创造模式马达仅用于**台架调试**，不作为最终 Boss 的隐藏无限能源。

## 2. 现有 Mod 是否足够：源码核对结果

| 任务 | 先用现有方块/API | 已确认具备什么 | 对 Boss 还缺什么 |
|---|---|---|---|
| 单一躯干刚体 | `simulated:physics_assembler` + 胶合、Sable SubLevel | `PhysicsAssemblerBlockEntity.assembleOrDisassemble()` 把连接块装配成 SubLevel；Sable physics pipeline 有碰撞与重力 | 真实装配测试、整体质量/重心、卸载重进世界 |
| 髋/膝物理关节 | `simulated:swivel_bearing` / 随动板 | `SwivelBearingBlockEntity.attachConstraints()` 调用 Sable `RotaryConstraintConfiguration`；其 `updateServoCoefficients()` 调真实伺服参数 | 绝对角度命令、行程限制、负载力矩、旋转超调、跨 SubLevel 可靠性 |
| 传动与动作 | Create 轴/齿轮/链条/离合器/红石；Simulated `directional_gearshift`、`torsion_spring`、`analog_transmission` | 动力 RPM 通过 Create 网络传播，旋转轴承角目标可由轴 RPM 逐 Tick 推进，扭簧/方向变速可形成非无限连续转动 | 高度可复现的多关节指定角度与反馈；移动时的机械网络延续 |
| 驱动与燃料 | `simulated:red_portable_engine`、Create 机械源和 SU | 发动源在移动结构中能提供 Create 网络动力的路径值得验证 | **绝不假定**所有能源能跨转动关节传递；逐级测量 SU 和耗能 |
| 指令通信 | Create 无线红石、`simulated:modulating_linked_receiver`、`analog_transmission` | 现有 1.21.1 机械爬行器案例使用 24 个调制无线接收器控制腿部 | 适合自主 Boss 的多通道目标角度/状态同步协议 |
| 姿态与速度 | `simulated:gimbal_sensor`、`velocity_sensor`、`altitude_sensor`、激光/光学传感器 | 可以得到姿态、速度/高度等输入，连接红石或数值控制 | 单脚真实接触法线、接触力及支撑质量评估仍需 GameTest |
| 地面接触与抓地 | Sable 方块碰撞、摩擦、质量系统 | 物理管线可产生实际接触、碰撞、惯性 | 光滑面打滑、台阶边缘悬空、多足自碰撞和关节负载是否稳定未测 |
| 坦克/轮式替代 | Offroad `wheel_mount` | 可实现车轮悬挂/摩擦/动力，利于后续 Boss 履带形态 | **不能**把轮子暗藏脚里然后声称为真实步行 |
| 流体/风/升力 | Aeronautics 桨叶/浮力 + Sable | 适合以后 Boss 推进、跳跃辅助和空中阶段 | 目前没有必要作为第一阶段依赖；风场是否来自附加 Mod 必须实际检测 |

证据：Create `RotationPropagator` 与 `KineticNetwork`、Simulated `PhysicsAssemblerBlockEntity`、`SwivelBearingBlockEntity`、`GimbalSensorBlockEntity`、`SimBlocks`，Sable `RotaryConstraintConfiguration`、`PhysicsPipeline`、`RigidBodyHandle`。不要复制受限制的 Mod 模型/PNG 至网站；原版美术仍由本地安装或用户浏览器向原始发布源加载。

**现成作品的证据和限制**：CreateMod.com “Aeronautics Crawler” 是 MC 1.21.x / Create 6.0.10 的 268 方块四足爬行器参考，清单包含 17 个旋转轴承、24 个调制无线接收器、弹簧、方向变速箱、链传动与便携发动机。其作者说明要解锁轴承、腿偶尔错位需要重置。它是**人工操控的社区方案**，不是已验证的自主六足 AI Boss；应复现其机械原理并找到不稳定根因，**不能直接声称可用**。
- 参考：https://createmod.com/schematics/aeronautics-crawler
- Simulated 源码：https://github.com/Creators-of-Aeronautics/Simulated-Project/tree/main/simulated/common/src/main/java/dev/simulated_team/simulated/content/blocks
- Sable 约束：https://github.com/ryanhcode/sable/blob/main/common/src/main/java/dev/ryanhcode/sable/api/physics/constraint/RotaryConstraintHandle.java
- Create 源码：https://github.com/Creators-of-Create/Create/blob/mc1.21.1/dev/src/main/java/com/simibubi/create/content/kinetics/RotationPropagator.java
- 可选第三方“Transmission & Linkage”提供可调液压杆、万向节和伸缩关节（NeoForge 1.21.1），可列入实验组，但**要先真实验证版本兼容性与发布许可，不把它作为核心硬依赖**：https://www.curseforge.com/minecraft/mc-mods/create-aeronautics-transmission-linkage

## 3. 原版不足时应添加的最小新方块（先测，后开发）

开发顺序优先使用现有方块，**不提前重复造 Sable 物理世界**。建议单独创建 NeoForge 模组包 `boss/` 或 `mods/mechanical-boss/`，自己 ID 命名空间 `reme_boss:`，不能混进 WorldSeam/NPC 项目，也不能修改 Create/Simulated 上游 JAR。

1. **`reme_boss:boss_core`／机械指挥核心**（预计必需）：控制自主状态机、目标、动作命令、成员关节登记、损伤模型、存档/区块加载、服务端主导的网络同步。它不负责瞬移物理体。没有它，原版方块只支持玩家/红石控制，并没有“敌对 Boss 的大脑”。
2. **`reme_boss:joint_servo`／目标角度伺服器**（条件开发）：**只有在**原版 Swivel Bearing + 扭簧/方向变速箱/调制接收器无法稳定复现指定角度时再实现；以 Sable 真正的 `RotaryConstraintHandle.setMotor(DEFAULT_AXIS, targetAngleRadians, stiffness, damping,...)` 或已安装的约束接口为底层；读取实际姿态反馈，暴露角度/速度/力矩上限、阻尼、刹车。禁止每 Tick 直接修改 SubLevel 坐标或伪造力矩。
3. **`reme_boss:foot_sensor`／承重脚垫**（条件开发）：现有姿态/激光传感器不足以区分脚尖悬空、接触、打滑或冲击时，监听物理碰撞/接触 API，汇报接触标志、法向、相对速度、估计承载；不得给脚垫凭空添加向前力。
4. **`reme_boss:power_coupler`／旋转关节动力耦合器**（最后手段）：真实测试证实移动结构里的动力/SU 跨子关节传递不稳定、原版连接+Transmission & Linkage 仍解决不了时才开发，必须从上游 Create 源头和消费者记账，不能产生无限 SU。
5. **`reme_boss:weapon_mount`／机械武器底座**（后续）：只管理真实攻击关节、伤害判定、动作冷却，不制造额外隐藏旋转物理。

不增加“万能机械腿方块”，腿必须能由 MC 原有方块逐段搭建、更换长度、齿轮比、重量和护甲。新方块解决的是**游戏原版确实无法很好表达的控制、感知与系统接口**。

## 4. 研发关卡（每关失败就修复，不假进度）

| 关卡 | 要做出的真实可见效果 | 必须由原版服务器与客户端验证的证据 | 新方块触发条件 |
|---|---|---|---|
| P0 原版爬行器复现 | 在 MC 1.21.1 真客户端搭出参考爬行器并操作每条腿 | 截图/录屏、轴承装配和 SubLevel 数量、真实转速、碰撞/保存载入，比较卡顿与腿错位 | 无 |
| P1 双关节机械臂 | 固定台架上两段可控刚体：抬起、弯曲、保持、复位 | 两旋转约束实测角度、目标误差、关节力/实际 RPM，重复 100 次无丢失 | P1 无法稳定保角→关节伺服器 |
| P2 单腿承重 | 真脚垫触地承受可测载荷，关节升降推动真实物理躯干 | 有重力、真实质量与碰撞；站住、抬脚、打滑、卸载重载之后数据一致 | 感知不足→脚垫传感器 |
| P3 四足爬行测试体 | 真正靠依次抬脚移动、身体有相应的物理受力，无“隐形车轮” | 前进、后退、转向、单层台阶；测量位移/偏航/翻倒/穿地 | 精准角度/动力链失败才补方块 |
| P4 六足完整移动平台 | 六腿交替三足支撑，从起步、停止到绕障，自主恢复平衡 | 目标点寻路与碰撞、地形坡度、腿损坏后跛行、跨区块、玩家搭乘/多人同步 | Boss Core + 可证明必要的关节 |
| P5 Boss 敌对战斗 | 扫描玩家、接近、追击、攻击臂抓砸、躯干部件伤害和死亡掉落 | 真实服务器判定和客户端动画/声音；断腿改变受力不是纯效果 | Weapon Mount（若原版机构不足） |
| P6 完整性/性能 | 多体长期运行无泄漏、无穿模、AI 与物理同步可读 | 自动 GameTest/真客户端录屏、反复重载，TPS/CPU/内存/异常定量监测 | 针对瓶颈优化，不靠隐藏删物理 |

**初步验收目标而非既有成绩**：P1 各角度命令反复 100 次且无空约束；P3 地面移动至少 30 格后能停住并回位；P4 连续 3 分钟自主移动能通过 1 格高障碍并重新稳定姿态；性能目标在指定测试机器上 1 只活动 Boss 时服务端 TPS≥18 且无持续内存增长。以上只是目标，所有测试需固定世界种子、模组版本和计算机规格，测试未通过不得写“实现完成”。

## 5. 动作控制结构：把动画和物理分开

- **高层 AI（每若干 Tick）**：巡逻/索敌/回避障碍/攻击/撤退/跛行；规划身体目标速度/朝向，不直接设置刚体坐标。
- **步态规划**：每条腿有 stance（支撑）、lift（抬腿）、swing（摆腿）、plant（落脚）四阶段，结合目标速度和地形射线选取脚点，六足交替保持三点支撑、倒地时改成紧急支撑模式。
- **逆运动学（IK）**：按腿部物理长度、目标足点求髋膝目标角，关节上下限、速度/角加速度、真实惯性必须考虑；超出可达范围则让本体减速而不是瞬移腿。
- **关节执行器**：现成 Swivel Bearing/Torsion Spring 可用时把目标换成原版可操作机械指令；精确度不足才用新 Servo Block 调 Sable 物理约束。每个关节采样目标角、实测角、实际 RPM、Sable 刚体角速度/负载。
- **地面接触/平衡**：测量脚是否真触地、支撑面的质心投影、躯干横滚/俯仰和脚底滑移；反馈修正 gait phase 与关节目标。不使用直接施加“向目标前进”的魔法力。
- **受伤/断肢**：按 MC 方块或部件受损从约束图中真实移除对应刚体/关节，控制器重新计算剩余支撑腿；不能行走则倒地、挣扎或启动武器阶段。

## 6. GitHub 工程分工

- **真正的游戏模组**：专用 NeoForge `reme_boss` 模组源码、子物理体组装、Boss Core、关节驱动和服务器同步；从现有 `mod/` 的 NeoForge 构建实践起步，但不给 WorldSeam/NPC 的工程混入类。
- **测试**：单关节和约束角度真实 GameTests、脚底接触/重心、连杆断开、动力 SU、逐步移动、存档重载、玩家/怪物碰撞，多客户端的实际视频/截图。使用 1.21.1 + Create/Aero/Sable 固定 JAR；旧有 10/10 GameTest **并未覆盖**任何实际 Boss 行走，不得借用为行走已完成的证据。
- **网站**：`viewer/rpms/` 建造/导入/查看原版模型、输出 Boss 分段装配蓝图、展示来自原版服务器的约束姿态/脚部接触/CPU-TPS；不在 Three.js 中偷偷让网页物理变成验收依据。
- **可交付**：NeoForge Boss Mod JAR、独立调试世界/可粘贴结构（合法自制内容）、游戏内运行截图/录屏、GameTest 日志、缺失功能报告，提交 PR 并由 CI 验证后合并主分支。

## 7. 首次实作任务，不继续空规划

下一 PR 应优先做 **P0 + P1**：搭建可自动执行的“物理双关节台架”真实 GameTest，尝试在原版服务器造出 1 个固定底座 + 2 个 Sable SubLevels + 2 个 RotaryConstraints，并读取各约束真实角度；随后用真实 Create 传动/Simulated 轴承控制 0→45°→−30°→0°，由游戏内截图显示结构确实摆动。记录装配失败、碰撞、自穿透、过冲、过载和保存载入问题。**没达标就修底层，不接着堆四条/六条腿。**
