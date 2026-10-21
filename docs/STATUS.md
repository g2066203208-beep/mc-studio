# 验证状态

日期：2026-10-20（Asia/Shanghai）。

## 已完成

- 实机发现 HMCL 与 TerraFirma Rebirth 2 实例。
- 读取整合包 manifest 和版本描述，交叉确认 Minecraft 1.21.1、NeoForge 21.1.248、Java 21。
- 生成 222 个 Mod 的大小与 SHA-256 清单；清单脚本已运行成功。
- GitHub 插件验证账号 `g2066203208-beep`，能够列出授权仓库。
- 建立最小 NeoForge 工程、文档、代理适配构建脚本、CI 配置和独立本地 Git 仓库。
- 首次直连构建失败于 Maven 连接超时；读取现有 Windows 代理后构建成功。
- `Build.ps1`：Gradle `BUILD SUCCESSFUL in 8m 8s`，退出码 0。
- 检查 JAR 内容：包含 StudioMod.class 与 META-INF/neoforge.mods.toml。
- Git 暂存差异空白检查通过，本机配置、日志和产物被排除。
- 已补充云端构建入口 `scripts/build.sh` 与跨会话交接文档 `docs/HANDOFF.md`；云端不依赖本机路径。
- 22 个工程文件通过 GitHub 插件上传，初始化 [PR #1](https://github.com/g2066203208-beep/mc-studio/pull/1) 已合并。
- [GitHub Actions 39402002769](https://github.com/g2066203208-beep/mc-studio/actions/runs/39402002769)：Java 21、构建、上传 JAR 均 success。
- 验证对应 PR head：`11c6feca3e25ca2f5be8e9a86a04a94b756ec036`。PR 工作流使用 GitHub 生成的测试合并提交，因此 artifact 名字中的 SHA 可与 head 不同。
- 产物 ID：`12255270668`，创建时未过期，平台记录过期时间为 2027-01-18。Actions 产物是临时下载，不等于永久 Release。
- 本地已经通过 Git 拉取远程分支；原本地初始化历史保留在 `bootstrap/local-history`。

产物：`mod/build/libs/reme_studio-0.1.0-dev.jar`

SHA-256：
`64310FBC8447A3FC5FFE6D89CC708F425CFD4A083E489277BE6C88E0030DC11C`

完整成功日志：`local/build-proxy.log`。
这是开发骨架，仅记录加载标记，没有新增玩法。
Gradle 报告使用了将来不兼容 Gradle 10 的弃用功能；当前固定 Wrapper 9.2.1 构建成功，不自动升级 Wrapper。

## 尚未完成

- 该骨架没有测试源码，Gradle `test` 为 `NO-SOURCE`；不能称为自动化测试通过。
- 尚未启动开发客户端、专用服务器或完整整合包验证。
- 尚未确认 HMCL 的实际生效隔离目录，未向正式 mods 目录部署。
- 本聊天已验证 GitHub 插件写入及云端构建；尚未在用户的另一个网页版聊天中实际发起开发任务。那个会话仍须具备该私有仓库的访问权和写工具。
- 尚未针对具体玩法展开上游源码分析与功能实现。

真实游玩文件和存档未被修改。后续先根据用户目标确定实例与功能，再开展对应开发和运行测试。

## 2026-10-21 · 自研维度边界（工作中）

- 废弃上游 Dimension Stack 适配方案（PR #16 closed）。
- 自主 Java 空间拓扑与跨维度玩家传送，版本 `0.3.0-dev`；无 Immersive Portals 依赖。
- 只有真正开发跨维度可视化渲染与数据流后才能称为无缝，不得提前宣称已实现。

## 2026-10-21 自研 Native Seam v0.4.0-dev

已完成自研帧版本协议、远端方块/光照增量推送、遮挡缓存、视锥裁剪和 320 格远程地形高度图取样。源代码仍在 PR #18。**严格区分 CI 编译/单测与真正客户端跨维无缝体验：后者尚未完成。** 参见 `docs/NATIVE_SEAM_ARCHITECTURE.md`。
