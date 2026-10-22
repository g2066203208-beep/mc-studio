# WorldSeam — 不可降低的最终验收契约 / Development acceptance ledger

更新时间 / Last review: 2026-10-22. 工作分支：`feat/worldseam-continuous-seam-p2` · [PR #98](https://github.com/g2066203208-beep/mc-studio/pull/98) (draft, must NOT merge yet).

## 永久目标 / Non-negotiable completion gates

- [ ] 主世界、地狱、末地和明确支持的模组维度在接触面如一个连续世界；不能用维度传送、全船原子搬迁、克隆刚体、贴片假渲染冒充无缝。
- [ ] 原生两个 ClientLevel / ServerLevel 的方块、光照、天空、流体、方块实体、普通实体和所有可见几何在接缝同时正确加载、裁剪、同步、渲染。
- [ ] 玩家、Mob、物品、红石、流体、机械、爆炸、实体碰撞都能沿实际空间在接缝实时交互；没有只有脚部的假碰撞，没有重复加载或长时间卡顿。
- [ ] 真正 Create Aeronautics + Sable 船体：**ONE authoritative Rapier rigid body**、两个原生维度的真实接触同时施加法向力、摩擦、转矩；创建器动力图、约束、关节、乘客、力反馈持续一致；跨界船体两半在两边同时实际可见。不能用两个刚体同步或原子传送。
- [ ] NeoForge 1.21.1 真正客户端/服务端 + 240+ 次实玩跨界测试，崩溃、泄漏、画面、FPS/TPS、实体追踪、多模组兼容的记录均通过；得到用户对最终游戏内体验的确认。

## 已实现且有具体实机/CI 证据

- [x] WorldSeam 独立编译、NeoForge GameTest、基础无 respawn 玩家换世界、原生远端 GPU 方块与液体网格、原生预热缓存的**特定场景**：详见 [原版本基线 CI](https://github.com/g2066203208-beep/mc-studio/actions/runs/39771397323)；日志 163/163 visible GPU sections，流体 38,365/38,380 cells 进入绘制流程。
- [x] 240 次真实主世界/地狱/末地往返压力测试，客户端 240 次 handoff，无大于 1 s 的 tick gap： [CI 39771397465](https://github.com/g2066203208-beep/mc-studio/actions/runs/39771397465)。**只证明此基线和环境**。
- [x] 读取 Sable 2.0.6 实际 `ServerSubLevel` / `SubLevelContainerHolder` 接口，真实发布版 Sable + Create 6.0.10 + Aeronautics 1.3.2 的专用服务端 GameTest 中组装了 18 个真实方块并识别一艘 straddling 船体： [CI 39784832595](https://github.com/g2066203208-beep/mc-studio/actions/runs/39784832595)；日志 `WORLDSEAM_REAL_SABLE_STRADDLE_OBSERVED: ... straddling=1, physicsBridgeActive=false`。**没有双世界求解器**。
- [x] 修复真实 CI 缺陷：Minecraft `AABB` 测试迁移 NeoForge GameTest；从 Modrinth 官方 `sha512` 验证发布版 JAR；发布版清单写入项目路径；专用服务器避免反射扫描 ClientLevel overload。

## 本轮新增、未验收 / Changes needing current-head CI

- [ ] `SeamBodySplit` + `SeamArrival.hasJoinedClearance`：玩家/普通实体进入相邻维度前用**真实两个 ServerLevel** 的原生碰撞体检查自己身体的两部分；注意它还不是全帧实体碰撞统一求解。
- [ ] `SeamNativeGeometryClient.isBuried` 按逻辑接缝 Y=128 而非地狱原生最大构建高 Y=256 读取跨维相邻方块；新增真实 GUI 可见性回归检测。
- [ ] `SeamShipChartBounds`：将同一船体的**AABB broad-phase 体积**映射成两个维度的裁剪区，不代表实际 OBB/网格碰撞求解。
- [ ] `SeamShipResidency`：真实 Sable 船体即使**没有玩家在附近**，接近维度接缝时仍异步向相邻 ServerLevel 申请有界区块 region ticket；不调用同步 `getChunk`，32 个唯一 ship UUID 上限，远离、卸载、禁用时移除；新增官方 Aeronautics/GameTest 验收：真实飞船接缝必须持有远端票据。
- [ ] 最新改动全部独立 Build / JUnit / GameTest、真实发布版第三方模组 GameTest、GUI 与 240 次长测的 CURRENT HEAD 通关。注意中间旧提交成功不等于最新版本通过。

## 实现中必须遵守的约束

- 只修改 `mods/worldseam` 及其专门 CI，不影响 NPC、攀爬、机械 Boss 等独立工作室。
- 第三方 JAR 仅在 CI 中下载运行并校验官方 SHA-512，不复制或重新分发 AeroPortals/Sable 等限制许可代码。IPSable 的 Apache-2.0 方案可作技术研究，但实际以 Sable 官方发布版 API 及许可证为准。
- 不能将 source/target 的双份显示缓存视为两份物理刚体；世界真实接触必须通过一个原生 solver 生成 impulse，并且没有原生光照/液体流动时不可说“完全连在一起”。

## 下一轮最高优先级

1. 检查 PR #98 当前最新提交的 [Actions](https://github.com/g2066203208-beep/mc-studio/actions)，修复任意独立 GameTest、真实 Sable 船体/区块票据、真实 GUI、长时间压力测试失败；为新增票据补移出接缝释放和断线/禁用 GameTest。
2. 研究并实际实现 Sable/Rapier 在原生两维地图上的 chart 区隔及 image colliders + single rigid body contact manifold；必须测试真实组装船压住不同维度方块所产生的同体 torque。未通过则 `physicsBridgeActive` 保持 false。
3. 完整实体/船体原生渲染裁剪、跨界动态光照与真实交互的连贯路径，并逐项用真实 MC 客户端视频/截图作验收。

每小时开发工作记录应在实际提交和 CI 证据基础上更新这里，目标不能因为实现困难自行缩减。
