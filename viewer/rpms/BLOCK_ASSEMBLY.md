# RPMS-Q12 / 实际 Aeronautics 方块装配台

本次纠正旧网页错误：旧版圆柱轴承和虚构梁只是自制三维形状，不是航空学原版方块。主页面 /rpms/ 改为逐块登记真实 Mod ID 的方块装配清单，旧概念演示保留在 /rpms/concept.html。

## 真实组件（源码核对）
- simulated:swivel_bearing 旋转轴承：官方 blockstates/swivel_bearing.json → models/block/swivel_bearing/block.json。
- simulated:physics_assembler 物理组装器；simulated:analog_transmission 传动；simulated:gimbal_sensor 姿态；simulated:velocity_sensor 速度；simulated:optical_sensor 光学；simulated:gray_portable_engine 动力。
- Create: create:andesite_casing 等；原版结构块 minecraft:iron_block 等。
官方源码：https://github.com/Creators-of-Aeronautics/Simulated-Project

## 官方美术版权
官方 LICENSE.md 将 simulated/common/src/main/resources/assets/ 下的模型及贴图定为 All Rights Reserved。不得直接拷贝进本站或仓库。
网页仅允许用户通过浏览器导入自己本地已安装的航空学 JAR，JSZip 本地读取 assets/simulated/blockstates、models、textures 并解析 Minecraft 元素面；如模型引用 Create 贴图，可再导入同版本 Create JAR。缺失文件时显示彩色方块占位，不伪装原版模型。浏览器不上传 JAR。

## 严格的技术边界
这是可以逐格检查 Mod ID 和 BlockState 的拟议装配设计，不是已通过 Sable/GameTest 的可运行机器 schem。尚未测关节轴承连接是否成立、实际动力齿轮连通、胶合、负载和稳定行走。局部 13 段/12 关节的 Three.js 示意摆动不等于真实物理。可下载 JSON 清单进行工程审查，不能直接导入正式存档当做可运行模型。

## 2026-10-21 网页维护
- 3D 场景中的轴承及关键航空学控制方块现在显示跟随位置移动的标签；“显示部件与方块 ID”开关真正控制标签层，而不再只改变右侧文字透明度。
- 修复本地 JAR 模型对无命名空间父模型 `block/cube_all` 的处理：按照 Minecraft 命名空间语义解析为 `minecraft:block/cube_all`，在未导入 Minecraft 客户端 JAR 时仅使用自制立方体几何，不包含官方贴图。
- 冒烟测试的合成模型改为使用这种父模型继承，并检查标签开关。动态 BER 与其他复杂父模型仍需要单独覆盖。

## 运行与测试
测试脚本使用自己生成的合成 fixture-simulated.jar 验证网页的 JAR 解析能力，**不复制任何官方原版资产**。原版大型 BlockEntity 动态渲染组件、复杂 multipart、特殊模型 UV 等尚无法在普通静态模型渲染器完全还原。
实现文件 index.html, blocks.js, blocks.css；独立 Chrome 测试 .github/workflows/rpms-authentic-blocks-check.yml。
发布成功地址 https://g2066203208-beep.github.io/mc-studio/rpms/ 。
