# PEAK v0.10 — 墙→天花板身体桥接、视角相对攀顶

基于 v0.9 Draft 分支，独立实验版，目标：修复抓点先切屋顶但身体仍停留在墙边、W 反向/下滑、屋顶无法随视角转身。

## 在真实源码中发现的问题

1. v0.9 在天花板使用旧抓点锁定的 grabYaw：角色抓墙面对墙时 W 移动仍按墙的朝向映射，旋转视角不改变屋顶运动。
2. traceTransition 一命中 roof 就直接赋值 s.left 和接触面法线、更新 roof 动画；身体还没移动到 roof 下方，渲染先于运动切换。
3. 同面重新抓握可能把平滑屋顶 yaw 重置到前一 tick；刚取得屋顶时又立刻搜索旧墙，造成姿态回弹。
4. Sable 移动子空间的逻辑位姿和玩家世界坐标需要一致；不允许把锚点变化误当成船体速度，也不能沿装饰 OUTLINE 虚假碰撞面抓握。

## 修正与状态机

- WALL：保留原墙握点、W 上爬；只在旧接触面局部边缘附近扫描新碰撞面。
- BRIDGE：如果命中顶棚，先记为 pendingRoof，保留 wall normal，不立即播放 roof 姿态；沿安全受限速度 <=0.175 格/tick 移到顶棚下方，禁止向下位移，碰撞检查未通过则停止该 tick 的桥接移动。新增加 peak_wall_to_ceiling 的 0.55 秒双臂上举过渡关键帧。
- ROOF：检测真实眼位在候选 roof 接触点下方、沿切面偏移 <0.58 方块、法线距离 0.19–1.12 后才提交抓点。再启用屋顶双臂悬垂动作和基于当前镜头 Yaw 的 WASD；身体 Yaw 每 tick 最多追随 19°。
- 防错误转向：初登 roof 若镜头仍对准旧墙，短暂将 W 导向旧墙的外法线，避免角色直接挤回墙面；一旦玩家转动镜头朝向安全屋顶，立即恢复完整镜头相对移动。roof 同面重抓不得重置身体 yaw。
- 屋顶中心不会直接换回原墙；只有触点接近 local block face edge 才允许 roof→wall 自动切面。此项与 Sable 的 local pose / world pose 变换兼容。
- 桥接超时 23 tick，找不到合法新 roof 时取消候选并继续沿旧墙抓握，无无碰撞/瞬移修补。

## 按键与版本

主副手空：右键按住抓握；WASD 攀爬；顶棚上的 W 按当前视角水平朝向；Space 瞄准蹬墙跳、Shift 短冲跃；松开右键立即放开。Minecraft 1.21.1、NeoForge 21.1.248、Java 21、Player Animator 2.0.4，按需 Sable 2.0.6。版本 0.10.0-peak-roof-bridge.alpha.1。

## 测试及待验证

- 新增 GameTest：距离顶棚太远时保持 wall 抓点不提前提交；移到真正 roof 下方再确认切换；初登 roof 的 W 不进入旧墙；旋转视角后 W 即刻改向；斜置 roof 切平面、转角 yaw；碰撞限制和非下降桥接速度。
- 实机仍需连续录影：10 格高墙→水平顶棚→旋转摄像机→沿顶棚移动→下另一面墙；Sable 物理船体平移、旋转及高延迟联机。CI 自动测试不等于镜头和第三人称动作已经足够好看。
- 已知：桥接只针对墙→屋顶；屋顶→侧墙仍使用原有直接换抓点策略，后续可能需要同样的接触相位处理；原版玩家碰撞盒始终保持直立，尚非物理双手独立 IK。

## 参考

- https://github.com/ryanhcode/sable/blob/main/common/src/main/java/dev/ryanhcode/sable/api/SubLevelHelper.java
- https://github.com/ryanhcode/sable/blob/main/common/src/main/java/dev/ryanhcode/sable/api/entity/EntitySubLevelUtil.java
- 上游 Sable 物理环境不能仅靠旧抓点世界坐标的差值替代真实速度；只用同一局部锚点变换作限幅短程随动。
