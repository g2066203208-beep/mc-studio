# WorldSeam：Create Aeronautics / Sable 跨世界物理路线与对照证据（2026-10-22）

> **2026-10-22 / P2 decision: atomic ship transfer REJECTED.** User requires one continuously physical joined world, not a whole-ship teleport. The P1 `SeamSableInterop`, `SeamShipCrossing` and `/worldseam physics enable` implementations were completely removed from the P2 production tree; they are documented below **only as a rejected compatibility experiment**. Current remaining working code is remote fluid/block GPU meshing and read-through neighbor state. IPSable's one-authoritative-body multi-chart Rapier contacts are a **future required engine integration**, not shipped. No Sable physics GameTest has been passed. Do not revive atomic transfers without an explicit separate compatibility mode and real user approval.\n\n
## 已核查的可运行上游（非效果推断）

目标为 **Minecraft 1.21.1 / NeoForge 21.1.248 / Java 21**。下列独立项目均需按实际 JAR 和版本进行兼容验收：

| 项目 | 实际技术 | 与 WorldSeam 差异 | 上游源码/许可 |
|---|---|---|---|
| Create: AeroPortals 1.3.x | 事务式移交 Sable 的 SubLevel、乘客、连接件/机械、Plot 和恢复日志 | 整艘搬运是 **atomic move**，没有让跨水平界缝的船体在两个 ServerLevel 同时物理碰撞 | https://github.com/breakinblocks/AeroPortals ，仓库 LICENSE.md 为 PolyForm Shield + PolyForm Perimeter 双条款，**非 MIT** |
| IPSable 0.5.x | Atlas World：每个 Minecraft 维度一个 chart，统一 Rapier 刚体，跨界远端 image colliders，片段裁剪与刚体约束；已说明适配 IP dimension-stack seams | 真正连续双侧物理的参考，而非简单换维；IP 作为另一个完整渲染器/物理框架不能在 WorldSeam 原有渲染管线里直接照搬 | https://github.com/r2smith141/IPSable ，根 LICENSE Apache-2.0，外部 Rust / Sable 许可另查 |
| Immersive Portals: Aeronautics 2.0.0 | 支持 Sable sublevel 在真实 1.21.1 NeoForge 门框穿越，带跨维渲染 | 门框方案不是 WorldSeam 的无穷水平连接平面 | https://www.curseforge.com/minecraft/mc-mods/immersive-portals-aeronautics |
| Sable 2.0.x | SubLevelContainer / ServerSubLevel、Rapier 物理与 plot、本地维度的 gravity/pressure 自定义 | Vanilla Entity != Sable SubLevel，不能更换实体 level 来搬迁物理船 | https://github.com/STRELENA1/sable ，PolyForm Shield |

上述技术事实来自**直接检查仓库 Java 源代码与上游说明**。本项目没有复制任何 Sable/AeroPortals/IPSable 源码，仅定义自己的坐标与接口调用。

## 为什么 P1 不是最终「完美跨维」

当前 WorldSeam 0.11.2 客户端支持非 Respawn 普通玩家转移、近景 GPU section 缓存和有限 5x5 双维度 chunk 预载，并不持有 Sable 真正的双维度 Rapier 碰撞场景。Sable 结构包含整船 chunks、block entities、动力传动、座椅与乘客；直接 `Entity.changeDimension()` 或复制方块都会破坏它的所有权/物理连续性。

[已否决/移除] PR #94 曾提供**非连续的初步技术接入**：
1. 仅管理员 opt-in，在物理 SubLevel 原点横越 WorldSeam 水平连接面、目标 chunk 全部已加载且原生高度合法时启动；
2. WorldSeam 不自行拆分/复制/删除 Sable sublevel，交给 AeroPortals 的成熟群组事务迁移处理，失败保留源侧；
3. 独立守护船内玩家和普通实体，阻止旧版自身的 vanilla 个体迁移把乘客留在另一维度；
4. 限 256 Chunk、拒绝不完整预热；拒绝时不做同步 Chunk 生成，减少服务器主线程尖峰；
5. 安装缺失、API 改动自动拒绝，默认关闭旧存档不会迁移。

**没有实现**半艘船在主世界、半艘在下界时的 GPU 几何裁剪、统一刚体跨世界接触、船体方块实体服务端双侧交互、重力/空气压力切换、热载 chunk ticket 联动、帧级玩家/座椅传送无加载屏。即使整船原子搬运成功也不是最终验收。

## P2 必须架设的底层：One body / multiple chart contacts

1. **单刚体权限**：一艘船只有唯一 UUID/真实 Rapier 刚体；其物理权威明确，不能为跨界视觉复制第二个活动刚体。
2. **图表映射**：主世界/下界/末地分别保持原生 Y、独立生成器与物理参数。接缝只做视图/物理坐标变换（本项目 X/Z 1:1），不全局改 `DimensionType`。
3. **跨平面图像碰撞**：按船体 AABB 与 WorldSeam seam plane 求交；远端物理图像 collider 参与目标地形接触，裁剪掉源侧不应存在的碰撞；**只向原刚体反馈一次求解**。不能通过两个 Rapier 实体之间的弹簧近似取代。
4. **连续画面**：远端普通方块、飞船方块、方块实体、普通实体、流体必须按 seam plane 在同一帧裁剪，并正确处理深度/stencil、透明层、天空、动态灯光；RenderSystem 的源/目标世界状态必须安全恢复。
5. **多人和连接件**：座椅、站立乘客与飞船关联坐标映射；拖缆、轴承、传动连接跨界时保持约束；解绑/断线/出视距不留下幽灵碰撞或重复 Chunk ticket。
6. **兼容矩阵**：分别钉死 Create / Aeronautics / Sable / NeoForge / Sodium / Iris / DH/Voxy；独立图形 E2E + GPU/内存/CPU 与 MS/网络包量回归，不把裸 MC 游戏测试等同于真实模组安装。

## 发布与真实 GameTest 门槛

- 新建可丢弃存档；一次只加载一个负责水平界缝的 Mod，防止 Immersive Portals 的垂直连接面与 WorldSeam 双重处理。
- 先小型单船，再多块动力飞船、多连接链、带玩家/动物/物品、跨越两个界面时同时受力的案例。
- 通过至少 100 次来回跨界、下界屋顶/主世界地板与末地接缝、2/4 人同时穿越、目标区块未加载、船体动力损坏、旅途中保存重载/断线重连、服务器重启、Mod 缺失/版本不匹配、死亡复活。
- 人工检查视频与日志是否真的「船的一半仍在上层且一半接触下层」；如果只是整船突然出现在另一世界，不能认定连续物理通过。
- 帧时间 P50/P95/P99、卡顿 spike、目标 Chunk ticket、Rapier 刚体数与碰撞对、物理子世界重复 UUID = 0、数据恢复日志全部纳入 CI。
- 最终原生验收没有通过前，本集成 `/worldseam physics enable` 保持**默认 OFF**，不得面向真实存档推广。
