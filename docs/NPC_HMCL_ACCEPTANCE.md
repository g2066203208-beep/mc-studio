# REME NPC v0.6.3-dev — HMCL 游戏内验收

这是一份随本次安装包实际打包的说明。支持 Minecraft Java 1.21.1、NeoForge 21.1.248 及更高同系列版本、Java 21。modId = reme_studio。**实验版本，并非完整 NPC 文明系统。**

## 正确安装
1. 关闭 Minecraft，第一次在新建的创造模式世界测试，不要使用唯一的正式存档。
2. 完整解压下载的 GitHub Actions ZIP（注意 Actions 的 ZIP 外面可能还有一层 ZIP），直至看到 reme_studio-0.6.3-dev.jar、Install-REME-NPC.cmd、Install-REME-NPC.ps1、README.txt。
3. 手动安装：只把这个 JAR 放到 HMCL **当前选中的 NeoForge 1.21.1 版本的实际游戏目录** 的 mods 内。使用版本隔离时，不能放到猜测的全局 .minecraft/mods。必须先备份旧 reme_studio JAR，确保只剩一个版本。
4. 或解压后运行 Install-REME-NPC.cmd，选对游戏目录，确认 INSTALL；脚本会先备份相同 modId 的老版本，不碰存档。
5. 重启该实例。Mods 页面应显示 REME MC Studio v0.6.3-dev。

## 新世界的五步真实测试
(1) 输入 /remecheck：**无作弊也能用**；必须看到 /npc registered=true 和 v0.6.3-dev。如果命令未知，即这个实例没有正确载入 Mod，不必再尝试其他功能。
(2) 开作弊后输入 /npc spawn TestNPC：应该真正出现可见 NPC，/npc info 返回状态，空手右键对话；Shift 空手右键**自己的** NPC 打开36格背包。
(3) 输入 /npc demo：生成六名 NPC；/npc settlement：附近确有三户不同住址时显示聚落，但不会凭空生成村庄建筑。
(4) 店员验证：靠近自己的 NPC，输入 /npc shopkeeper、/npc setwork；拿着面包正常右键给店员补入真实背包，再拿绿宝石右键，一个绿宝石买一份现有面包/苹果/胡萝卜。没货、背包满就不能成交。**没有自动生成便利店建筑。**
(5) /npc info 观察真实工作/饥饿/精力。守卫需要真实敌人或巡逻点，农民要成熟作物，运输员要两座已加载的原版箱子，否则他们不会凭空生产出职业活动。

## 重要能力边界
- 真实玩家同等能力：目前可见的 NPC 仍是 PathfinderMob。独立 RealNpcServerPlayer 仅为未登录/未加入 PlayerList 的隔离原型。外部 Player Animator/PAL/3D Skin Layers 等不保证直接生效。
- 野外自然刷新规则已注册于部分主世界地表生物群系，但不会保证玩家站在某处立即刷出 NPC；局部72格上限4人。
- 报警/监狱：仅支持真实 NPC 商队抢劫、受害者举报、附近守卫调查，以及管理员指定的铁栏杆拘留点；没有完整城市场景与司法系统。管理员看着真实铁栏杆运行 /npc jail set。
- 部分皮肤没有真正双层服饰，所有原画与 3D 挤出美术目标尚未达到；发布时仍标为研发阶段。

## 不能用构建成功替代的验证
NeoForge GameTestServer 断言实际方块/物品/实体、居民身份、工作、店员售卖及犯罪状态；真正图形客户端通过 GitHub Actions Xvfb 启动进入单人世界，测试 /remecheck、/npc spawn、/npc demo、聚落、实际挖矿和 NPC 拾取。**尚未用真人鼠标实际点击店员完成交易、观测牢房内完整押送、测试你的 HMCL 整合包、200 NPC 长时间 TPS。**

证据：
- GameTest 44/44 https://github.com/g2066203208-beep/mc-studio/actions/runs/39707816385
- 实际游戏客户端 + 截图 https://github.com/g2066203208-beep/mc-studio/actions/runs/39707811628
- Windows HMCL 模拟安装 https://github.com/g2066203208-beep/mc-studio/actions/runs/39707816422
- 独立草稿 PR https://github.com/g2066203208-beep/mc-studio/pull/65

若 /remecheck 未知、启动崩溃或 NPC 没动作：把当前 HMCL 实例 Mods 页面和该实例 logs/latest.log 发来，可以辨别未装正确目录、版本冲突、实际 NPC AI 出错。安装器脚本不能自己推断你的本机版本路径。