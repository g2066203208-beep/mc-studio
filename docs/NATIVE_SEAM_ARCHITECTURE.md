# Native Seam — 自研无缝维度引擎技术路线

MC 1.21.1 / NeoForge 21.1.248 / Java 21。项目 PR #18。

## 基础技术事实与学习资料

- Immersive Portals 的实现说明指出，视觉无缝除了传送还依赖客户端多维上下文、服务端跨维区块和实体跟踪、定向网络协议、目标维度预渲染及相机帧级切换。
- NeoForge RenderLevelStageEvent 能叠加少量自定义几何，但不等于拥有可独立构建和管理的多维 LevelRenderer。
- 参考：https://github.com/iPortalTeam/ImmersivePortalsMod/wiki/Implementation-Details
- 参考：https://lexxie.dev/neoforge/1.21.1/net/neoforged/neoforge/client/event/RenderLevelStageEvent.Stage.html
- 参考：https://github.com/UpperMoon0/ImmersivePortals

**这些是架构研究来源，REME 的网络格式、缓存、几何计算和上下边界实现均为自主代码，不引入 Immersive Portals 依赖。**

## v0.4.0-dev 已实现

| 模块 | 当前功能 |
|---|---|
| SeamGraph | 双向维度拓扑、1:1 X/Z、边界 Y 距离映射 |
| SeamServer | 玩家和未乘骑实体的跨界迁移；使用 Minecraft 原生切维方法 |
| SeamSettings | 世界存档专属 opt-in 开关 |
| SeamFrameState | 顺序号、维度和视域隔离、全量/增量/删除、光照变更 |
| SeamPreviewPayload | NeoForge Version 2 的有界网络帧 |
| SeamPreviewSender | 5tick 更新近场、20tick 远景取样、40tick 保活，每轮最多强制加载1个缺失目标区块 |
| SeamPreviewClient | 模型和光照绘制、更新时遮挡预计算、距离和视锥裁剪 |

近场采样容量 13×13×12=2028，远景 19×19=361 个单方块表面，满载上限 2389，不超过数据包 2560 条变化上限。远景最多深入目标维度 320 格，优先使用真实地形高度图，不自行捏造地形。仅采样目标侧已加载区块。

## 离真正无缝还缺什么

1. 自主远端区块服务：按维度、区块、区块段版本索引，支持订阅/卸载/增量区块与光照，限制每玩家加载票据和带宽，替代临时快照。
2. 真正的 Chunk Renderer：分层网格、流体透明排序、跨维光照与天气、块实体和远端实体。区块缓冲与远端视锥独立。
3. 屏幕和深度裁剪：边界面上正确的两套深度、镜头换维时同一帧上下文切换，真正去除原版维度加载画面。
4. 连续实体和物理：远端实体跟踪、跨边界碰撞、骑乘树原子迁移、Sable 刚体与 sublevel 验收。
5. 世界生成与存档：新生成区块的接缝/基岩规则，旧存档单独备份与迁移；不能擅自挖除原世界基岩。

## 严格验收

- Java21 构建 + 单元测试通过，不等于客户端成功启动。
- 客户端：边界提前看见完整地形、双向穿越不中断画面、光照水体物体正确、离开边界及时卸载远端区块。
- 服务端：多人并发、断线、跨边界实体和载具、带宽内存上限、重启持久化。
- 整合包：TerraFirma Rebirth 2、Sable 的独立实机测试；录屏逐帧检查跨维画面、帧率和稳定性。

**v0.4.0-dev 仍是可编译的近/远场代理研究版本，不是上述所有验收通过的最终无缝引擎。**

## v0.5 的真实底层实现

`SeamSectionCodec` (纯 Java) → `SeamSectionSender` (ServerLevel / ChunkSection) → `SeamSectionPayload` (自定义有界二进制协议) → `SeamSectionCache` (维度和视域隔离、LRU) → `SeamSectionClient` (区块段可见模型缓存与按视锥绘制)。该通路不会伪装成当前 ClientLevel 的正常 Chunk packet，也不会加载第三方门户代码。未来真正完整实现需替换最终的逐块模型渲染端并加入多 ClientLevel 及跨帧切换。

独立的 `SeamBounds` 提供 Nether 上接缝 Y=128，`SeamTerrain` 只在管理员显式允许且是新生成区块时改动主世界底部 / 下界屋顶的基岩。ChunkEvent.Load 的上游文档明确提醒：回调可能发生在区块转为 FULL 之前，禁止在这里执行额外世界加载；实现只直接修改事件提供的 ChunkAccess。

## 实际运行证据

2026-10-21 在 GitHub Actions 中的 NeoForge `runGameTestServer` 实际启动、运行并通过 1/1 测试。详情在 [TEST_EVIDENCE.md](TEST_EVIDENCE.md)。不应将它扩大解释为客户端无缝穿越、整合包兼容或 GPU 渲染完成。
