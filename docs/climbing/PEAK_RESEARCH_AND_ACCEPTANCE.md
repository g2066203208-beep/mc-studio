# PEAK 攀爬重构｜技术参考、手感规格与验收

日期：2026-10-20。此版本取代旧版 G 模式、双手点击锁定及左手弓箭实验作为主线开发目标。

## 真实 PEAK 操作参考

- 游戏玩法操作：https://peak.wiki.gg/wiki/Movement （贴墙按住 LMB 进入攀爬、W 向上）；https://peak.wiki.gg/wiki/Heads_up_display （攀爬消耗体力、Shift 冲刺/冲跃）。
- 操作键位：https://www.destructoid.com/all-peak-key-bindings-how-to-change-controls/ （左键攀爬/使用物品、右键帮助队友、E 互动、Q 丢物、Space 跳、Shift 攀爬冲刺）。
- **我们只参考玩法手感/输入，不复制 PEAK 的游戏代码、美术资源、纹理或模型。**
- Minecraft 交互差异：第一阶段约定**空主手按住 LMB**靠近可抓几何进入攀爬，松开下落；持工具的主手仍保持原版挖掘，避免频繁劫持原版点击。右键继续原版使用。地面上表面站立不抢占普通挖掘。
- WASD：墙面 W 上爬，S 下爬，A/D 沿墙横移；天花板 W/S 前后、A/D 横向；在地面贴附沿切线运动。Shift 上冲跃，持续消耗体力；停在地面回复。
- 稳定握住墙面时**无需手动左右交替点按**。系统周期性寻找新的接触点，真实玩家角色遵循方块碰撞与重力，不使用自由飞行、无碰撞或每 tick 强制瞬移。
- Sable 动态方块仍通过 Companion 子世界局部抓点和逻辑姿态转换；3D 旋转/高速船体尚须另设实体游戏测试，不因共同启动成功而宣称无抖动。
- 第一人称使用原版 Steve/Alex 手臂，伸向前方，左右交替；第三人称基于 PlayerModel.setupAnim 的后置动画、左右袖子及双腿配合、网络广播其他人可见。拒绝简单固定手臂偏移或只在本地客户端播放。
- 游戏内体力耗尽解除攀爬，松开 LMB 当帧释放。Shift 冲跃仅按键上升沿触发一次并设冷却，防止一直按住无限喷射。

## 源码学习清单（使用公开 API 或独立实现；不直接复制他人代码）

- ParCool 1.21.1-NF：https://github.com/alRex-U/ParCool
  - `src/main/java/com/alrex/parcool/mixin/client/PlayerModelMixin.java`：动画在 setupAnim 后叠加，而非在 PlayerRenderEvent.Pre 被覆盖。
  - `client/animation/impl/ClingToCliffAnimator.java`、`ClimbUpAnimator.java`：原版角色双臂抓墙、过顶、身体和腿的协同效果参考。ParCool 为 LGPL-3.0，严禁在此 All Rights Reserved 仓库复制其源代码段。
- ClimbingClaws 1.21.1-neoforge：https://github.com/IntelligenceModding/ClimbingClaws
  - `common/event/ClimbingClawsClimbHandler.java`：方块真实形状、侧墙与顶棚接触、限速、悬挂、落地处理；MIT，可参考但这里独立实现。
- ParCool Sable 兼容附属：https://github.com/semillakan6/ParCool-CompatibilityAddon-NeoForge
  - `compat/extern/sable/SableCollision.java` 和 `compat/mixin/sable/WorldUtilSableCollisionMixin.java`：动态子世界旋转的碰撞边界检测，警惕全局 noCollision 注入。
- Sable API：https://github.com/ryanhcode/sable 和 https://github.com/ryanhcode/sable-companion
  - 局部/全局接触点的可靠转换，Rapier 是动态结构物理，不是直接提供人体攀爬控制器。
- NeoForge 官方：https://docs.neoforged.net/docs/1.21.1/、`RenderHandEvent`、`PlayerModel.setupAnim` 及 `PacketDistributor.sendToPlayersTrackingEntityAndSelf`。

## 独立验收关卡

1. GitHub Actions Java21 / NeoForge21.1.248 编译、JUnit 规则测试必须通过。
2. GameTestServer 模拟空手 LMB 按下抓墙、W 产生向上速度、松手释放、体力消耗及 Shift 冲刺、六向方块抓点。
3. 用真实 Sable 2.0.6 + Rapier 加载后运行相同 GameTests。只是兼容启动/静态测试；不是在旋转实体抓墙。
4. **必须进行真正图形客户端、第一人称两臂同时渲染、第三人称两臂攀爬、从两格到高墙的持续攀爬、顶端攀上平台、Shift 冲刺、松手下落的手动实测**；此项未验证不能当成完成。
5. 两人 dedicated server：旁观者同屏观察对方两臂攀爬动画是否同步。
6. 真实 Sable 船体移动、旋转、急停、解体的挂墙与释放回归测试。
7. NeoForge 整合包 Create / Aeronautics / TFC 组合测试；遵守玩家正式存档不自动变更。

## 当前阶段

开发中的 PEAK MVP 分支 `feat/peak-climb-mvp`。完成任何提交都只能说明代码存在；不能因为编译成功宣称图形客户端的手感和动画已验收。必须在 PR 中记录真实已完成/未完成的测试证据。
