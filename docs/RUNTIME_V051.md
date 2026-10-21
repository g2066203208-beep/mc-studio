# v0.5.1 runtime failure investigation
- User reports v0.5 does nothing in actual client. Previous v0.5 evidence was GitHub compile and 7 automated server scenarios; **never a human client session**.
- Found natural placement predicate excluded CHUNK_GENERATION, which blocks passive resident candidates in newly generated world chunks. Now allow NATURAL + CHUNK_GENERATION with surface checks.
- Increase ultra-rare biome spawn weight 1 -> 12; retain small local cap for NATURAL. Chunk-generation spread requires separate long-world population cap validation.
- Navigation cooldown previous implementation returned true even when no path and deferred path attempts; now skips replan only if navigation already has an active path.
- New real-server tests assert biome modifier applied to plains, allow chunk-generation reason, and direct navigation moves an NPC across a constructed test lane.
- Demo NPCs still require actual farmland and crafting materials, so a demo spawn is NOT a ready-made working village.
- Added GitHub Actions virtual-display client startup smoke test attempt. Successful client initialization only proves startup, not normal gameplay or modpack compatibility.

## 实测结果：v0.5.1-dev（2026-10-21）
- v0.5 用户报告实际客户端“没用”。代码分析定位到真实缺陷：生物群系 passive chunk-gen spawn 原因被错误拒绝（只有 NATURAL 被接受）；随机刷怪 weight 1 太稀少；路由重算限频在不存在导航路径时仍返回“正在移动”。修复了这些。
- **NeoForge 编译 + Java 测试**：最终源码提交 `e8daa4ab68e18dce4643cbf75dbc19835040990f`；push workflow [39537393137](https://github.com/g2066203208-beep/mc-studio/actions/runs/39537393137) SUCCESS；PR workflow [39537399591](https://github.com/g2066203208-beep/mc-studio/actions/runs/39537399591) SUCCESS。
- **真正 Minecraft 1.21.1 NeoForge 服务端 GameTest**：[39537399622](https://github.com/g2066203208-beep/mc-studio/actions/runs/39537399622) SUCCESS，日志 `10 tests are now running` 与 `All 10 required tests passed :)`。这次不再只看 spawn 初始化：新增检查实际 plains 生物群系注册表、实体真实导航位移、FakePlayer 调用 `/npc demo` 并确认近处确有 6 个实体。测试场景中的导航路线由 GameTest 构建，不证明默认村庄具有日常活动。
- **真实游戏客户端启动尝试**：GitHub Linux Xvfb 模拟显示器启动 `./gradlew runClient`，[39537393170](https://github.com/g2066203208-beep/mc-studio/actions/runs/39537393170) SUCCESS。虽然测试到 180s 由 timeout 主动终止，但日志已进入 Render thread，记录 `Reloading ResourceManager: vanilla, mod_resources, mod/reme_studio, mod/neoforge`；未见脚本筛查的 fatal/crash。**这不是操作登录 HMCL，也没有进入实际单人生存世界或截图证明 NPC 表现**。
- artifact 12317652189 ZIP 解出 `reme_studio-0.5.1-dev.jar`，实际核查大小 **124714 bytes**，SHA-256 `aa61ce2e079b08e395cddbc4e54fc7213a7a51c37483e03dbc8b3f518dd1ac88`；包含 renderer、NPC 类、GameTest 类、`natural_residents.json` 和元数据。
- 2026-10-21 尚未通过用户真实整合包/新世界自然生成概率/日夜家庭/长期 TPS 对照测试。旧的 7/7 GameTest 不等于这些项目已可玩；自然刷怪依旧会受原版生物上限和既有区块影响；应进入**新生成的自然地形区块**验证。

## 玩家端报错优先级（不要再次仅给模糊命令）
1. 输入 `/npc demo` 提示 Unknown command：首先确认安装文件是这个 JAR，Minecraft 是 1.21.1，NeoForge >=21.1.248，且没有另一份 `reme_studio*.jar`；在实际 HMCL 实例 `logs/latest.log` 中搜索 `reme_studio`。
2. 命令已接受但看不到人：此版 GameTest 验证命令在服务器端确实生成了实体。请直接发送 `logs/latest.log` 与实体附近截图检查客户端渲染/模组冲突/世界加载。
3. 人能看见却原地站着：`/npc demo` 不会建农田/工作台/住宅！农民需要成熟作物，面包师需要真实麦子及工作台；这一版只有有限的社区漫步，不是已经完成《星露谷物语》质量的完整日程。能移动的 GameTest 只验证可寻路，不能保证所有职业空场景一直移动。
4. 野外没自然 NPC：旧区块不会重新执行 CHUNK_GENERATION。当前修复要求**装 Mod 后新探索/新世界**，并被动生物刷怪上限制约；不要靠原来已经探索完的平原原地等待验证。
