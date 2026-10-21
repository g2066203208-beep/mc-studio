# REME NPC 0.6.0 → 1.0：Minecraft 风格皮肤、动作表演、智慧文明和高性能总导演规范

> 2026-10-21 深入审计。项目 **MC Java 1.21.1 + NeoForge 21.1.248 + Java 21**；本规范是任务定义，**不是所有功能均已做完**。分支 `feature/npc-animation-art-pipeline-v060` 继承目前的 v0.5.9 道路/聚落/商队系统，草稿 PR；不合并 main，不动其他项目（无缝维度、航空学托管网页）。
> 目标不是无意义地塞动作或随机盖城市，而是让玩家看到 *一个人→一群人→有道路和职业互相依赖的村庄→市场、城镇、城堡* 的真实、长期、可验证 Minecraft 世界。

## 一、审计结论：以前六张皮肤不及格，必须重做，不能谎称“专业”

从已打包 `reme_studio-0.5.9-dev.jar` 逐张读取 64×64 RGBA 纹理：
- `green_farmer, blue_farmer, brown_trader, formal_trader`：**六个第二层部位全部 0 个不透明像素**。
- `field_explorer, teal_explorer`：只有头部 Hat 外层共 32 个非透明像素，衣服外套、左右袖、左右裤脚外层全部为 0。
- 六张表面都是基本平贴，虽然文件尺寸与基本 UV 对齐、色彩有不同主题，远未形成专业“有体块的 MC 像素皮肤”。早期脚本 `scripts/check-npc-skins.py` 只检查尺寸、SHA 与是否打进 JAR，**不能说明层次/绘画质量**。
- 当前渲染使用 `MobRenderer<PlayerNpcEntity,PlayerModel<PlayerNpcEntity>>`；**并非** `AbstractClientPlayer`。原生 `PlayerModel` 能承载基础+外层 UV 和原版膨胀面，但已安装的 `3D Skin Layers`（玩家 mixin）**不会自动给我们的 NPC 外层逐像素挤出深度**，需要原生模型层或兼容适配。

## 二、64×64 正确画法：体块、UV、双层、三维、Steve/Alex

**原版第二层是什么**：UV 在标准 64×64 画布上有独立的 Hat/Helmet（头）、Jacket（身体）、Left/Right Sleeve（两臂）、Left/Right Pants（双腿）。它是覆盖在基本皮肤外稍微胀大、可以透明并分片遮挡的**服饰/发梢形状**，而不是第二个人体、不能完全铺满、不该默认遮掉精心设计的脸和衣服。第三方 3D Skin Layers 的“逐像素 3D”则是**把非透明外层像素各自生成有厚度的像素盒或表面**，并在靠近时显示，距离远可退回原版 2D。二者不是一回事。

MC 审美约束：
1. 固定 **8×8×8 头、8×12×4 躯干、4×12×4 四肢**（经典标准 Steve 臂宽 4px）和两个小黑眼点/眉影；Slim/Alex 可作为未来臂宽 3px 单独建立模型，**不可把 4px 纹理拉歪成 3px**。不要写实头身比例、胸肌、描边插画、圆润动漫脸、128×128 模糊渐变。
2. 每个职业画的首先是**身份轮廓**，再是像素块的材料差异：农民亚麻帽边+卷袖/围裙；探险者发梢、肩部背带+皮革护肩；商人立领、扣襟、夹克衣摆、袖口；守卫头巾/护臂/斗篷边；矿工头灯/背带/防尘领（不添加真实发光除非有专门发光实现）。至少 1 个**非对称的识别特征**，但保留 vanilla 几何和可信 MC 配色。
3. **主色 3–5 色 + 结构阴影 2–3 阶**，主色与肤色/头发区分清楚；光从左上，避免 40 种接近的噪点，头部/胸口高对比、腿部低细节；轮廓从前/后/侧及远景 16–32px 屏占比可读。6 人不是换皮肤 Hue 的复制体。
4. 第二层的有效非透明面积**约 8%–35% 对应职业必要覆盖部位**，其余保留透明空洞：如 1 px 帽檐突出、额头几簇头发、外套敞开边、袖口/立领、背带连接和裤脚护膝；**上下相邻四面 UV 要连续**。不能只在一个面画半截、背面断层、腿内侧粘连、身体弯曲时悬空。
5. 透明像素 RGB 清成 0，以降低 mipmapping 边缘白边；真正 alpha=0 透明，不要半透明灰雾假像素；禁止套别人的受版权保护皮肤/直接复制三维材质。
6. 近景（0–12 方块）NPC 自研逐像素 3D 外层、保持 CPU 共享 mesh 按 skin variant 缓存；中景 12–32 退回原版 2D overlay；远景 >32 仅默认骨骼/基础层。以上是**实现目标，不是当前功能**，须单测遮挡、尺寸、透视、胳膊旋转、装备、阴影。每个部位 6 个面正确 UV，防透视漂浮。盔甲/背部物品深度优先级严格控制。
7. 作品验收：**必须输出原画 64×64 PNG + 五视角渲染 + 角色动作四连帧**，逐部位统计 overlay alpha，至少一名原创人物先出艺术总监审查，再推广至六职业；实际 MC 启动/对比 screenshot 证明存在，不能用 AI 画一张渲染插图冒充游戏纹理。

### 皮肤美术总 Prompt（供原创像素美术师/专用工具）

> 设计一位 [职业:农民/矿工/商人/侦察兵/守卫/运输员] 的**原创 Minecraft Java Edition 标准 64×64 双层 Steve 风格皮肤**。最终必须是严格像素级完整 UV PNG、alpha 通道、内层六体块与外层 hat/jacket/袖/裤附加层分别对齐；没有抗锯齿、渐变、自动插值。保持原版 8×8×8 方头和四肢比例，性格为 [标签]，地区为 [温带边境聚落]，工作磨损仅是少量色块不是脏噪点。识别焦点是 [2–3 个部位]，主配色 [3–5 色]、光照层次 [亮/本色/阴影]。Hat 外层只画头发或帽檐的局部，不要把脸盖住；Jacket 外层画局部敞开襟边/背带，Sleeve 和 Pants 的局部厚度需要从斜45°角可见，四面纹理边界完全连续；透明区域必须 alpha=0。渲染展示：正前、背后、左右侧、3/4，摆臂/举物/行走四帧。禁止改成卡通插画或高模、禁止把第二层放到 UV 第一层、禁止侵用市场商用皮肤。质检：每张至少 4 个不同外层部位有清晰合理的局部覆盖，且两条手臂旋转时无断裂/悬浮，必须是真游戏里可装的 64×64 PNG 而非单张概念图。

## 三、动画技术选型与真实兼容

来源与检查：
- 原生模型 `PlayerNpcRenderer.java` 是 MobRenderer + PlayerModel，已有原版步行动作（非真人级）。依靠 `setVisualPose()` 服务器每4tick按动作事实选一次，**只在变化时网络同步一个枚举值**。客户端 `LivingNpcPlayerModel.setupAnim()` 先让 MC 原版处理基础动作，再加入温和的呼吸、扫视、招呼、工具作业、抱货、警惕、受惊、低头休息动画；最后**重新复制帽、衣、袖、裤的变换**，保证 double layer 跟随同一枢轴。这是 P0 基础，动画是否达到自然级别必须实机多帧人工检查。
- [KosmX Player Animator 1.21 分支 README](https://github.com/KosmX/minecraftPlayerAnimator/blob/1.21/README.md) 明言不再维护并建议 PAL；Player Animator 可关键帧、混合，但目标是**玩家模型**。`player-animation-lib-forge:2.0.4+1.21.1` 版本可见于 GitHub Packages。**直接加依赖不会给自定义 PathfinderMob NPC 生效**。
- [PAL 官方概览](https://docs.zigythebird.com/pal/intro/) 与 [PAL 1.21.1 NeoForge 真版本](https://www.curseforge.com/minecraft/mc-mods/player-animation-library/files/7611367)：支持 Blockbench JSON、过渡/优先级/局部骨骼；但它的 Avatar 目标不等于我们的 PlayerNpcEntity，必须先做 **实体兼容 Spike**，不可未经验证引入成强制依赖。[PAL 官方实体说明](https://docs.zigythebird.com/pal/features/mannequins/) 请注意不同 MC 版本 Avatar API 区别。
- [Actions & Stuff 官方 Marketplace](https://www.minecraft.net/en-us/marketplace/pdp/oreville-studios/actions--stuff-1.12/61c7a786-d7ad-49e0-a710-817121cd9795)：学习一/三人称动作意图、重量感、过渡、身体重心、骨架保持方块化。**Bedrock 内容不直接兼容 Java，不导出或复制付费资产**。
- [Fresh Animations 官方 Modrinth](https://modrinth.com/resourcepack/fresh-animations)：研究 vanilla-faithful 的小幅动作和头部注意力，主要针对生物，依赖 CEM（EMF/ETF 或 OptiFine）；**不是直接给我们写好的玩家动画**。不能在不经允许的情况下复制其表达式、模型或付费内容。
- [3D Skin Layers 源码和技术说明](https://github.com/tr7zw/3d-skin-layers) 做外层逐像素厚度，并在 >12 方块回退2D；其仓库为 **tr7zw Protective License**，**不可直接拷贝实现代码到我们的 mod**。独立重写 UV 挤出算法，在我们的 PlayerModel 六个部分以相同矩阵跟随。

动作制作遵循动画师的 Blocking → Spline（不能改变方块几何轮廓）→ Polish：
- **Idle**：非完全静止，低振幅呼吸、头部注意力，随机相位取自 UUID 稳定 hash；高频抖动/眼球滚动/连续转圈禁止。
- **Walk**：vanilla 双腿反相是基础；速度影响步幅，脚掌不穿固体、不会走月球步；站立/转身/起步/停下具有 4–8帧过渡。
- **Run / Fear**：重心前移、短促手摆，实际 AI 在逃跑才生效，不允许只是见到敌人就伪装逃跑。
- **Mine / Build**：工作时上肢 0.5–1.2rad 有准备→蓄力→挥动→回收，工具实际破坏发生时才有接触爆发；不允许 NPC 挖矿未完成却看似瞬间挖完。
- **Courier**：带有真实背包小麦才能抱货，空包恢复自由手臂，背包/手持物不得悬空；实体到达箱子才执行卸货状态。
- **Talk**：对话目标可见才定向头眼，左/右手轮替少量手势；社交活动不能永远站着挥手。
- **Combat/Guard**：目标存在才警戒，反击、受击/后退、恢复状态优先，不能覆盖出手判定。
- **Sleep**：当前只是疲惫低头，**不能称真实躺床**；要实现躺床必须服务器确认床占有和实体姿态与碰撞。
- 动画状态优先级 `AFRAID > GUARD > WORK > CARRY > REST > SOCIALIZE > IDLE`；以同步枚举推送，客户端接近/远离 LOD 各自调速，不每帧传骨骼。

## 四、高智能 NPC：不假冒聊天机器人，也不无限烧 TPS

### 游戏因果的“真人感”来自四层

1. **感知**：真实相邻区块、视线、声音事件、原版容器、同阵营消息/谣言、道路热度；不得读取没加载区块或全地图箱子、没有目击则不能自称知道是谁抢劫。
2. **个人**：有职业技能、作息/疲劳/饥饿、短长期偏好、个人事件+信任图、住家/工作地、装备/健康、过去工作记忆；把 100%随机游荡改成目的性往返，偶遇者互让行/搭话。
3. **计划**：可中断层级 GOAP/Utility + 行为树，不是每 tick 对一百种目标全算。任务状态 `IDLE → PLANNING → TRAVELLING → INTERACTING → VERIFYING → COMPLETE / SUSPENDED / FAILED`；事件优先级危险 > 生存 > 货物安全 > 工作 > 社交 > 随机闲逛；每个 action 有预算、超时、资源预约、重试策略和错误理由。真正路径被堵时重找路/报告无法通行，不会瞬移、挖别人家、强制加载远处维度。
4. **社会涌现**：货物有限、供求与收益、村庄的家庭/床位/农田/储蓄真实存在，村民自主形成近邻组织；两个聚落资源差异产生商队，安全事件改变选路、声望影响贸易，守卫决定追捕/收缴赃物。

**LLM 可选**：只生成安全的文字表达/计划建议，不能每 NPC 每 tick 远程请求，不能绕过原版玩家行为/权限核对。没有 LLM 也能完整生产、运输、睡觉与交易。

### 性能门槛（目标，非既有基准）

- 20/50/100/200 NPC，单人/多人 2 小时样本；明确方块规模、渲染距离、机器 CPU/GPU、堆大小和 modpack。采集 **p50/p95 MSPT, TPS, heap/GC, pathfinding calls/10s, AI decision calls, chunk loads, traffic SavedData size**, 另外 Java Flight Recorder/spark 用于找热点；**目标** 200 NPC 时 p95 MSPT < 50ms、渲染 > 60 FPS（基准硬件须声明），实际达不到就标失败而非隐瞒。
- **预算式 LOD**：0–16 方块 tick 精细互动；16–48 每5–20 ticks规划；远距已加载 NPC 每80–240 ticks更新社交职业；卸载时只记录事件账本不跑虚假物理。所有重体力任务批量错相 `entityId % interval`，最多 M 个寻路重算/每tick，超出排队，禁止 200 NPC 每tick各扫半径12+天空+库存。
- SavedData 交通 4096 tiles上限不能每tick做完整序列化；实体 36-slot背包与可追踪近邻工作任务必须有严格缓存与过期。路线优先维度/区块层级图，再局部细路；低热度历史退潮，否则热度满地图永久污染。
- 渲染：顶点网格每 skin variant 共享 3D mesh 缓存；近景有表演/3D外层，中景 vanilla 2D 覆盖，远景低细节；玩家离开 FOV 暂停昂贵表演，实体关节状态只同步事件变化。

## 五、聚落成长、道路、城镇、城堡：严格资源与建筑验收

| 阶段 | 物理需求门槛 | 城市玩法因果 | **目前状态** |
|---|---|---|---|
| 3–5 家庭聚落 | 3+ 真住址、互相关系、一定食物、邻里步行轨迹 | 每日会合、周围有短路 | **已有简单人口+阵营识别，不代表建好村庄** |
| 6–20 户自然村 | 真正床/住宅空间、田地、储物箱、工匠、食物盈余 | 建路、仓储、市场、治安、交换 | 未实现资源/住房硬验收 |
| 20–60 户集镇 | 建筑蓝图、公共工坊、税账/工价、跨村运输 | 集市/护送/旅宿、劫匪、执法、迁徙 | 未实现 |
| 60+ 户城市 | 人口/食物/物流/税收/外城路图/城墙经费 | 区划、分工、拥堵、经济反馈 | 未实现 |
| 城堡/要塞 | 区域冲突/军事需要、建设材料/工时、补给与守卫 | 护城河、城门、军营、警戒与防御 | 未实现，不应作为普通城镇自动长出 |

道路要按交通热度、地形坡度/河流、合法用地、施工材料、耐久和维护成本形成“土径→碎石道→石砖街”；不侵占玩家领地、不踩农田、不穿机器，不自动跨维度强载区块。单纯三人扎堆不自动造城堡。村庄建房优先向玩家开放原版 BlockItem 的建筑工人动作，以**真实材料账本**和每块权限成功回执逐块施工；否则暂停并提出所缺材料。

## 六、逐阶段产出与专业 QA

**S0 — 可靠修复**：聚落真实客户端 `/npc settlement` 要接受 >=3 户，不是写死“恰好3”；多个不同阵营邻居也能正确枚举有效聚落；挖矿截图玩家站远，圆石真实进入 NPC 而不是 Dev；随机 5 种地形/多个种子验证。真实 GameTests + GUI 硬验收至少连续3轮。

**S1 — 动画最小切片 0.6.0**（本分支已开始）：NPC_NATIVE `NpcPose` enum/同步、`NpcPoseSelection` server 低频动作、`LivingNpcPlayerModel` 客户端方块人体表演、保持 outer layers 骨骼；GameTest 覆盖威胁/职业优先级；真实客户端录连续帧验证角色轮廓。**不得称已达到 Action & Stuff。**

**S2 — 美术六职业重做**：先产一名矿工与一名农民 原创全 UV 双层；UV/alpha 静态审计，五视角截图，艺术审查，若不达标反复返工，不拿几张 Hue 替代原创；随后六种衣装差异 + 冒险状态可穿卸。

**S3 — NPC 3D 外层**：自行编写客户端逐像素 box mesh（基于法律许可允许的 UV 事实重新写），每部位旋转随 model part，距离 LOD，盔甲遮挡，多模组碰撞测试。不能简单安装 tr7zw 玩家专用模块宣称支持 NPC。

**S4 — Blockbench/关键帧动画**：建立动作白皮书 `idle/listen/talk_wave/walk/run/stop/mine/harvest/carry/put_down/open_chest/block/hurt/flee/sleep` 关键帧文件+分层/过渡/变速/状态优先级，做 PAL兼容 Spike；PAL 无法直接驱动 PathfinderMob 时继续 NPC-native 动画管线，不改变实体类别作弊。

**S5 — 有经济条件的文明**：近邻家庭实际住所/资金/储粮、职业任务链、真施工、区域运货、交易谈判、威胁/护卫/法庭、旅行休息与恢复；完整验收 7+ MC 游戏日、存档恢复、物资守恒和领地保护。

**S6 — 性能与整合包**：明确 200 NPC p95 MSPT 门槛、装载/离屏事件成本、各种 NeoForge Mod 的权限与物品系统、用户 HMCL 222 Mod 清单与实际隔离存档；一次测试不能推导万能兼容性。

## 七、可交给 GPT/Codex 的专业总执行 Prompt

> **角色**：你是 Minecraft 1.21.1 / NeoForge 21.1.248 / Java 21 高级玩法引擎开发者、系统 AI 负责人、Minecraft 角色像素美术总监、动画导演、游戏性能工程师和 QA Lead。仓库 `g2066203208-beep/mc-studio`。**任务不是写另一个计划就停下，而是按优先级持续提交真实源码并证明在游戏可用**。本项目唯一范围是 NPC 活世界；无缝维度/航空学网页属于独立项目，不得混入。
>
> **准备**：先审阅 `AGENTS.md`, `docs/HANDOFF.md`, `docs/EMERGENT_WORLD_V059.md`, `docs/NPC_PLAYER_PARITY_MASTER_PLAN.md` 与本计划，追踪当前草稿 PR、源码 HEAD 与真实 Build/GameTest/GUI log。读取原版 `PlayerModel`, `HumanoidModel`, `PlayerNpcRenderer`, `PlayerNpcEntity`, `LivingWorldActions`, `RoadNavigator`, `FootTrafficData`，再查 Player Animator/PAL/3D Skin Layers/Open source许可。对比头、躯干、四肢、帽层、夹克、袖、裤的真实 64×64 UV，保留原版大小和命名，任何 Blender 高模化、复制付费资源包、作假客户端截图都禁止。
>
> **执行**：先修可复现失败，GameTest 实测又不能绕过断言；随后 0.6.0 同步 NPC 7 个有事实依据的姿态，客户端原版骨骼温和添加表演且第二层跟随；每个阶段先小 PR 编译/测试。皮肤六人全部从真实原创 64×64 像素绘制，第二层至少覆盖4个合理部位但仍大部分透明；分别呈五视角、动作转身检查、透明边缘检验并展示人看得到的真实截图；之后 NPC-native 3D skin mesh 缓存及 12m+ LOD，测试盔甲。单独用验证过的 PAL NeoForge 1.21.1 版本做可丢弃兼容 Spike，只有对 `PlayerNpcEntity` 实际有效才集成，否则使用自研状态骨骼，不要调用不存在的 player-only hook。
>
> **高智能**：真正 percept→bounded memory→utility planner→continuous job lifecycle→player-safe world interaction→server receipt。每次巡逻、采矿、种田、取货、护送、抢劫必须与世界存在的物品/方块/实体耦合，尊重 FakePlayer 的 NeoForge 权限/取消、默认不开世界改造。让超过 3 个自然成员组成社会，增长到村庄/城市要有已建住宅/床/物资/职业/需求的硬条件与长时间交通反馈；城堡需要防御需要和成本，不能凭空长建筑。旅行、夜宿、物品实权、失窃与护卫必须可以观察并保存。
>
> **测试与文档**：每次改动建立独立 branch、draft PR，不 merge 主线；Minecraft 实际 GameTestServer 定量验证每个因果链（包括失败/撤销/存档/区块未加载/权限取消/物资守恒），Xvfb/Openbox 真 Minecraft NeoForge 客户端进入世界截图＋聊天 NBT 断言，连续多次执行；再做 20/50/100/200 NPC Benchmark（p50/p95 MSPT/TPS/heap）并按独立硬件说明。不能假造截图、假装所有系统实现、把编译成功当整合包通过，也不得承诺后续自动在后台持续执行。每次给 GitHub PR/commits/Actions、真实可用 JAR 和明确下一步未完成项。
