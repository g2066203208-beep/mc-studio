# RPMS-CTRL｜运动控制、动力学、传感估计与安全设计
工程关键：真实机器不是把 12 个马达同时设为循环波形。必须有实测状态闭环、可控力/扭矩、分段故障处理。

## 1. 控制层级与有向数据流

Operator/Boss AI → DesiredMotion（期望体速、转向、站立高度）
→ locomotion state (STAND/WALK/TURN) → gait scheduler（每条腿 stance/swing）
→ footstep planner（脚端目标）+ COM balance（质心/支撑多边形）
→ IK / pose target（每关节期望角/角速）
→ compliance/PD + gravity compensation（受限力矩）
→ PowerLimiter + SafetySupervisor（最高权限）
→ JointAdapter / Aeronautics / Sable 约束马达
→ 真实刚体 / 地面反力 / 姿态 / 足部接触
→ StateEstimator 回馈所有上层闭环。

上层 AI 每 4–10 ticks 更新任务；关节和接触反馈至少每 game tick 更新。若取得物理 substep 的合法插件回调可细化低层控制频率，**不得跨线程操作 Minecraft 世界**；若实际 20 Hz 不足以稳定控制大型刚体，必须在 PDR 标记技术阻塞并设计更慢步态/原生物理回调，不可以冒称现实工业 1 kHz 控制。

## 2. 状态估计

原始数据（source标签）：
- IMU/姿态：根刚体 logical pose quaternion、角速度；GimbalSensor 可作视觉和备用输出。
- Encoder：父子 Segment Pose 在 joint axis 上投影的真实夹角，不用“命令目标角”代替实际角；连续解包以防 ±180° 抖动。
- Contact：foot pad 真实碰撞/接触法线、滑移速度/冲量。若 Sable API 未暴露真实冲量，就只把光学射线标为 ESTIMATED_NEAR_GROUND，不进入需要真实载荷反馈的 Release 验收。
- Mass：由各段实际 mass/inertia tracker 聚合；破坏方块后必须重计算或接收质量变更回调。
- Terrain：足端前方光线/体素碰撞探测，要过滤自身 Segment UUID，避免把巨兽自己另一条腿当作地面。

提供 SensorStatus = VALID/ESTIMATED/STALE/LOST；禁止不知不觉使用陈旧数据输出无限马达目标。

## 3. 基础稳定站立

- 站立时四个真实触地脚形成支撑多边形，控制躯干 pitch/roll、高度与 COM 投影。
- 在慢速准静态 G3，计算 COM 投影到地面支撑多边形的有符号最短边距 margin；若 margin <= marginMin，先转移重心、缩短步幅或返回 STAND，不能立即抬下一脚。
- 仅余 3 个稳固支撑脚时多边形变成三角形；若 2 足或地面滑动，慢速稳定步态没有足够静态支撑，应进入 RECOVER/FALL 而非开启虚构力量。
- 动态加速度/陡坡时，COM 投影只是近似，需进一步研究 ZMP/Capture Point/地面反力。不得把静态三角形测试推广为高速跑跳稳定。
- 能量来自 Create/自研动力核心。定量能力要通过实际力矩台试出：effortMaxEngineUnits 受每关节 power budget 限幅；断开动力后可以出现姿态下沉与真实翻倒。

## 4. 四足步态与运动学

第一种步态采用可停机的慢速 wave crawl：
- 初始四脚都 STANCE，先判断 COM 支撑 margin。
- 按轮换顺序选择一个无故障脚作为 SWING；移动重心后释放其支撑并控制实际抬升。
- SWING 腿按 local foot trajectory 规划抬脚高度 h、前后步幅 stepL、摆动时间；落地目标由地形探针确定。
- 足接触可信后转换 TOUCHDOWN → STANCE 并确认足在不打滑，下一条腿才可切为 SWING。若触地超时/腿关节不跟随，则暂停重新规划。
- 每只腿的 3-DOF FK/IK：HAA 控足的侧摆，HFE/KFE 在矢状面解二维肢段；使用刚体长度、髋位置和角度限制。不可达位置明确夹取/拒绝，不能无提示解出 NaN。
- 足位置从真实动态 Segment pose + joint feedback 求，不从“预期动画姿势”直接认定。
- STANCE 脚目标是相对地面尽量不动，身体向前时由腿关节运动驱动反作用力；SWING 脚产生前摆轨迹。身体不获得隐藏+Z 推力。
- 低速转向按左右足目标差动和躯干 yaw 闭环完成，不调用 vanilla mob 的瞬时旋转。
- 与 PEAK 一致允许玩家抓动态构件，但机器人控制器**不必依赖**安装 PEAK 才能走路。

## 5. 伺服控制与供能契约

每轴 actuator 支持：DISABLED/PASSIVE/POSITION/VELOCITY/EFFORT（底层不支持的模式返回 UNSUPPORTED 而不是偷偷位置传送）。

参考控制公式（单位随物理引擎校准）：
  e = shortestAngularDifference(q_target, q_actual)
  tau = clamp(Kp*e + Kd*(qd_target-qd_actual) + tau_gravity, -tauLimit, tauLimit)

- Angle/rate/accel/torque 四种上限缺一不可。刚性目标位置锁定不是通用允许的扭矩实现。
- Create shafts 的 RPM→目标角累积与我们的连续角度闭环在语义上不同，Adapter 必须解决速度/控制模式转换，否则需要自研 MuscleServo。
- 过载堵转时保留真实 e 大且 angle 跟不上的证据。电机不能无视大型躯干质量“瞬间到位”。
- 防积分饱和：首版选 PD 不含积分项；有用到 PI/PID 时必须有 anti-windup。
- 电机热/疲劳/供能失衡首版允许抽象为可测的负载预算，不假称现实热力学模拟精确；断电和器官受损真实降低可用力矩。
- 动力状态、应急停机、红石禁止、用户遥控失联都要进入统一 SafetySupervisor 的 authority 优先级。

## 6. 统一故障状态机

~~~mermaid
stateDiagram-v2
  [*] --> UNASSEMBLED
  UNASSEMBLED --> VALIDATED: build checks pass
  VALIDATED --> CALIBRATING: correct UUID graph
  CALIBRATING --> IDLE: zero/range/sensor pass
  IDLE --> STANDING: power && contact
  STANDING --> WALKING: valid motion && stable margin
  WALKING --> STANDING: stop
  STANDING --> REST: rest
  REST --> STANDING: wake and validate
  WALKING --> RECOVERING: slip/tilt exceeds thresholds
  STANDING --> RECOVERING: contact lost
  RECOVERING --> FALLEN: unrecoverable support
  RECOVERING --> STANDING: stable again
  FALLEN --> IDLE: safe rearm and recalibrate
  WALKING --> FAULT: missing segment/torque error
  STANDING --> FAULT: missing feedback
  IDLE --> E_STOP: operator E-stop
  STANDING --> E_STOP: operator E-stop
  WALKING --> E_STOP: operator E-stop
  FAULT --> E_STOP: safety escalation
  E_STOP --> CALIBRATING: deliberate manual reset
~~~

- E_STOP 不是“冻结物理物体”或 noGravity：撤销主动电机指令、按配置进入有限被动阻尼/刹车，让真实重力仍起作用。
- 控制器落空、命令过期、UUID 变更、轴读数 NaN、碰撞异常、Sable 版本不兼容都触发失效安全。
- 控制权优先级：Physical Safety > Machine Integrity > Operator > Boss AI > cosmetic effects。
- 脚部/头部/尾部进入玩家碰撞盒时，一般冲击伤害必须通过真实接触事件、运动阈值、服务端 cooldown 判定；不能后台按范围隐形扣血。

## 7. 故障分析 FMEA（初版高风险）

| Failure ID | 故障模式 | 影响 | 检测 | 缓解 |
|---|---|---|---|---|
| F-01 | 关节轴安装反向 | 抬腿变踢地/坠毁 | ±5° 正负标定 | 阻止 CALIBRATED |
| F-02 | 两段胶合成一刚体 | 轴不转/约束自连接 | UUID 相同 | 装配前失败 |
| F-03 | 轴承目标无法软件控制 | 控制器空转 | actual-angle 与目标误差常驻 | 换自研 MuscleServo |
| F-04 | 扭矩无限或过大 | 物理爆振/玩家飞起 | 力矩限幅、角速度跃变 | 饱和器、E-stop |
| F-05 | 足压测量是虚假估计 | 悬空抬脚、摔倒 | source validity 和接触检测 | 研制 FootPad/不通过 G3 |
| F-06 | 玩家拆掉承重腿 | 不平衡/模块分离 | Sable segment + block event | 停机/重规划/倒地 |
| F-07 | 拆分后旧 UUID 复用 | 别人的船被加力 | 当前 UUID+owner+graph 校验 | 立即 Fault，不重绑定任意实例 |
| F-08 | TPS 下降/命令过期 | 力矩积分积累造成爆发 | wall tick/timestamp/watchdog | 固定步长限幅/渐降 |
| F-09 | 物理分裂+存档重载 | 多个无主约束/残骸 | Save/reload 与 Handle registry | 幂等清理、归属恢复 |
| F-10 | 机械腿与玩家抓握冲突 | 玩家被卡进墙/抓点漂移 | PEAK cross-segment test | 安全换段/释放，止损 |
| F-11 | 脚被卡在自身装甲 | 物理振动/卡顿 | self-collision 标签 | 装配净空、仅必要碰撞过滤 |
| F-12 | 部件再生凭空复制装备 | 资源无限 | loot source provenance | 白名单/只恢复无 BE 结构 |

## 8. 安全与性能度量

Telemetry 每机器写入 debug trace：tick, machineId, mode, segmentUuid, jointCmd/Actual, motorSaturation, supportContactFlags, supportMargin, bodyRoll/Pitch, faultId, powerBudget, snapshotVersion, controllerMspt, physicsMspt, FPS 另列。控制帧出于性能考虑使用有限大小 ring buffer 和按需 dump，不每 tick 把全部日志写硬盘。

真实录制规则：对照无人驾驶、遥控、断电、卡脚、砸腿、卸载、重启、多人，使用隔离世界；记录整体 20TPS 是否达到、P95 服务器 MSPT、控制器 P95 和 physics step 最大尖峰。目标不可达时明确 NO-GO，不把故障路径藏在动效中。
