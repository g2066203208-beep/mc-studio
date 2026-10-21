# REME NPC v0.5.4 — Minecraft 真客户端 GUI 验收证据与边界


## 2026-10-21 v0.5.4 客户端真正进世界验收（首次完成）

- JAR 源提交 `e469663d8c648e22cdd26d339c5f3ca0f14665c9`；`226220f3d52c015c859bc39662881532674d3274` 只改图形客户端测试脚本。
- **Minecraft 1.21.1 + NeoForge 21.1.248 原生图形客户端**：GitHub Linux Xvfb/Openbox 用真实鼠标键盘 `xdotool` 完成首次辅助功能引导、点击 Singleplayer、打开新世界设置、开启 Allow Commands、点击 Create New World、启动集成游戏服务器；实际世界进入成功，非仅启动菜单。
- [实际图形客户端运行 39545260822](https://github.com/g2066203208-beep/mc-studio/actions/runs/39545260822) SUCCESS；artifact `12321243417` 含 11 张真实截图和 `client-gui.log`, `run/logs/latest.log`。日志 `Dev ... logged in with entity id`、`REME NPC: /npc present on dispatcher`。
- 在该真实世界，模拟键盘输入 `/remecheck`，聊天栏显示 `/npc registered=true`；`/npc spawn` 成功，在客户端渲染出 Steve 模型 NPC；`/npc info` 真正回显饥饿、精力、社交、意图与区域；`/npc demo` 生成 6 名各有姓名和职业的居民，画面可见多个实体；`/time set night` 真实切夜且画面中的居民仍在。**这次截图由实际游戏窗口截取，非 AI 图像或服务端模拟。**
- NeoForge GameTest [39545266331](https://github.com/g2066203208-beep/mc-studio/actions/runs/39545266331) SUCCESS，**13/13 required tests**，含服务端 FakePlayer 空手右键属于自己的 NPC，确认问候记忆实际增加。**没有用 Xvfb 图形鼠标手工右键并打开背包界面**。
- **真实 Windows PowerShell 安装测试** [39545266342](https://github.com/g2066203208-beep/mc-studio/actions/runs/39545266342) SUCCESS：非交互干运行不改 mods、备份重命名旧版 REME、不误删其他 Mod、新版安装 hash 校验通过。
- Java21 + NeoForge 编译及双 ZIP artifact [39545266219](https://github.com/g2066203208-beep/mc-studio/actions/runs/39545266219) SUCCESS，installer artifact `12321820441`，ZIP `REME-NPC-HMCL-v0.5.4-installer.zip`，完整解压得到 `Install-REME-NPC.cmd`、`Install-REME-NPC.ps1`、`README.txt`、`reme_studio-0.5.4-dev.jar`。JAR 128021 bytes, SHA256 `c31c085d490d3a60353ae4ffd098c1d9736a6dd6b027d40b09b9cf2a5c277568`，已解包并确认元数据 ModID=reme_studio，version 0.5.4-dev。
- **尚未完成**：用户自己的 HMCL 游戏实例是否已经载入正确版本、222 Mod 整合包兼容性、真实 GUI 右键与背包窗口、长时间自然生成/数日行为、床/工作台 POI、充分的生活生产、20/50/100/200 NPC TPS。不得以已进入 CI 测试世界推论“所有人环境可用”。
- 已发现轻微文案问题：v0.5.4 Mod 元数据正确，但部分自检/登录提示仍显示旧 `v0.5.2`，只是字符串未更新，不应误当实际安装的 JAR 版本；以模组界面的 Mod 版本和文件 SHA 为准。

## 原版单人世界操作过程

GitHub workflow `.github/workflows/npc-client-gui.yml` 配合 `scripts/test-client-gui.sh` 在 ubuntu-latest 上启动 Xvfb + Openbox + 实际 NeoForge `./gradlew runClient`，使用 xdotool 模拟鼠标点击并截取每步窗口画面；它与依赖 GameTestServer 的 `civic-gametest.yml` 是相互独立的两套验证。

成功世界截图在 artifact 12321243417 中：`client-gui-world.png`、`client-gui-remecheck.png`、`client-gui-npc-spawn.png`、`client-gui-npc-info.png`、`client-gui-six-residents.png`、`client-gui-six-status.png`、`client-gui-night.png`。这些文件都对应运行 39545260822 的真实屏幕。

## 给用户的一键安装（必须选对目录）

1. ZIP 全部解压，不在 Windows ZIP 内部直接运行 CMD。
2. 关闭 Minecraft；双击 Install-REME-NPC.cmd。
3. 选择 **HMCL 正在启动的 Minecraft 1.21.1 NeoForge 游戏实例根目录**，而不是浏览器下载文件夹或 HMCL 程序目录。
4. 输入大写 INSTALL；脚本会在实例 mods 中备份旧版 REME 并安装一份新的 JAR；其他模组和 saves 不改。
5. 重启 HMCL 当前版本；Mods 列表显示 REME MC Studio 0.5.4-dev。进入创造测试存档执行不需作弊的 /remecheck，再在允许命令权限下执行 /npc spawn。
6. 若 /remecheck 仍是未知命令，提交该具体实例的 **logs/latest.log** 和 Mods 页面截图。不要再仅凭 Github CI 成功声称用户已加载。
