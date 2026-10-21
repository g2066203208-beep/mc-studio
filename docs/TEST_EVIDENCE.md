# Native Seam 0.5.0-dev · 自动测试证据

日期：2026-10-21。环境：Minecraft 1.21.1 / NeoForge 21.1.248 / Java 21。

## 已验证

- **构建、单元测试、JAR**：[GitHub Actions Build 39528162343](https://github.com/g2066203208-beep/mc-studio/actions/runs/39528162343)，结果 `success`。`compileJava`、`compileTestJava`、`test`、`build` 与上传 Artifacts 成功。
- **JAR**：`reme_studio-0.5.0-dev.jar`，SHA-256 `2fefcf36125da5a9ceffb8b62c66b25f0fd16776b0ac0de7faa378076e502d17`，下载：[Artifact 12314305534](https://github.com/g2066203208-beep/mc-studio/actions/runs/39528162343/artifacts/12314305534)。
- **实际 NeoForge GameTest 服务器启动**：[GameTest 39528162363](https://github.com/g2066203208-beep/mc-studio/actions/runs/39528162363)，`runGameTestServer` 成功。日志包括：
  - `Started game test server`
  - `Running test batch 'reme_studio.native_seam:0' (1 tests)...`
  - `1 GAME TESTS COMPLETE`
  - `All 1 required tests passed :)`
  - 保存 `overworld`、`the_nether`、`the_end` 后正常停止
- **GameTest 范围**：检查三个原版维度存在、双向接缝拓扑正确、Nether 逻辑屋顶 Y=128、跨界及新地形修改默认禁用。实际测试类：`SeamRuntimeGameTests.java`。
- **单元测试范围**：16³ Palette 编解码与非法包边界、区块缓存修订号/隔离/清除/LRU/保活、空间边界往返、Nether 屋顶与新生成区块接缝范围。

## 不得写成已验证的项目

- 没有启动**图形 Minecraft 客户端**，没有录屏或像素级穿越验证。
- GameTest 服务器不能替代普通专用服务器长时间运行、多玩家联机或整合包测试。
- 没有证明原版 `ClientboundRespawnPacket` 所触发的客户端单世界重建已消除。
- 没有完成真正完整的目标维度 Chunk Mesh、流体、方块实体、光照引擎、跨维实体与视锥/深度缓冲整合。
- 未在 TerraFirma Rebirth 2 / Sable 上做集成性能测试。
- 没有测量 TPS/FPS 的前后差异。

结论：**通过自动化编译、单元测试与真实 GameTest 服务端初始化**；目前仍然是独立世界同步与近场渲染原型，不能宣称完全无缝上下世界已完成。
