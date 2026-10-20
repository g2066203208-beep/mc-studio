# REME MC Studio

这是本聊天的 Minecraft 开发工作台。目标是由助手负责需求拆解、源码研究、开发、升级、性能诊断、Git 提交和测试，用户主要负责游玩与体验反馈。

GitHub 是共享开发中心，桌面端与网页端通过同一仓库交接。新会话先读 [交接说明](docs/HANDOFF.md)。远程仓库：[g2066203208-beep/mc-studio](https://github.com/g2066203208-beep/mc-studio)。初始化 PR 已合入 main，[首次云端构建成功](https://github.com/g2066203208-beep/mc-studio/actions/runs/39402002769)，可在该运行页面的 Artifacts 下载 JAR 压缩包。

## 当前基线

2026-10-20 实机核实：

| 项目 | 状态 |
|---|---|
| 游戏 | Java 版，Minecraft 1.21.1 |
| 加载器 | NeoForge 21.1.248 |
| 发现的整合包 | TerraFirma Rebirth 2，1.21.1-1.0.0-release |
| 已发现实例 Mod | 222 个，文件大小及 SHA-256 见 local/mods-baseline.csv |
| 编译 JDK | 本机 JDK 21.0.9，64 位；另有 JDK 25 |
| Git | 2.54.0.windows.1 |
| GitHub | 插件账号 g2066203208-beep 已连通；gh CLI 未登录 |
| 远程工作室仓库 | g2066203208-beep/mc-studio（私有） |

发现的实例是暂定兼容基线，不代表用户已选定今后的唯一整合包。游戏根目录同时存在 mods、config 和 saves；正式部署前必须核实 HMCL 的实际游戏隔离设置及启动日志，不能只凭目录名确定生效路径。

## 工作台内容

- `mod/`：依据官方 NeoForge 1.21.1 MDK 建立的最小 Mod 工程，锁定当前加载器。仅打印加载标记，不新增方块或修改世界。
- `scripts/Build.ps1`：使用本机配置的 Java 21 构建并检查退出码。
- `scripts/Inventory.ps1`：重新生成已发现实例的 Mod 文件校验清单。
- `docs/WORKFLOW.md`：开发、测试、升级、回滚和 GitHub 管理流程。
- `docs/RESEARCH.md`：开源研究入口、研究方法和来源记录。
- `.github/workflows/build.yml`：已在 GitHub Actions 验证成功，推送和 PR 会触发 Java 21 构建并保存 JAR。
- `local/`：本机路径、构建日志与清单，不进入 Git。

## 构建

Linux / 网页开发环境只需 Java 21 和网络访问，在仓库根目录执行：

```bash
bash scripts/build.sh
```

不需要本机游戏安装或被忽略的 `local/` 配置。

在工作台目录执行：

```powershell
.\scripts\Build.ps1
```

产物目录为 `mod/build/libs/`。开发客户端可在 `mod/` 下使用相同 Java 环境运行 `gradlew.bat runClient`。首次构建需要下载依赖。

本工作台的“构建成功”“客户端能启动”“进入世界成功”“完整整合包兼容”和“性能改善”是不同验收项，必须分别有证据。

## 使用方式

后续直接在本聊天描述玩法、报错或想修改的 Mod。助手先读取本工作台与本机基线，尽量独立完成技术工作；只有账号授权、目标不明确或确实需要玩家体验判断时才让用户介入。

当前尚未部署到正式游戏，也尚未完成真实客户端与整合包运行测试。最新构建结果见 `docs/STATUS.md`。


## Sable Hands 双手物理攀爬（规划中）

目标：参考 White Knuckle 的左右手独立抓握，保留 Minecraft 原版第一人称方块手臂，在墙壁、平地、天花板及 Sable 动态结构的真实方块表面进行攀附移动。当前仅完成规划和开源研究，尚未实现玩法、运行客户端或做游戏内测试。

从 [玩法工程规格](docs/climbing/SPEC.md)、[开源项目及许可](docs/climbing/OPEN_SOURCE.md)、[测试矩阵](docs/climbing/TEST_PLAN.md)、[开发总提示词](docs/climbing/DEV_PROMPT.md) 开始。下一步是 M0：编写/验证真实表面探测器，然后再逐阶段实现运动功能。其他会话从 docs/HANDOFF.md 接手。


## Sable Hands 0.2.0-alpha.1 (可编译的功能原型，尚未实机验收)

[实验版功能与验收记录](docs/climbing/PROTOTYPE_STATUS.md) · [玩法规格](docs/climbing/SPEC.md) · [测试](docs/climbing/TEST_PLAN.md)

已新增 Java 代码：G 切换攀爬、左右手抓握、非空气且存在 outline 形状的任意方向方块表面、Sable 子世界坐标抓点、玩家单/双手运动约束、服务端意图校验与客户端锚点快照。本功能正在 [PR #3](https://github.com/g2066203208-beep/mc-studio/pull/3) 中开发。编译成功不代表真实游戏、Sable 运动平台、专服及整合包测试完成。正式玩家存档未部署或更改。

## Sable Hands alpha 最新构建

已提交 0.2.0-alpha.1 双手独立抓握代码和轻量原版手臂姿态，完整证据在 [Actions 39409432696](https://github.com/g2066203208-beep/mc-studio/actions/runs/39409432696)。Java/JUnit、普通 Minecraft GameTest 2/2 和带 Sable 2.0.6 的集成 GameTest 2/2 均通过。具体可安装 JAR 从该运行的 Artifacts 获取。功能仍在 [Draft PR #3](https://github.com/g2066203208-beep/mc-studio/pull/3)，动态运动船体与游戏画面未实机验收，不要安装到正式存档。


## PEAK 风格攀爬重构 0.4.0-peak.alpha.1（实验版）

**新功能 PR #5：** https://github.com/g2066203208-beep/mc-studio/pull/5

用户否定了旧 G 切换模式、左右手点击锁定、未伸向墙壁的动画。新方案参照 PEAK：空主手靠近墙壁 **按住左键抓握，WASD 攀爬，Shift 冲跃，松开左键下落**；右键保留原版物品操作；状态包含体力条、自动更新抓点、原版第一人称双臂与第三人称 Steve/Alex 双臂动画、附近玩家网络状态同步。仍保留 Sable Companion 动态子世界探针，不替换玩家皮肤或使用 PEAK 资产。

[玩法与开源参考、验收清单](docs/climbing/PEAK_RESEARCH_AND_ACCEPTANCE.md)。

已确认 `0.4.0-peak.alpha.1` 第一版源代码（git e90e02d... 后加清理与 HUD）通过 Java/JUnit/标准 Minecraft GameTest 3/3/真实 Sable 2.0.6 GameTest 3/3，证据：[Actions 39419885573](https://github.com/g2066203208-beep/mc-studio/actions/runs/39419885573)。GitHub 图形客户端启动实验还需以最新 Actions 结果为准。**没有实机试玩、真实运动/旋转 Sable 船体攀爬或第三人称截图验收，禁止宣称已完全复刻。**


## PEAK Player Animator 0.5 实验版

**正在开发：** [PR #6](https://github.com/g2066203208-beep/mc-studio/pull/6) 引入 `Player Animator 2.0.4+1.21.1` 原版玩家动画 API，五段原创关键帧（挂墙、攀升、横移、翻顶、松手）、多人动作状态同步、碰撞检查的真实翻顶运动。已经删除旧的直接修改两臂角度的 Mixin。此版安装时必须另外安装匹配 NeoForge 1.21.1 的 Player Animator Mod。

[完整安装、接口实现与仍未完成的实机验收](docs/climbing/PLAYER_ANIMATOR_INTEGRATION.md)。PR 保持 Draft；不要把构建成功理解为完整复刻 PEAK。
