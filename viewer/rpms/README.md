# RPMS-Q12 · 交互式三维机械样机展示

位置：GitHub Pages 项目页面下 /rpms/。与之前的 Mustang 车辆查看器同站，分别保持完整功能。

## 功能

- Three.js 真 3D 程序化网格，不依赖图片假旋转：主机架、传动总成、骨架、伺服轴承、上/下腿梁、执行杆和脚掌都是真正可检视的 Mesh。
- 读取 quad12.design.json（镜像自设计分支 projects/physical-machinery/quad12.design.json）：13 个设计物理段、12 个旋转约束、4 条腿 × 3 DOF；主机架 1 段，4×HIP_CARRIER/UPPER/LOWER_FOOT 三段。
- 运行模式：站立、慢速四足运动学示例（逐腿轮换，75% 支撑/25% 摆动）、单腿循环、单轴手动调姿。每轴角度按设计限位约束。
- 自由 OrbitControls 视角、正面/侧面/俯视、点击局部关节、12 轴检视、分段爆炸视图、显示地面/轴线/足底设计参考/网格、调整速度、PNG 截图。
- 中文界面、手机触屏、静态 GitHub Pages 托管。

## 严格的真实性边界

网页是机械运动学数字样机：有真正的三维关节坐标层级和随角度变换的父子零件，但 Three.js 动作曲线不是通过 Sable 物理求解产生。脚圈是设计足底位置参考，并非实测足底接触/压力。不会计算真实重力、力矩、滑移、受载或自身站立控制。页面明示 G1-G4 尚未实测，不能把网站动画当成游戏内真实运行的 Mod。

网页先用于用户检查 Q12 机械造型、关节位置/方向/行程，以及评审工程拓扑和发现干涉，之后依据真实 Aero/Sable 标定修正。Minecraft JAR、Sable UUID、马达力矩/压力和真实站立行走 GameTest 是后续 M1-M4 交付物。

## 文件和部署

- index.html：网页结构与可交互操作区
- style.css：工业实验室自适应样式
- app.js：Three.js 三维旋转层级与分段网格
- quad12.design.json：工程四足拓扑与限制的独立镜像
- tests/smoke.cjs：真实浏览器操作冒烟测试
- .github/workflows/rpms-viewer-check.yml：静态和浏览器 CI

GitHub Pages 使用现有 .github/workflows/deploy-viewer.yml，在 main 的 viewer/ 更新后部署整个 viewer 目录。部署成功后路径为 https://g2066203208-beep.github.io/mc-studio/rpms/ 。如果 Pages 未开启或无权限，不能仅因写了 URL 就声称已经上线。

## 资源授权

所有机械外观通过 Three.js 基础几何程序化创建，没有拷贝第三方模型/贴图；Three.js 由指定版本的 CDN 动态加载，浏览器需联网。字体失败将自动回退。原来的 Mustang 网页只新增到 RPMS 的导航，不动其模型功能。
