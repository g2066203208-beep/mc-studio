# Minecraft 多项目隔离登记（纠正于 2026-10-21）

## 唯一真实位置与职责

| 项目 | 确认过的实际开发位置 | 当前事实 | 本会话的权限边界 |
| --- | --- | --- | --- |
| **WorldSeam（界缝）无缝维度** | `feat/worldseam-standalone` 分支中 `mods/worldseam/`，专用 `.github/workflows/worldseam*.yml` | **独立 Gradle/NeoForge 工程已存在**；`gradle.properties` 的 `mod_version=0.9.0-dev`；[PR #21](https://github.com/g2066203208-beep/mc-studio/pull/21) 当时仍开放、尚未合并 main | **唯一负责** |
| 机械动力 × 航空学 | 当前 `main` 中 `mod/`、`viewer/rpms/` 及 `feat/rpms-*` 功能分支 | 已存在真实系统相关开发，不应与 WorldSeam 的 CI 混合 | **禁止更改** |
| NPC 自主行为 Mod | `feature/living-world-*`、`feature/npc-*`、`feature/player-npc-v1` 等专用分支 | 存在实际分支，但尚未核实某一最终完整独立工程目录 | **禁止更改** |

## 修正之前的错误

2026-10-21 误只检查 `main` 就声称找不到无缝维度，创建了 `projects/seamless-dimensions/` 平行占位工程。**该重复目录已撤销**。真实 WorldSeam 在 `mods/worldseam/`，不要重新初始化，不要丢弃 v0.8/0.9 开发历史。

## 严格隔离

1. 无缝维度只在现有 WorldSeam 的工程、分支、专属 CI、专属 Mod ID `worldseam` 下继续，不接触共享 `mod/` 或 `viewer/rpms/` 和 NPC 分支。
2. 每次更改先核对 WorldSeam 分支 HEAD、PR #21 与变更清单，避免覆盖此前迭代；不要用 main 的骨架状态推断 WorldSeam 开发程度。
3. 0.8 的堆叠编辑器和 0.9 的双维度 Chunk 驻留属于**实现记录**，不等同于可视化无缝已通过验收。
4. 证据分开：编译、服务端 GameTest、真实 GUI 客户端、双维度区块同步、同帧视觉合成。未实测部分明确标记。

## 验收缺口

仍需真实双 LevelRenderer 同帧渲染、接缝裁剪/深度、天空光照/实体/流体/粒子与物理系统完整兼容；详见该分支 `mods/worldseam/docs/DUAL_WORLD_LOADING.md`。
