# 挖掘机 IMU 正运动学知识库

## 1. 目标与边界

根据驾驶室、大臂、小臂、铲斗的实时角度，以及本机标定尺寸，计算斗尖位置，并为找平、挖沟和修坡提供几何基础。

目标输出：

- 驾驶室本地坐标下的斗尖 `(x, y, z)`；
- 左、中、右斗齿坐标；
- 接入 RTK 高程、上车 yaw 和天线杆臂后，输出斗尖 ENU 坐标与经纬度；
- 相对设计面的高度误差、坡面误差和沟槽误差。

本文件描述的是机械臂正运动学。**工程体积意义的填挖量**还需要设计面与实测地形网格，不能由单一斗尖坐标直接得到。

---

## 2. 当前传感器角度语义（已确认）

以下实时数据均为度数：

```text
realCabinPitchAngle  驾驶室俯仰；驾驶室水平 = 0°
realCabinRollAngle   驾驶室横滚；驾驶室水平 = 0°
realBoomAngle        大臂相对驾驶室的关节角
realStickAngle       小臂相对当前大臂的关节角
realBucketAngle      铲斗相对当前小臂的关节角
```

它们不是三个构件相对地面的绝对俯仰角。因此不能将 `realBoomAngle`、`realStickAngle`、`realBucketAngle` 分别直接代入 `sin/cos`。

### 2.1 大臂

以驾驶室为参考：

```text
大臂低位：-90°
大臂向上抬升：-90° → -80° → ... → 0°
```

默认使用：

```text
boomJoint = realBoomAngle
```

### 2.2 小臂：180° 为参考线，且跨 ±180°

```text
小臂参考位置：180°
向内收：180° → -170° → -160° → ... → 0°
向外伸：170° → 160° → 150° → ... → 0°
```

必须先将它转换为围绕参考线连续变化的关节角：

```text
stickJoint = wrap180(realStickAngle - 180°)
```

结果示例：

| `realStickAngle` | `stickJoint` |
|---:|---:|
| `180°` | `0°` |
| `-170°` | `+10°` |
| `170°` | `-10°` |
| `-160°` | `+20°` |
| `160°` | `-20°` |

`+180°` 和 `-180°` 是同一个物理方向；在端点处选择哪个数值不影响旋转后的坐标。

### 2.3 铲斗：90° 为参考线

```text
铲斗参考位置：90°
向内收：80° → 70° → ... → 0°
向外伸：100° → 110° → ... → 180°
```

转换为相对小臂的关节角：

```text
bucketJoint = realBucketAngle - 90°
```

示例：`80° → -10°`、`90° → 0°`、`100° → +10°`。

---

## 3. 坐标系与符号约定

### 3.1 驾驶室本地坐标系 C

```text
x：车辆/大臂前方
y：车辆左侧
z：驾驶室向上
```

大臂、小臂、铲斗在理想情况下都位于 C 的 `x-z` 平面。`+y/-y` 主要由驾驶室 roll、斗宽和铲斗侧向安装偏移产生。

### 3.2 角度正方向

本文先使用软件中的默认符号：上文转换出的 `boomJoint`、`stickJoint`、`bucketJoint` 均按 `+1` 使用。

不同 IMU 的安装方向可能相反。因此量产配置必须保留三个符号和三个零位项：

```text
boomJoint   = boomSign   × (realBoomAngle + boomSensorOffset)
stickJoint  = stickSign  × wrap180(realStickAngle - 180° + stickSensorOffset)
bucketJoint = bucketSign × (realBucketAngle - 90° + bucketSensorOffset)
```

其中 `boomSign/stickSign/bucketSign ∈ {+1, -1}`。首轮安装可均取 `+1`，现场以已知姿态验证并校正。

### 3.3 角度归一化

```java
/** 将角度归一化到 (-180°, 180°]。 */
static double wrap180(double angleDeg) {
    double result = angleDeg % 360.0;
    if (result <= -180.0) result += 360.0;
    if (result > 180.0) result -= 360.0;
    return result;
}
```

---

## 4. 相对关节角递推为构件绝对角

在二维高度计算中，角度以“相对水平、向上为正”的绝对俯仰角表示。令 `cabinPitch` 为驾驶室俯仰（水平为 `0°`）：

```text
boomAbs   = cabinPitch + boomJoint
stickAbs  = boomAbs   + stickJoint
bucketAbs = stickAbs  + bucketJoint
```

铲斗本体方向不等于斗尖方向。还需要斗轴到斗尖向量的固定安装偏移：

```text
tipAbs = bucketAbs + bucketTipAngleOffset
```

`bucketTipAngleOffset` 是“铲斗本体参考轴 → 斗轴到斗尖连线”的几何角，和 `bucketSensorOffset`（传感器安装误差）是两个独立参数，不能混用。

> 若现场验证发现“内收/外伸”与预期方向相反，优先调整对应的 `*Sign`；若所有姿态都固定偏移同一角度，则调整对应的 `*SensorOffset` 或 `bucketTipAngleOffset`。

---

## 5. 二维斗尖正运动学

设：

```text
Lb：大臂根铰点 → 小臂铰点距离
Ls：小臂铰点 → 斗轴距离
Lt：斗轴 → 斗尖的等效距离
```

所有长度统一使用米，所有三角函数的输入必须为弧度。

```text
x = Lb × cos(boomAbs)
  + Ls × cos(stickAbs)
  + Lt × cos(tipAbs)

z = Lb × sin(boomAbs)
  + Ls × sin(stickAbs)
  + Lt × sin(tipAbs)
```

这里的 `(x, z)` 是**相对大臂根铰点**的坐标，不是 RTK 世界高程，也不是斗尖经纬度。

等价 Java 伪代码：

```java
double boomJoint = boomSign * (rawBoom + boomSensorOffsetDeg);
double stickJoint = stickSign
        * wrap180(rawStick - 180.0 + stickSensorOffsetDeg);
double bucketJoint = bucketSign
        * (rawBucket - 90.0 + bucketSensorOffsetDeg);

double boomAbsDeg = cabinPitchDeg + boomJoint;
double stickAbsDeg = boomAbsDeg + stickJoint;
double bucketAbsDeg = stickAbsDeg + bucketJoint;
double tipAbsDeg = bucketAbsDeg + bucketTipAngleOffsetDeg;

double x = boomLengthM * Math.cos(Math.toRadians(boomAbsDeg))
        + stickLengthM * Math.cos(Math.toRadians(stickAbsDeg))
        + tipLengthM * Math.cos(Math.toRadians(tipAbsDeg));
double z = boomLengthM * Math.sin(Math.toRadians(boomAbsDeg))
        + stickLengthM * Math.sin(Math.toRadians(stickAbsDeg))
        + tipLengthM * Math.sin(Math.toRadians(tipAbsDeg));
```

---

## 6. 铲斗几何与 L2…L14 的使用原则

### 6.1 直接有铲斗相对小臂角（当前情况）

当前 `realBucketAngle` 已给出铲斗相对小臂的角度。计算斗尖只需要：

```text
Lt + bucketTipAngleOffset
```

或者更通用地保存斗尖在铲斗坐标系中的固定向量：

```text
rTipBucket = (tipX, tipY, tipZ)
```

因此 L2…L10 的油缸/摇臂尺寸不应参与本次斗尖 FK；它们可用于机械可达性校验、动画，或在“没有铲斗本体角、只有连杆角/油缸行程”时反解铲斗本体姿态。

### 6.2 没有铲斗本体角时

若未来输入改为铲斗连杆角或油缸行程，才使用图中的 L2…L10 做四连杆闭环。实现必须：

- 明确每个长度的两个端点；
- 在同一坐标系中建立闭环方程；
- 固定内收/外伸分支，避免 `acos` 双解跳变；
- 对每帧结果做可达性检查和连续性检查。

### 6.3 L11…L14

L11…L14 最终应归结为“斗轴到指定斗齿”的固定向量。左右齿不应只共享一个中心点，而应分别保存其相对铲斗坐标。

---

## 7. 三维坐标、驾驶室 roll 和左右斗齿

二维公式只适合在驾驶室近似水平、仅关心斗尖高度变化时使用。完整三维计算应分两步：

1. 在驾驶室坐标系 C 中，使用相对关节角计算斗尖和左右齿；
2. 使用驾驶室 `pitch/roll` 以及上车 `yaw`，将 C 旋转到 ENU 世界坐标系。

推荐保存三个铲斗局部点：

```text
rCenterBucket：中心斗尖
rLeftBucket：左斗齿
rRightBucket：右斗齿
```

则：

```text
pTooth_C = pBucketPivot_C + R_C_bucket × rToothBucket
pTooth_ENU = pBoomRoot_ENU + R_ENU_C × pTooth_C
```

其中 `R_ENU_C` 由 yaw、cabin pitch、cabin roll 构成。旋转顺序及正负号必须与 IMU 坐标系一致，并以静态标定姿态验证。

不要把 `cabinRoll` 直接加到二维俯仰角；roll 会导致左右斗齿的 `y/z` 不同，只能通过三维旋转正确处理。

---

## 8. ENU、RTK 经纬度与绝对高程

要得到斗尖经纬度，至少需要：

- RTK 天线 `latitude / longitude / altitude`；
- 上车 yaw（双天线 RTK heading，或回转编码器加零位标定）；
- RTK 天线到大臂根铰点的三维杆臂；
- 本地 ENU 原点或投影坐标系。

计算流程：

```text
RTK 天线 WGS84
  → 转 ENU
  → 减去/补偿天线到大臂根铰点杆臂
  → 加上斗尖在 ENU 中的机械臂向量
  → 斗尖 ENU
  → 转回 WGS84 经纬度与高程
```

仅有 `lat/lon`、没有 RTK 高程与 yaw 时，不能可靠计算斗尖绝对高程和经纬度。

---

## 9. 找平、挖沟、修坡与填挖误差

### 9.1 水平设计面

```text
heightError = tipUp - designUp
```

- `heightError > 0`：斗尖高于设计面，需要继续下挖；
- `heightError < 0`：斗尖低于设计面，已超挖；
- `heightError = 0`：到达设计面。

建议 UI 使用“高于设计面 / 低于设计面”或“下挖 / 超挖”描述，避免“填挖量”在体积和高度偏差之间歧义。

### 9.2 坡面

设计平面：

```text
aE + bN + cU + d = 0
```

斗尖到坡面的有符号法向距离：

```text
planeError = (aE_tip + bN_tip + cU_tip + d) / sqrt(a² + b² + c²)
```

### 9.3 沟槽

将斗尖 EN 平面坐标投影到沟槽中心线，获得：

- 里程 `station`；
- 横向偏差 `crossTrack`；
- 该里程处设计沟底高程 `designUp(station)`；
- 高程误差 `tipUp - designUp(station)`。

---

## 10. 标定与验收

### 10.1 必备标定参数

```text
Lb / Ls / Lt
boomSensorOffset / stickSensorOffset / bucketSensorOffset
boomSign / stickSign / bucketSign
bucketTipAngleOffset
斗宽及左/右齿在铲斗坐标中的位置
RTK 天线、驾驶室 IMU、大臂根铰点之间的杆臂
yaw 零位
```

### 10.2 最小验证姿态

至少记录并量测下列静态姿态的斗尖坐标：

1. 驾驶室水平，`boom=-90° / stick=180° / bucket=90°`；
2. 小臂内收约 `10°`，即 `stick=-170°`；
3. 小臂外伸约 `10°`，即 `stick=170°`；
4. 铲斗内收约 `10°`，即 `bucket=80°`；
5. 铲斗外伸约 `10°`，即 `bucket=100°`；
6. 驾驶室分别施加已知 pitch 和 roll，验证三维旋转后左右斗齿高差。

若某一步运动方向相反，先调整对应 `Sign`；若始终存在常量偏差，调整对应 offset；若误差随姿态放大，复查长度、斗尖固定向量和杆臂。

---

## 11. 当前工程改造清单

1. 将 `realBoomAngle / realStickAngle / realBucketAngle` 先按第 2 节转换为相对关节角；
2. 在一个统一的运动学类中递推 `boomAbs / stickAbs / bucketAbs / tipAbs`；
3. 用 `tipAbs`，而不是 `bucketAbs`，计算斗尖向量；
4. 将 `realCabinPitchAngle` 和 `realCabinRollAngle` 传入运动学输入；
5. 找平逻辑仅使用真实 FK/TCU 高程，移除随机模拟高度作为施工数据源；
6. 接入 yaw、RTK altitude 与天线杆臂后，再开放斗尖经纬度、左右斗齿世界坐标与坡面引导；
7. 为跨 `±180°` 的小臂角和参考姿态增加单元测试。
