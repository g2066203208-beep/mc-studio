# WorldSeam · 界缝

**独立的 Minecraft 跨维度世界底层 Mod**（仍处实验阶段）。

- Minecraft：1.21.1
- 加载器：NeoForge 21.1.248；Java 21
- **Mod ID：`worldseam`**
- Java 包：`dev.reme.worldseam`
- 独立 Gradle 工程：`mods/worldseam/`
- 构建产物：`mods/worldseam/build/libs/worldseam-0.5.0-dev.jar`
- 依赖：Minecraft 与 NeoForge；**不依赖 PEAK 攀爬，也不依赖 Immersive Portals**。

## 为什么和 PEAK 完全独立

WorldSeam 管维度边界与世界区块/实体流。PEAK 管抓握、攀爬和运动。两个 Mod 不复用入口类、包命名、资源命名空间、Mod ID、项目构建或 JAR。这个目录的源码、资源和 CI 只用于 WorldSeam。攀爬工程仍在 `feat/peak-*` 功能分支，本次没有修改。

## 自己构建

```sh
cd mods/worldseam
./gradlew test build
./gradlew runGameTestServer
```

CI：`.github/workflows/worldseam.yml` 单独运行这些任务，同时检查 JAR 不含 `dev/reme/mcstudio` 或任何 `climb`/`peak` 包。工作室通用 `mod/` 的构建不会打包 WorldSeam。

## 测试世界专用指令

```mcfunction
/worldseam status
/worldseam enable
/worldseam disable
/worldseam probe
/worldseam terrain enable
/worldseam terrain disable
```

默认关闭。新地形接缝额外开关也默认关闭，若开启只处理未来新生成的区块。正式存档请勿直接测试。

## 当前能力边界

自主空间拓扑/服务端跨维实体迁移、原生 16³ 区块段调色板编码、远端区块段网络协议与客户端实验性区块代理渲染已迁移。**还没实现**完整多 ClientLevel、真正独立远端 GPU Chunk Mesh、流体/实体/光照全要素以及无黑屏镜头跨界。GameTest 服务端通过也不能代替客户端实测。这是自研引擎的独立开发版，不是完成的无缝世界。

[历史引擎源代码及研发说明](https://github.com/g2066203208-beep/mc-studio/pull/18)（旧 PR 不应合并）；最新独立 WorldSeam CI 才是新命名、新 Mod ID 的有效构建证据。
