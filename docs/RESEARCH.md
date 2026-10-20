# 开源研究路线

2026-10-20 已核实以下官方文档与仓库入口可访问。这里是研究索引，不表示已完成全部源码阅读。

| 来源 | 研究用途 |
|---|---|
| https://docs.neoforged.net/docs/1.21.1/gettingstarted/ | Java 21、开发环境、构建和运行 |
| https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle | 当前工作台模板 |
| https://github.com/TerraFirmaCraft/TerraFirmaCraft | 群峦传说体系、数据和附属 Mod 接口 |
| https://github.com/Creators-of-Create/Create | 机械动力架构、动力系统与交互实现 |
| https://github.com/kube-mods/kubejs | 整合包脚本、配方与事件；旧 KubeJS-Mods 地址会重定向 |
| https://github.com/lucko/spark | 性能采样与瓶颈定位 |
| https://docs.fabricmc.net/develop/ | 将来明确需要 Fabric 时的独立技术路线 |

当前 `mod/` 基于官方 MDK 提交：
`00b49d3f81a257858ca425792d59b9e5ca0f7e56`。
保留 `mod/TEMPLATE_LICENSE.txt`；修改 NeoForge 版本以匹配本机 21.1.248。
自有代码的公开许可尚未选定，Mod 元数据暂为 All Rights Reserved。

每次针对具体功能研究时：

1. 确定本机 JAR 的版本与目标仓库，确认是真实上游。
2. 选择对应 MC/加载器分支与提交，不直接照抄默认分支的新版本 API。
3. 记录 LICENSE、代码与资源是否采用不同许可。
4. 按入口、注册、事件、数据、网络、渲染、兼容层绘制调用关系。
5. 在小型独立工程验证关键行为，再接入整合包。
6. 输出“源码位置 → 行为解释 → 可用扩展点 → 风险 → 验证结果”的研究笔记。

优先按实际需求拉取源码，避免预先克隆大量不相关仓库。

## 本机已发现的相关文件

以下版本来自实际 JAR 文件名，完整校验值保存在本机清单；正式接入 API 前还需核对 JAR 内的 Mod 元数据。

- TerraFirmaCraft：`TerraFirmaCraft-NeoForge-1.21.1-4.2.9.jar`
- Create：`create-1.21.1-6.0.10.jar`
- KubeJS：`kubejs-neoforge-2101.7.2-build.368.jar`
- KubeJS Create：`kubejs-create-neoforge-2101.3.1-build.18.jar`
- KubeJS TFC：`kubejs_tfc-1.21.1-2.0.2.jar`
- spark：`spark-1.10.124-neoforge.jar`

实例还包含 Aeronautics 和多项 TFC 扩展，涉及这些系统的改动需要做组合兼容验证。
