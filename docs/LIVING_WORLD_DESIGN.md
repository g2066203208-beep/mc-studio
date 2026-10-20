# LIVING WORLD NPC / 活世界 NPC 技术与游戏设计纲要
版本：2026-10-21。适用当前 Minecraft Java 1.21.1 / NeoForge 21.1.248 / Java 21。**本文是研究与实施目标，不意味着功能全部实现**。

## 一、纠正目标
NPC 并非玩家角色模型加脚本，也非 GPT 对话机器人，而是有身份和社会位置的**持续模拟代理**。每人至少有：姓名/性格、住宅/工作地点、职业/资源、日程、需求、能力、私人记忆/他人关系、风险判断、当前任务和活动执行结果。玩家不是世界中心；玩家可作为他人一样的对象被观察、对待和记住。

检验用语：不是“能对话/会走路/按命令挖矿”，而是“玩家完全不介入，在村庄观察两个游戏日，NPC 仍然自行做了有因果关系的事情”。

## 二、三大作品的真正设计机制
### Stardew Valley
- 日程以时间、星期、天气、季节、节日、关系和世界进度切换；不是只写「一天走四个点」。
- 关系随着交往改变，玩家能在不同时机认识居民的新面貌，有偏好、礼物与事件。
- 学习：*基础日程表 + 高优先级异常覆盖 + 社交关系门槛的场景事件*。先形成可预测的生活节律，再允许被动态打断。
- 原始依据：https://wiki.stardewvalley.net/Modding:Schedule_data 与 https://stardewvalleywiki.com/Villagers 。

### Red Dead Redemption 2
- 情境交互：招呼、挑衅、威胁、缓和、作证，环境与人物身份决定反应；行为可能造成声誉和社会后果。
- 学习：*感知→解释事件→意图→动作→见证人/传闻→对后续行为影响*。不要宣传“RDR2每个路人运行永久人生 AI”，我们只借鉴其出色的临场真实感。
- 原始依据：https://arstechnica.com/gaming/2018/08/rockstar-reveals-first-red-dead-redemption-2-gameplay-details/ 。

### Skyrim
- 游戏的 AI Package 将 NPC 的工作、进食、睡眠、移动、sandbox 配置成可调度任务，与条件、地点、时段结合。
- 学习：可配置「活动包」和中断恢复。不为每个角色重复编码一套 AI。
- 依据：https://en.uesp.net/wiki/Skyrim_Mod:Creation_Kit_Usage 。

## 三、值得研究的现有 Minecraft 开源项目（仅参考架构）
- MCA Reborn：https://github.com/Luke100000/minecraft-comes-alive （查看 1.21.1 分支）
  - 源码路径 `common/src/main/java/net/conczin/mca/entity/ai/` 下存在 `SchedulesMCA`、`Relationship`、`LongTermMemory`、`Mood`、`brain/VillagerBrain`、`Residency`。
  - 取经：身份关系、日程、心情与寻路/职业协调。
- MineColonies：https://github.com/ldtteam/minecolonies （1.21.1 版本）
  - `src/main/java/com/minecolonies/core/entity/ai/workers/` 下存在 CitizenAI、builder/production/service/guard 等功能划分。
  - 取经：职业生产、任务执行、实际资源供需、建造、劳动分工、物流与路径寻址。
- VillAIgence：https://github.com/True-Ruslan/villAIgence （1.21.1 分支）
  - `docs/livingworld/MEMORY.md`、`ACTIONS.md` 和其他文档研究分层记忆、受限行动、安全执行与服务端线程边界。
  - 取经：对话只是表达层；任意模型输出不能直接执行任意代码或服务端命令。
- 三项目当前许可证均显示 GPL-3.0。**本仓库采用自己编写的功能设计，没有复制它们的源代码或素材。若日后借用代码须事前单独检查许可证及合规，而非直接粘贴到 All Rights Reserved 工程。**
- 研究补充：Generative Agents (Park 等, 2023) https://arxiv.org/abs/2304.03442，启发观察、记忆、反思和计划分层；不意味着必须联网调用 LLM。

## 四、真实生活模拟：五层体系
1. **持久的个体**：UUID、住宅、家庭关系、性格、职业、技能、身体状态、需要、财富、所知事件。NBT/世界 SavedData 分离。NPC不能因玩家走远就丢失人生。
2. **环境与社会感知**：游戏时间/天气/光照、资源、威胁、方块、工作点/床/炉/箱、所见所闻（限距离、视觉与声源），不允许全知。
3. **决策**：可配置日程为默认；紧急情况由 Utility 评分打断日程（危险/饥饿/疲惫/社交/工作/声望）。复杂事务由 GOAP 或分层任务进行计划；行为附加成本/收益/风险；设置冷却和回滞防止每 Tick 改主意。
4. **实际行动与世界后果**：服务端权威，依照玩家交互和权限/保护规则挖掘、拾取、耕作、交易、制作、建造、交谈、求助、告密。只有产生实际的资源转移才算真正工作。
5. **叙事与表达**：观察到的事件写入有界记忆（who/what/where/when/impact/source）；观点与信任随着事件变化；对白从状态产生。可选 LLM 只润色对白/选择受控意图，不决定任意世界写操作。

### 一个因果链的标准
农民缺面包 → 去找粮食/买食物 → 市场粮价受供需影响 → 商人库存减少 → 商人改派补货 → 遇怪物延误 → 护卫介入 → 农民与护卫彼此认识/关系增长。
无真实资源转移、工作逻辑或可验证日志者不能宣传已实现该因果链。

## 五、技术架构建议
```
LivingWorldSavedData  — village/market/settlement/rumors/economy + offscreen snapshots
  ├─ ResidentState   — identity, traits, health/needs, home/work, skills, inventory
  ├─ SocialGraph     — NPC-NPC / NPC-player affinities, family, witnessed-event relations
  ├─ EventLedger     — bounded stamped events; visibility, rumor propagation, decay
  ├─ ScheduleBook    — authored data-driven default occupations and festivities
  ├─ UtilityPlanner  — safe interrupt, risk, time/cost, plan hysteresis
  └─ TaskExecutor    — path + block/player/inventory/POI interaction and result receipts
                             |
     PlayerNpcEntity + navigation + animation + skin + network snapshot
```
- **加载区**：真正行动/寻路、详细动画/感知，按活动设置 2～20 tick 的细任务更新与较慢的规划频率。
- **远距离或区块卸载**：不用加载区块模拟寻路；用 SavedData 的事件/资源账本低频推进，回到视野再恢复实体，防复制、错序、无限生产。
- **多人/整合包**：世界修改统一在服务端主线程通过受约束动作执行器，复用权限检查、领地/保护适配、事务式库存变更；禁止凭空复制物品。Sable 攀爬只作为可选导航动作适配，失败时必须有平地方案。
- **性能**：感知局部性、缓存 POI、预算每秒规划次数、远处低频更新、世界保存可中断；200 NPC 按原版整合包进行基准测试，不在未经测量时承诺某 TPS。

## 六、最小可玩样片：一个真正活着的小村庄
先建一小村，铁匠、农民、商人、护卫及两位有不同性格的村民。玩家什么也不做也能看到：
- 睡觉/起床/早餐/工作/休闲，雨天有变化；
- 农民生产真实农产品、商人收购库存、铁匠耗材料制作物品，物品总量守恒；
- NPC 相遇可交谈，依性格和历史形成好恶；不会每天忘记玩家；
- 怪物袭击使 NPC 停下工作，护卫守卫，目击者记录事件，恢复日程；
- 玩家送礼、偷窃、袭击、援助产生**持续有解释的后果**；
- 退出再进入，角色关系、记忆、库存、事务一致；
- 仅对真实加载 NPC 模拟具体路径；远处以低频经济账本表示活动。

### 用例与验收
1. **无人干预**：玩家躲在观察点，连续两昼夜，居民自主执行多种活动且日程在天气影响下有分支。
2. **缺资源**：农民库存耗尽，生产/交易受限，不能靠脚本无中生有。
3. **社交**：连续帮助某 NPC 两天后，该 NPC 对玩家互动变化，并记录原因；重启世界后保持。
4. **紧急**：工作时遇敌，优先逃跑/自卫/报警；事后续做任务，而非永久卡住。
5. **多人**：不同玩家声望独立；一个人犯罪不应等同于所有人犯罪。
6. **空间**：处于不加载区块时不会偷偷加载新区块或绕开玩家领地保护。
7. **性能**：1/20/100/200 NPC 分组测 tick、峰值与平均内存及 save/load 成功率。
8. **正确性**：工作时有真正的方块/容器/物品变更，可回放行为原因的开发诊断日志。

## 七、分阶段执行
- P0（本次）：完成三游戏与开源研究、架构/验收与前期功能差距记录。当前玩家形态 Entity 仅作视觉/世界交互原型，明确**尚非活 NPC**。
- P1：身份与住所/工位、个人需求、日程和雨天分支、持久化事件/关系。改为默认 LIVE，不再默认跟随。
- P2：一个可实测的真实职业闭环（先农场→库存→出售），基于可观察到的资源变动。
- P3：NPC 间关系、记忆、消息传播、威胁反应与事件后果；优化动作连续性。
- P4：村落生活循环、服务端离屏模拟、任务/商店、可选语言/语音和模组适配；游戏内压力测试。

**无编译/客户端/整合包实测的模块分别标注；不可把文档、类文件或聊天描述冒充功能完成。**
