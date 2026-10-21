# RPMS-ICD｜系统架构与接口控制文件 v0.1
所有接口采用版本化且服务端 authoritative 的契约。ROS2 的关节 command/state 思路仅作工程借鉴；Minecraft 工程不要求搭载 ROS 进程。

## 1. 分层模块

~~~mermaid
flowchart TB
  TASK[Operator / Boss AI / Vehicle Autonomy] --> CMD[DesiredMotion cmd]
  CMD --> PLAN[Behavior Arbiter + Locomotion Planner]
  PLAN --> G[Footstep Planner + Gait FSM]
  G --> IK[FK / IK + Body Balance]
  IK --> CTRL[Joint PD / Compliance / Saturation]
  CTRL --> SAFE[Safety Supervisor + Power Budget]
  SAFE --> ACT[JointActuator Port]
  ACT --> AERO[Create Aeronautics / Servo or Sable Adapter]
  AERO --> PHY[Sable RigidBodies + Constraints + Contacts]
  PHY --> EST[Sensor Adapter / State Estimator]
  EST --> G
  EST --> IK
  EST --> SAFE
  HEALTH[Structure Snapshot + Damage Registry] --> SAFE
  HEALTH --> PLAN
  POWER[Create Power / Mechanical Supply] --> SAFE
  PHY --> HEALTH
  SAFE --> OBS[Telemetry / Debug / Run Report]
  EST --> OBS
  PLAN --> OBS
  STORE[Machine Registry / SavedData] --> PLAN
  STORE --> HEALTH
~~~

依赖必须单向：BOSS AI 只发 DesiredMotion，不能直接调用 Sable.setPosition 或物理 pipeline。JointAdapter 与 SensorAdapter 不向上暴露特定第三方 Mod Java 类，便于更换 Aeronautics 旋转轴承为未来 Muscle Servo。

## 2. 统一标识与坐标系

- machineId：独立 UUID；partId：语义枚举 torso, LF_upper, LF_lower, LF_foot 等；segmentId：Sable UUID（运行时可失联）；jointId：语义路径 LF.hip_pitch 等。
- Frames：WORLD（游戏全局坐标）；SEGMENT_LOCAL（每 Sable SubLevel 自己的 plot 变换）；BODY（身体 COM 附近校准 frame）；JOINT_PARENT / JOINT_CHILD（关节轴及零位）；FOOT（脚垫中心与接触法线）。
- 局部约定：+Y 向上；身体前进 +Z，+X 右、-X 左。每个 joint axis 使用单位向量在 JOINT_PARENT 下定义，任何反转需显式 jointSign=+1/-1，禁止靠查看图片猜左右。
- worldPos = segmentPose ∘ localPos；worldNormal = orientationTransform(localNormal) 后归一化。与 PEAK 跨 Sable 抓点共用基本变换定义。
- 源码所有角度内部用 **rad**、角速度 rad/tick 或 rad/s 必须统一声明；对外 Aeronautics UI 若用 degree 在适配器处转换。世界距离 blocks，物理时间 seconds；MC 1 tick=0.05 seconds 仅在服务器维持 20 TPS 时成立。step dt 必须来源于实际固定逻辑步长，不能用渲染 FPS。
- 力/扭矩必须以 Sable 适配器**引擎单位**表达；未经校准前不标为 SI N 或 N·m。质量/惯量同理记录物理引擎输出与试验标定来源。

## 3. 关键 API 契约（接口设计而非已存在类）

~~~text
MachineAssembly:
  assemblyId, machineId, rootSegmentUuid, segments[], joints[], materialMapHash
  validateConnectedGraph(); validateUniqueSublevels(); validateLimits(); commit()

JointFeedback:
  machineId, jointId, parentSegmentUuid, childSegmentUuid
  sequenceTick, angleRad, angularVelocityRadPerSecond, effortEngineUnits?
  validity{fresh, calibrated, source, ageTicks, physicallyMeasured}
  motorState{passive, enabled, saturated, fault}, jointConnected

JointCommand:
  machineId, jointId, sequenceTick, expiresAtTick
  mode{DISABLED, PASSIVE, POSITION, VELOCITY, EFFORT}
  targetRad?, targetVelocityRadPerSecond?, effortLimitEngineUnits
  velocityLimitRadPerSecond, accelLimitRadPerSecond2
  kp?, kd?, expectedSublevelUuids, authorization

BodyState:
  rootSegmentUuid, worldPose, worldLinearVelocity, worldAngularVelocity
  bodyCOMLocal, bodyCOMWorld, massEstimate, inertiaEstimate
  supportContacts[], supportMarginBlocks, rollPitchYaw, validity

FootContact:
  legId, footSegmentUuid, contactWorldPosition, normalWorld
  touching, relativeSlipSpeed, normalImpulseEngineUnits?
  contactQuality{measured, derived, estimated, invalid}

DesiredMotion:
  machineId, actorUuid, sequenceTick, expiry
  mode{STAND, WALK, TURN, REST, EMERGENCY_STOP}
  bodyVelocityForwardBlocksPerSecond, bodyVelocitySideBlocksPerSecond
  desiredYawRateRadPerSecond, desiredBodyHeightBlocks
~~~

问号参数意味可能因底层不支持而不可用；必须在 API probe 中标明 estimated/invalid，不能用上次命令假装实测 effort。

## 4. 执行与反馈时序

| 层 | 计划节拍 | 输入 → 输出 | 约束 |
|---|---|---|---|
| Sable Physics | Sable 实际固定子步（待探查） | actuation → 物理位姿与接触 | 物理线程/求解器边界明确，禁止跨线程写世界 |
| Servo & Safety | 每 server tick 20 Hz 最低保证（可用安全子步回调需另验） | joint state → motor target | 不依据客户端帧率，不越过版本兼容 |
| Body estimator | 每 1 tick | pose/contact/encoders → BodyState | 失效明确输出 invalid |
| Gait & Balance | 每 1–2 tick（测量后确定） | BodyState + desired → foot targets | 不在低 FPS 跳过安全门槛 |
| Behavior AI | 每 4–10 tick（配置） | perception → DesiredMotion | 高层不可以写关节 |
| HUD/network | 2–5 tick／仅差异 | state summaries → client | 不每 tick 全船数据同步 |
| Blueprint audit | 增量更新 + 分批巡检 | block changes → health | 防全身 1k 方块逐 tick 全扫 |

当 TPS 低于阈值：使用固定逻辑 dt、限制 actuator rate、超时 watchdog 进入安全模式。绝不可“为了跟上真实时间”一次输出巨大力矩。

## 5. 运动组件适配器矩阵

| 接口 | Aeronautics/Sable 当前见到的基础 | 验证状态 | 替代方案 |
|---|---|---|---|
| ISegmentStore | ServerSubLevel UUID / plot positions / mass pose | ShipRepair 小船 2/2 test 但大机器未测 | 不能擅自退化为全局 world chunks |
| IJointMotor | Aeronautics SwivelBearing 的 rotary constraint + Create 动力 | 读源码，未测承重马达 | 自研 MuscleActuator 与有限力矩 Sable joint |
| IJointEncoder | bearing targetAngleDegrees, Sable 角度可由两段 pose 推导 | 需校准轴正向/零位 | 自研 JointEncoder Sensor |
| IFootContact | 光学射线可估距离，Gimbal 与速度可读身体状态 | 真正触地载荷不等于光线命中 | 新增 FootContactPad，或经 Sable contact API 探查 |
| IPowerBudget | Create speed/stress | 能量与实际 Sable 力矩关系未测 | EnginePowerAdapter 额定扭矩标定 |
| IStructureIntegrity | ShipRepair 快照和差异 | 原型已验证，Boss 移植未验 | 共享 Blueprint 语义，BOSS 自己 immutable 基线 |

## 6. 生命周期状态与容错

Build → Validate → Assemble → Calibrate → Standby → Stand → Walk / Turn → Rest → Shutdown。
任意运行态可进入 Fault / E_STOP / Falling / Recovery。
- 组装失败：不得创建部分残余约束。
- 保存/重载：本体 identity 重新解析后**必须先 Calibrate/Validate**，不能直接恢复上一存档的油门/满力矩。
- 物理 Segment UUID 被 Sable split/disassemble：先停失联关节并注销 handle，状态转换到 Fault；不能用原来坐标重新创建新无主刚体。
- Motor stale command / feedback fault：无条件由 SafetySupervisor 覆盖高层指令。
- Player-supplied packets：只传允许的 DesiredMotion，必须验证距离、权限、速率与 machineId，服务端执行真实接触与损坏判断。

## 7. 设计分工的稳定扩展点

RPMS core 不引用 PEAK、Boss、ShipRepair 的具体主类。扩展桥：
- BossMovementAdapter 发 DesiredMotion + 读取 PartIntegrity。
- PEAK ClimbableSegment adapter：UUID/局部接触/角速度；关节跨段安全握持。
- ShipBlueprintAdapter：不可变基准数据 + 增量损坏回调，不开放给玩家覆写 Boss 基准。
- ManualControlAdapter：玩家控制器遥控机器。
这些桥均可以分别关闭；缺失任一游戏扩展不影响机械腿测试台启动。
