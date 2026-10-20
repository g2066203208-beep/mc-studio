# PEAK 攀爬 0.6 — 右键抓握 / 无体力 / 第一人称双臂 / 运动优化

2026-10-20 最新用户试玩反馈，优先于旧版 0.4 与 0.5 的 LMB 与体力设计。
开发分支：`feat/peak-rightclick-no-stamina`。**仅实验分支，不覆盖 main**。

## 默认操作与资源

- **RMB 按住**，主手及副手空、准星命中附近固体墙壁时抓握，松开 RMB 立即释放；不需要 G、不点击锁定。
- **WASD**：W/S 上下，A/D 横移。身体沿表面法线局部切线移动。
- **Shift**：攀爬冲跃（仍有 17 tick 触发冷却，用于动画与运动平衡）；**不会消耗体力**。
- 不再有体力属性、疲劳耗尽判定、体力网络字段、体力条或持续消耗。**无限攀爬**直到主动松手、抓点破坏、超过有效接触距离。
- 右手或副手有物品时**不抢占使用键**，以免阻碍原版放置和吃东西；持物攀爬的精细交互未来独立适配。
- 版本 `0.6.0-peak-motion.alpha.1`。需另外安装 Player Animator **2.0.4+1.21.1 NeoForge**；Sable 2.0.6 可同时加载。

## 第一人称根因及修复

Player Animator 的 `FirstPersonConfiguration` 在 2.0.4 中 `showRightArm`、`showLeftArm` **默认均为 false**。0.5 只选了 `THIRD_PERSON_MODEL` 而忘了允许渲染空手原版手臂，导致第一人称看不到伸手。

现在在 `PeakAnimationController.swap()` 给 KeyframeAnimationPlayer 显式配置：
```java
keyframes.setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL);
keyframes.setFirstPersonConfiguration(new FirstPersonConfiguration()
  .setShowLeftArm(true).setShowRightArm(true)
  .setShowLeftItem(true).setShowRightItem(true));
```
这应当通过动画 API 显示 Alex/Steve 双臂及玩家实际皮肤，而不是另外注入旧 RenderHandMixin。**尚需进入真实游戏录像确认远近、遮挡和透视裁剪**；自动化 GameTest 无法证明视觉正确。

## 运动和动作改善

- `ClimbMotion` 将玩家上 tick 速度投影到接触面的切线，再平滑混合目标速度；松开 WASD 期间主动衰减，反向改变方向时有短暂缓冲。
- `SurfaceProbe.traceOffset` 支持从预期前方肩膀位置追踪下一个相邻抓点（含 Sable 子世界局部点变换）；防止向上攀爬时目光越过上一块墙就失去攀爬。
- 玩家依然使用原版碰撞而非 `noClip` / 传送。动态 Sable 结构大速度、旋转和真正跨结构边角仍需实测。
- 五段原创动画升级：挂墙的轻微重心摆动；左右臂交替向前伸及收回；配合支撑腿蹬墙、摆腿与躯干反向扭转；横移独立探手；翻顶双手撑起和双腿上拉；放手回收。
- 顶部攀越完成后锁定“须先松开 RMB 再重抓”标记，防止停在边缘原地吸回去。
- `ClimbNetwork` 协议升级到 3，删体力字段；多人须使用同版本 REME。
- 继续使用 Player Animator 独立客户端每人动画图层，第一人称与第三人称同一套关键帧；不恢复旧的 PlayerModel 或 RenderHandEvent 硬编码。

## 验收方法与局限

1. Java 编译与 JUnit：动画库自己解码五套动画，确认可实例化；无体力耗尽接口。
2. GameTest：各方向方块命中、右键抓握/松键、W 上移、Shift 冲跃仍可用且无限、翻顶障碍阻断、正确释放防止立刻重抓。
3. 加载 Sable 2.0.6 + Player Animator 2.0.4 在游戏服务器执行相同测试。
4. Xvfb OpenGL 客户端启动，排查动画 API 装载/渲染崩溃。
5. **最关键仍需真实画面**：第一人称两只手必须在 0.6 中可见且动作真的向前探、交替扒墙；F5 双手双脚与身体协调，爬过连续高墙；翻顶从实际身位与碰撞自然走完。
6. 实际移动旋转 Sable 船体、多用户同步与其他动画 Mod 叠加尚未完整验收。

任何自动测试通过不能被包装为“完全复刻 PEAK 视觉效果”。没有真实玩家视角录像和连续跨方块端到端验收之前保持 Draft，别合并 main。
