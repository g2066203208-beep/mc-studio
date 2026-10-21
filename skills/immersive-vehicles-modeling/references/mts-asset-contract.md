# MTS 车辆资产接口：OBJ + PNG + JSON

> 下述字段、示例来自对实际包文件的阅读；仅是**设计契约和核查清单**，不是替代 MTS 官方 JSON Schema 的完整规范。制作原创包前必须锁定目标 MTS 版本并用游戏加载器验证。

## 必须区分的三层

| 层 | 职责 | 常见物件 |
|---|---|---|
| OBJ 网格 | 可渲染的真实几何、UV、对象名/局部几何 | 车身、车门、仪表、踏板、灯、玻璃、车轮视觉占位 |
| 贴图 PNG | UV 材质颜色与像素结构 | 同车不同车漆；物品图另属于 `textures/item` |
| `jsondefs/vehicles/<id>.json` | 插槽、功能、动画、碰撞/座位/车辆参数 | `motorized`, `parts`, `collisionGroups`, `rendering`, `definitions` |

OBJ 常见行：`o <objectName>`（命名分组）、`v`（顶点）、`vt`（UV）、`vn`（法线）、`f v/vt/vn ...`（三角面/多边形）。UV 显示异常先检查 VT 与对应车漆图，而不是生成“看起来差不多”的贴图。

## 参考 JSON 结构：字段级而非盲复制

Mustang 实际 JSON 顶层包含：
`motorized`、`parts`、`variableModifiers`、`collisionGroups`、`connectionGroups`、`instruments`、`definitions`、`initialVariables`、`rendering`、`general`。

- `motorized` 中可观察到 `emptyMass`、`fuelCapacity`、`steeringForceFactor`、`axleRatio`、`brakingFactor`、`dragCoefficient`、`hasHeadlights` 等。单位、物理效果需用对应引擎文档验证。
- `parts` 中每个安装点含 `pos: [x,y,z]`、`types`，部分还有 `turnsWithSteer`、`isController`、`partScale`、`animations`。MTS Mustang 里四个轮位均标注 `ground_wheel`。摩托车要验证二轮数量及前轮转向，不套用四轮车辆配置。
- `rendering.animatedObjects` 的 `objectName` 指向 OBJ 对象名，`animations` 配 `animationType`、`variable`、`centerPoint`、`axis` 等。例如车门旋转、方向盘转动、油门踏板摆动。
- `rendering.lightObjects` 用于与发光相关的模型部件配置。需要验证光照条件、灯罩材质和不同摄像机角度。
- `collisionGroups` 属于游戏中物理/交互范围，不会被裸 OBJ 网页查看器自动实现。

## 每个原创模型的建议项目结构

```text
assets/<原创packid>/
  objmodels/vehicles/<id>.obj
  textures/vehicles/<id>_red.png
  textures/vehicles/<id>_black.png
  jsondefs/vehicles/<id>.json
model-source/
  <id>.bbmodel
reports/
  <id>.inspection.json
```

各路径须与**实际 MTS 版本加载规则**核对；不要将该示例误当成无需打包、无需验证就能玩的完整 Pack。源工程 `.bbmodel` 留在工程层，不要无意发布到 MC 模组资源中。

## 零件/动画自查

1. 每个车身可动对象在 OBJ 中名字**唯一且稳定**，不因再次导出被自动追加 `.001` 而失联。
2. 以 Pivot 表记录：objectName、动作变量、旋转中心（本地空间）、轴向、角度限制、遮挡安全。
3. 车轮位置由轮中心推导：轮径、接地高度、悬挂行程、轮距/轴距；正前方/正侧面的轴线重合须检查。
4. 驾驶席、乘客席、碰撞盒不能直接照搬 Mustang 的四轮座位布局。
5. 灯光部分要分清“几何亮色 PNG”与 MTS 触发发光/照明效果，两者不等价。
6. 如果测试的是 `OBJLoader` 网页，驾驶、碰撞、Sable 船体随动、车轮实体等都**尚未被验证**。
7. 如 JSON 包含 `//` 注释，普通 `json.loads` 无法直接解析；仓库检查脚本支持 JSONC 形式，但最终仍须用游戏原生加载器确认。
