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

## 2026-10-21：沉浸车辆模型研究 Skill（新任务）

- 用户要求把持续模型研究变为可复用 GitHub Skill；在独立分支 `docs/immersive-vehicle-model-skill` 增加 `skills/immersive-vehicles-modeling/SKILL.md`、真实 OBJ+JSON 的五种车辆研究矩阵、造型/UV/动画/安装契约、原创摩托车示例、纯标准库 OBJ/PNG/JSONC 检查器与单测。
- Skill 和网格检查不是已经创建原创摩托车 OBJ，也不表示 Minecraft、Sable 或公开网页版已通过真实游戏测试；下一位助手在用户要求做摩托车时按 Skill 创建并提供真正可旋转检视的新模型。
- 已有真实 3D 源码 `viewer/index.html`，主要针对 Mustang；如果要预览自己制作的摩托车需扩展支持任意 OBJ+PNG。
- 操作门槛：审查来源许可后使用原创资产；执行自动化测试，再审阅 CI 和 PR。

## 2026-10-21 · WorldStack 工作分支

- 分支：`feat/worldstack-immersive-portals`；目标 PR 合入前不可将其写成 main 已完成功能。
- 入口：`docs/WORLDSTACK.md`；实现 `WorldStackLayout`、`WorldStackBridge`、`WorldStackCommands`。
- `/reme worldstack setup` 管理员显式启用，不自动修改既有世界；`status` 查看连接数量；使用上游维度堆叠逻辑，不复制上游代码。
- 拓扑：末地（顶）→ 主世界（中）→ 下界（底），1:1 X/Z，四个双向门户面，无重力翻转。
- 提交后请根据 Actions 检查编译与 JUnit；客户端、服务端、整合包实测仍需真实游戏环境，不能用云端 BUILD 代替。
- 已知限制：老区块基岩未改写；存在其他全局传送门会被保护性拒绝；跨维度载具、Sable 物理尚未实测。
