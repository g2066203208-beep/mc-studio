# 玩家型 NPC Mod — v0.1 开发记录

目标：从服务端实体出发实现玩家型 NPC，不把视觉假人或虚假的 ServerPlayer 账号当成完整玩家替代。

## 本阶段实际已实现的代码
- 注册实体 reme_studio:player_npc；使用原版玩家身体模型与 Steve 默认纹理、手持物层、基础行走和攻击动画。
- /npc spawn [名字] 创建归属玩家的 NPC；/npc follow, stay, wander, guard, info 控制附近归属自己的 NPC。
- 寻路跟随、随机巡游、遇水漂浮、敌对生物目标选择、基础近战和受伤。
- 36 格独立背包，右键每次放入一件，潜行空手右键打开 4 排背包 GUI；潜行右键把手持 1 件装备到主手，旧主手物品归入 NPC 背包。
- 空手右键切换跟随/待命；背包、归属、模式通过实体 NBT 与世界一起保存。
- 命令最近 16 格内寻归属 NPC；创建需指令权限 2 级。服务端是 NPC 逻辑权威。

## 严格不宣称已完成
- NPC 不是 ServerPlayer，不会自动获得所有其它 Mod 的玩家特有能力（包括攀爬 Mod）。
- 尚未实现用工具挖掘、放置方块、物品使用、盔甲/副手、皮肤系统、工作台、熔炉、红石交互、生命/饥饿完全同步、睡觉、传送门、经验、死亡后背包掉落、原生玩家成就及配方。
- 未在真实游戏客户端、独立服务器和 222 Mod 整合包内实测。需要单独验收；Gradle BUILD 成功不等于这些项目已通过。
- 目前 NPC 死亡时背包物品调用原版容器掉落流程；仍请勿在未经测试时交付贵重物品。

## 构建验证（2026-10-20）
- 首轮 Github Actions 39453134471: FAIL，原因是 NeoForge 21.1.248 不支持旧文档的 `DeferredRegister.Entities` API。
- 改为标准 `DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, modid)`。
- Actions 39453494702: SUCCESS；Actions 39453501071: SUCCESS。
- 对应提交：`b51773ecebda40705fc54009baa40aee80f34e0e`。
- ZIP artifact ID：`12279378797`，JAR：`reme_studio-0.1.0-dev.jar`。
- 独立服务器、开发客户端、整合包、世界保存读取尚未做真实运行验证。

## 验收操作
1. 在 1.21.1 NeoForge 开发世界加载 Mod，在有指令权限时执行 /npc spawn 小雷。
2. 空手右键确认 FOLLOW/STAY 切换；/npc wander、/npc guard、/npc follow、/npc info。
3. 右键放入普通物品，潜行右键装备一件物品；退出并重进世界，确认模式、持物和内容持续保存。
4. 与普通敌对生物交战，观察行走、碰撞、攻击及物品外观。
5. 封闭世界测试后，再在单独整合包副本测试；严禁未经备份部署到真实存档。

## 下一阶段架构
行为任务层 + 物理导航层 + PlayerAction 执行器（借助受权限控制的 fake player 模拟真实服务器交互）+
 玩家状态/装备/容器组件 + UI/网络同步 + 动作适配层。只有用实机实测证明的功能才能标记为“复刻成功”。
