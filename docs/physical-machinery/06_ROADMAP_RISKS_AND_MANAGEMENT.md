# RPMS-PM｜开发阶段、责任、风险台账与项目治理 v0.1
项目主管负责全过程需求、设计、编码安排、代码评审、GameTest、性能测量和 GitHub 交付；用户拥有玩法和最终验收权。不将“设计完成”写成“游戏功能完成”。

## 1. 工程迭代与分工（按依赖，不虚构日期）

| 里程碑/工作包 | 输出的具体工作物 | 前置条件 | Definition of Done |
|---|---|---|---|
| M0 系统工程 | 章程、需求编号、ICD、机械树、零件清单、风险、V&V、基础检查器 | 需求初步明确 | 文档/机器图自检通过，PR 审阅，SRR 待用户确认 |
| M1 关节适配试验 | 独立 Mod 工程，原生 Aeronautics bearing 与 Sable constraint 的实测 adapter | M0 + 所有组件版本兼容 | 真 2 段/1关节能正反转、堵转、限幅、失电，G1 通过 |
| M2 单腿工程样机 | 1 ROOT + 3 Segment/3 motor，标定工具、脚接触测试台、负载测试 | M1 | 可见试验支架上做 100 周期运动，G2 |
| M3 状态估计与平衡 | FootContactPad/估计层、COM calculator、4脚拓扑、stand controller | M2 | 4足卸掉试验台真实站稳 30 秒，G3 |
| M4 连续步行 | FK/IK、WaveGait、落脚规划、摆动曲线、接触确认、转向 | M3 | 20格前进、停住、平地转弯、20TPS 基线，G4 |
| M5 故障与维护 | 断腿/断电/重载/UUID 失联/结构损害、日志与 telemetry | M4 | E-stop/失控回归、3足退化或真实倒地，G5 |
| M6 Boss 应用 | Boss Brain/器官/破甲/碰撞攻击/PEAK跨肢体/条形结构健康 | M5 | 玩家能够连续上身、拆甲、失衡、击杀，G6 |
| M7 生产与扩展 | 坡地复杂步态、四人联机、工程机械复用、模板系统、优化 | M6 | 压力负载/回归/体验评审通过 |

依赖路径：M0 → M1 → M2 → M3 → M4 → M5 → M6。禁止为了好看提前投入恐龙模型并跳过 M2–M4。

## 2. 管理工作流（每次任务）

1. Issue：功能/故障背景 + SYS-nnn + 风险 + 可执行验收。
2. Branch：小范围的 feat/rpms-... 或 fix/rpms-...；不直接修改已验收实验结果。
3. ADR：对物理方式、马达 API、关节数、物理引擎版本变化留决策记录。
4. PR：代码+更新参数/需求/测试/日志，明确破坏性存档变化和回滚。
5. CI：设计 lint → JUnit → NeoForge/Sable GameTest → Client → 性能；未执行者必须标 NotRun。
6. Review：按真实日志区分需求失败/模拟 fixture 失败/版本不兼容。
7. Release：标识实验版，用户测试实际 jar 或 world template；有安全问题禁止推正式存档。
8. Handoff：docs/HANDOFF.md 记录当前分支、commit、PR、测试结论、已知技术债。

## 3. 十项重点风险（初始概率/严重度为定性，不是测量）

| ID | 事件 | 可能性 | 影响 | Owner WBS | 最早缓解 |
|---|---|---|---|---|---|
| R-01 | Aero 普通轴承无法接外部实时关节角/力矩命令 | 高 | 极高 | 03,05 | M1: probe 稳定 motor API；失败则自研 MuscleServo |
| R-02 | 大质量下 Sable 物理关节抖动、穿模、约束爆炸 | 高 | 极高 | 02 | M1: 单关节负载与刚度/阻尼 sweep |
| R-03 | 20 TPS 级软件伺服反馈过慢 | 中高 | 极高 | 05 | M1/M2: 固定子步、低速波形；需合法 physics callback |
| R-04 | 获取不到真实足部接触冲量 | 高 | 高 | 04 | M2: contact API audit、FootContactPad、清楚标估计数据 |
| R-05 | 四足重心无法在稳定支持区域内转换 | 高 | 高 | 06 | M3: 几何工作空间、mass 分布与站立控制 |
| R-06 | 13 刚体+碰撞拖垮 MSPT | 中高 | 极高 | 02,10 | M1/M2 各段增加记录 step 消耗，设降级容量 |
| R-07 | 拆腿/卸载导致旧 UUID 错绑到其他飞船 | 中 | 极高 | 09 | 每 tick 校验 ownership+identity 并幂等移除 handle |
| R-08 | 多人攀爬动态肢体抖动、玩家卡进关节 | 中高 | 高 | 07 | M6 跨关节 safety handshake、相对速度门槛 |
| R-09 | 免费结构恢复无限复制资源 | 中 | 高 | 08 | 不恢复机器/容器 NBT，标识战斗掉落单次 |
| R-10 | 多个可选附属 Mod 依赖或许可不兼容 | 中高 | 高 | 01 | M1 pinned versions, licenses; 先做 core-only fallback |

任何 **G1（真正可控关节）** 无法通过，项目主管应发 NO-GO/技术风险报告，不能用假动画替代关键关节以骗过 M2/G4。

## 4. 首轮实际施工 Backlog（M1，按执行顺序）

- RPMS-101：冻结/锁定 Sable、Aeronautics、Create、NeoForge、JDK 二进制矩阵，记录依赖许可证、哈希。
- RPMS-102：搭建独立 Mods/rpms 工程与关节 API 封装，不把 Boss 代码写进物理适配器。
- RPMS-103：专用原生 Swivel Bearing + Sable 2 段 testworld builder，Sable UUID 一致性验证。
- RPMS-104：读取真实关节角/速度、质量/惯量、命令目标与机械速度。
- RPMS-105：软件角度控制可行性 POC；无法接口控制时做自研 PD MuscleServo 方块（有限扭矩）。
- RPMS-106：joint soft limits、watchdog、E-Stop、异常身份无输出测试。
- RPMS-107：物理门禁日志：过载反应、功率不足堵转、角度校准、记录指令源与 actual。
- RPMS-108：GameTest + Actions 实际二进制构建，归档 G1 Gate 证据。
G1 必须亲眼看到“机械实体转动，能承重且断电不再驱动”；任何机架约束须明示。

## 5. 企业级交付检查单（由项目负责人维护）

- [x] 需求工作包与唯一 ID
- [x] 系统架构与模块接口初稿
- [x] 机械组装树与 BOM/替代件初稿
- [x] 控制与真实物理安全策略初稿
- [x] 分阶段试验矩阵与准入门禁
- [x] 风险、负责人、M1 backlog
- [ ] 完成用户 SRR 审核与方案冻结
- [ ] 实测 Sable+Aero 关节/Servo 的 API 和承载
- [ ] 可运行真正的 2 段物理关节 testworld 和 JAR
- [ ] 真正承重/单腿运动、四足站立和步行
- [ ] Boss 爬行破甲系统与生产负载测试

“已完成”只针对规划文档与文档检查，不代表所有机械测试已经做完。
