# 桌面端 / 网页端交接

## 用户目标

GitHub 是整个 Minecraft 工作室的开发中心。代码、需求、研究结论、任务状态、测试证据与产物版本需要在仓库中留档，不能仅保存在某一个聊天里。用户主要游玩并提出需求，助手负责工程工作。

## 接手入口

1. 阅读 `AGENTS.md`、`README.md`、`docs/STATUS.md` 和本文件。
2. 检查当前分支、HEAD、未提交更改及相关 PR，避免覆盖其他会话的工作。
3. Minecraft 1.21.1 / NeoForge 21.1.248 / Java 21 为当前暂定基线。
4. Linux/云端在仓库根目录执行 `bash scripts/build.sh`。Windows 本机可执行 `scripts/Build.ps1`。
5. 云端无需 `local/environment.json`、Windows 游戏目录或用户存档。
6. 完成修改后提交代码并记录结果。具备远程写权限时推送任务分支、创建 PR，核对对应提交的 Actions 结果。

## 当前工作

- 最小 Mod 工程已经在 Windows + Java 21 下构建成功。
- 尚无玩法功能；没有测试源码，不能把 `test NO-SOURCE` 写成自动化测试通过。
- GitHub 专用私有仓库：https://github.com/g2066203208-beep/mc-studio。初始化 PR #1 已合并到 main，合并提交 facfadeeafe4fcfe055d33754259885012196e4d。
- 仓库应包含全部工程文件及 Gradle wrapper JAR；不要上传 local、build、存档或第三方整合包。
- 已验证云端构建：https://github.com/g2066203208-beep/mc-studio/actions/runs/39402002769。对应 PR head 为 11c6feca3e25ca2f5be8e9a86a04a94b756ec036；build 和上传产物步骤均成功。
- 下一任务：根据用户玩法需求实现首个功能，然后独立分支、PR、Actions，再由本机会话进行游戏验证。
- 网页聊天只有实际提供 GitHub 写工具或可写执行环境时才能修改和提交。若只有搜索/阅读工具，明确说明能力缺口，不声称已提交。

## 交付记录要求

每个任务记录：需求、实现、分支/提交/PR、构建结果、游戏实测结果、已知限制、下一步。
游戏启动、完整整合包兼容和本机部署由具备本机访问能力的会话验证；编译成功不能替代这些验收。

## 可复制给另一个聊天

请接手这个 GitHub 仓库的 Minecraft Mod 开发。先读取 AGENTS.md、README.md、docs/STATUS.md 和 docs/HANDOFF.md，检查最新提交与相关 PR，再继续任务。默认 MC 1.21.1、NeoForge 21.1.248、Java 21。使用独立任务分支，构建后提交代码并同步交接记录；明确区分编译结果和游戏内测试结果。


## 新增研究任务：Sable Hands（双手物理攀爬，规划阶段）

2026-10-20，用户确认：Sable 指 RyanHCode 的 Minecraft 物理/动态子世界 Mod，不是同名独立游戏；玩法参考 White Knuckle 的双手交替抓握，保留 Minecraft 原版方块手臂，优先做功能。墙壁、平地、天花板、可旋转/移动的 Sable 方块都应能够按有效方块表面抓握。

规划文件位于独立候选分支 docs/sable-hand-climbing-plan：
- docs/climbing/SPEC.md：需求、抓握与身体约束、静态与 Sable 坐标架构；
- docs/climbing/OPEN_SOURCE.md：上游源码/版本/commit/许可证清单；
- docs/climbing/TEST_PLAN.md：六向方块、旋转结构、单/双手、联机测试；
- docs/climbing/DEV_PROMPT.md：可供下一个会话直接执行的完整开发指令。

当前只有研究与文档，**没有攀爬源代码，也没有新攀爬功能构建或实测**。不要把早期工作台骨架的云端 BUILD SUCCESSFUL 当成本功能通过。计划下一任务为 M0：原版方块及 Sable 动态碰撞表面的真实抓点识别，先通过可视化测试和自动化校验；然后按 M1 静态双手攀爬、M2 Sable 动态锚点、M3 联机、M4 整合包兼容逐阶段开发。

新会话接手先检索该候选 PR 和最新 main HEAD；如文档已经合并按 main 读取，不要以旧分支覆盖新版。
