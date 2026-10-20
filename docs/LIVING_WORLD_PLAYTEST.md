# 活世界 NPC v0.2 实机测试说明

基线：Minecraft 1.21.1 / NeoForge 21.1.248 / Java 21。

已编码：默认 LIVE、自主日程/天气/饥饿/疲劳/社交、危险逃跑、邻居见面双向记录、食物从背包消耗、回家休息。农民只采收真实成熟的原版小麦/胡萝卜/马铃薯/甜菜：先计算真实掉落、模拟背包容量与扣除真实种子，成功后重植和转移库存。不加载未激活区块寻找农田。背包、需求、住址、工作锚点、48 条个人记忆、最多 128 份关系数据保存到实体 NBT。

命令：/npc demo 生成 6 名居民（2 名农民、1 名面包师、3 名普通居民），不主动改变地形；/npc info 显示附近自己居民的需求、意图、最近行为、信任；/npc sethome、/npc setwork 指定附近自己居民的位置；/npc live 让其恢复自主生活；/npc farmer 或 /npc resident 切换职业。旧版跟随/待命/巡逻命令仍存在。

测试：在独立备份测试存档先建已有成熟作物的农田，站在农田中央并在有权限时执行 /npc demo。检查白天农民采收和重种是否真实改变方块与背包。给 NPC 放入真正可食用的食物，观察消费。夜间返回 home 锚点；如果下雨，会尝试返回 home（不保证真实屋顶）；下午 NPC 会试着相遇。退出重进验证需求、关系和库存是否持续。

限制：居民尚无真实房屋/床领用；仅农民有真实生产任务；面包师可用 3 个真实小麦在工作台旁制作面包，与农民以 1 面包交换 3 小麦；其余三名普通居民暂无生产职业；没有实际商店/村庄经济/远处离线模拟；不能把对话和规划输出当成已完成的交易、生活、完整 NPC。尚未进行客户端/独立服务端/222 Mod 整合包测试。世界方块编辑没有领地保护 API 适配，禁止用含有贵重建筑或真实存档进行实验。


## 2026-10-21 build verification

- CI push run 39457871574: SUCCESS (Java 21 standalone planner tests + Gradle build + JAR upload).
- CI PR run 39457879491: SUCCESS.
- Verified commit b27de8cff1f4b7f222255d5adea94038ea4d0634, artifact 12281113336.
- The artifact was compiled while the internal mod_version still read 0.1.0-dev; version bump to 0.2.0-dev is a follow-up build-only metadata change.
- No Minecraft client or dedicated-server game-world playtest yet.


## 最终开发版验证
- mod_version=0.2.0-dev，提交 4a8b87d25c56ce7f01639265eaea6afd196a82ea。
- push CI: https://github.com/g2066203208-beep/mc-studio/actions/runs/39458311637 SUCCESS。
- PR CI: https://github.com/g2066203208-beep/mc-studio/actions/runs/39458319744 SUCCESS。
- Artifacts: 12281915935 from successful PR CI (see run Artifacts). 仅证明 JAR 构建与上传成功。
