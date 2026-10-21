# WorldStack · 沉浸式传送门垂直世界（第一阶段）

**基线：** Minecraft 1.21.1 / NeoForge 21.1.248 / Java 21。项目主 Mod ID 为 `reme_studio`。

## 能实现什么

借助 Immersive Portals 原生的 `DimStackInfo.apply`，生成四个双向的全局垂直门户面：

```text
   [末地 / End]
        地板
          ↕
        天花板
   [主世界 / Overworld]
        地板
          ↕
        天花板
   [下界 / Nether]
```

拓扑从**顶部到底部**依次是 `minecraft:the_end` → `minecraft:overworld` → `minecraft:the_nether`。
层间 X/Z 比例均为 1:1，默认上下重力，不启用循环、不改变重力方向。渲染、跨维度实体传送与远端区块同步由 Immersive Portals 负责；本模块只负责安全地初始化拓扑。

**重要：第一阶段不是一个完整的自研无缝世界引擎。** 它是可重现的上游兼容集成；天空地形衔接、世界生成定制、Sable 复杂刚体跨界、跨模组兼容仍需后续游戏实测。

## 安装前提

- 安装支持 **NeoForge 1.21.1** 的 Immersive Portals 构建（上游 Mod ID `immersive_portals_core` / `imm_ptl`）。
- 安装该构建所需的 Cloth Config API 等前置依赖。仅装原版 Fabric JAR 不适用。
- 客户端和服务端都必须安装与本整合包兼容的 Immersive Portals。避免混用不同分支的同名 JAR。
- 为了避免弄坏存档，**先复制世界，在独立测试世界使用**，不要在正式存档第一次运行。

## 指令（单人世界允许作弊 / 服务端 OP）

```mcfunction
/reme worldstack status
/reme worldstack setup
```

`status`：显示末地、主世界、下界各自的垂直连接数量（预期 1 / 2 / 1）。

`setup`：仅管理员权限 2 可执行。检查三维度均存在、不存在已建立的维度堆叠、其他维度也没有现存全局门户；仅在完全干净的门户网络上安装。**不会在进入世界时自动安装，也不会删除/覆盖已有门户**。

**已有区块的基岩不会被原地清除。** 安装后 Immersive Portals 接管**新生成区块**的基岩替换规则（默认黑曜石），旧区块仍可能被基岩阻隔。请在新区域测试，或者用创造/旁观模式先验证边界视觉；需要生存可达的入口时，先备份并单独处理旧区块。末地、主世界、下界的天空、洞穴和地形不会因此自动变成物理连续的地形。

## 测试记录/下一验收

- 静态拓扑测试：`WorldStackLayoutTest`，Gradle `test` 与 `build`。
- 运行测试待完成：NeoForge 开发客户端、服务端、真实跨界传送、反向传送、实体/乘骑/载具穿界、旧区块/新块区分、重启保存、整合包与 Sable 联测。
- 完全兼容性未得到实际证明前，勿直接写入用户的正式世界。

## 上游和设计依据

无复制第三方源码，运行时仅调用上游公开类型/方法：

- [Immersive Portals NeoForge CE](https://github.com/UpperMoon0/ImmersivePortals) · 检查提交 `ad2e7ee5d73e4ff78bd0befade4f797265e65939` · Apache-2.0
- [NeoForge 原始移植](https://github.com/iPortalTeam/ImmersivePortalsModForNeo) · 检查提交 `aede93a4865fe4aab5dd2781fb38ab3e5cecd63b`
- [维度堆叠官方说明](https://qouteall.fun/immptl/wiki/Dimension-Stack.html)

此桥接层使用反射加载 `DimStackInfo` 等 API 类，**不会**把上游 Mod 打进本项目 JAR；因此在缺失 Immersive Portals 时，REME 工作室也能启动，相关指令会明确提示缺少依赖。
