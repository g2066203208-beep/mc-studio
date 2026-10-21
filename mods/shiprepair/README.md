# ShipRepair / 飞船结构修复站 v0.1 Alpha

**独立 Minecraft Mod**：MC 1.21.1、NeoForge 21.1.248、Java21，依赖 Sable 2.0.6；不依赖 PEAK/WorldSeam/NPC。

## 玩法

1. 从创造模式功能方块栏获取「飞船结构修复站」，放进飞船中；用 Sable 将飞船组装成物理 SubLevel。
2. 空手右键首次点击：扫描并保存此 Sable 船体全部已加载 plot 区块的非空气方块、方块属性/朝向、修复站相对坐标、SubLevel UUID 和拥有者。记录完成后聊天提示。
3. 破坏或修改船体方块，或往船体加乱七八糟的普通方块，再空手右键：先扫描差异，分批还原。每 tick 最多修复 96 块，先补船壳，再删除新增普通方块。船体飞行/旋转后使用的是局部 plot 坐标，而非世界坐标。
4. Shift+空手右键：船主重新录制新的完整快照。蓝本持久化在此方块的 NBT 中。只有录制者/OP 可以维修或重录。

## 已知限制

- 最多扫描 512 个已加载船体区块、250000 个非空气方块，每 tick 扫描 2 区块。Sable 结构拆分/卸载、UUID 不一致立即终止，**不会在普通主世界写方块**。
- **BlockState**（种类/朝向/属性）可以修复；**物品库存、机器能量和 BlockEntity NBT 暂不快照**，避免无限复制。后来放置的箱子、其他方块实体及其他修复站不会被自动破坏，也不会覆盖原位新机器。
- 修复站被彻底炸掉、整个飞船解体或者保存的 chunk 被卸载后无法直接一键恢复；需下一版做独立 SavedData 救援。批量维修不是全局原子事务；多人/Sable高速运动需真实整合包测试。
- 第一次保存时不要同时改造结构，否则采样跨 tick 可能记录到混合版本。正式存档必须先备份。

## 构建和参考

独立 Gradle 工程目录 mods/shiprepair，使用仓库共享 Gradle 启动器：

    bash mod/gradlew -p mods/shiprepair --no-daemon build

构建产物 mods/shiprepair/build/libs/shiprepair-*.jar。自动 CI 为 .github/workflows/shiprepair.yml。
研究参考：https://github.com/ryanhcode/sable（ServerSubLevel、LevelPlot、BlockEntitySubLevelActor）与 NeoForge BlockEntity 持久化文档；未复制上游源码或资产。
