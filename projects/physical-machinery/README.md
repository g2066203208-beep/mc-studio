# RPMS 工程包 / Mechanical Prototype

本目录放真正的机器配置基线、关节树、零件登记与将来的单关节/单腿/四足测试台，而不是艺术效果图。参考 docs/physical-machinery/ 系统工程系列。

当前仅有候选的四足 12 DOF 设计基线：quad12.design.json；物料与组件实现状态见 docs/physical-machinery/COMPONENT_REGISTER.csv。真实的 Sable/Aeronautics Java 控制组件和可游戏内拼装的机器仍**尚未开发**。

设计结构：
- 机器 ROOT + 四条腿，每腿 HIP_CARRIER → UPPER_LEG → LOWER_FOOT 三个独立 Sable 物理段（总 13）。
- 每条腿三个主动旋转关节：HAA axis Z, HFE axis X, KFE axis X（总 12）。
- 足底被动/固定脚垫不另建独立踝关节，以防初期过多刚体造成约束风险。
- 一切扭矩/mass/物理步长是待实际 Sable 二进制物理测试后填写的参数；空值不得作为零或无穷大处理。
- 后续运行时必须将 segment symbolic ID 绑定真实 Sable UUID，并执行 build/assembly 物理身份校验。

检查命令（只验证设计结构，无 MC 动态行为）：

    python3 projects/physical-machinery/validate_design.py

生产准入：G1/G2/G3/G4 门禁明确列在 docs/physical-machinery/05_TEST_VALIDATION_AND_RELEASE.md；仅设计检查通过不能称为已实测 12 轴机械。
