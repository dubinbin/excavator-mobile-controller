# 找平 TCU 协议对接说明

本文档说明 App 侧找平（Level）功能与 TCU 的通信流程、代码结构与 UI 映射。协议来源：`imu.txt` §4.3–§4.5、§4.8、§6.2。

## 1. 协议流程（§6.2）

| 步骤 | 方向 | MsgID | 说明 |
|------|------|-------|------|
| 1 | App → TCU | `0x04` | 功能选择：Feature=找平(0x01)，Action=进入(0x01) |
| 2 | TCU → App | `0x84` | 成功判据：`Result=0` 且 `ActiveFeature=0x01` |
| 3 | App → TCU | `0x10` | 测点：Feature=找平，PointID=参考点(0x00)，PointMode=左/中/右(0/1/2) |
| 4 | TCU → App | `0x90` | 回传 Height(0.1cm)、Latitude、Longitude |
| 5 | App 本地 | — | 根据测点高度 + 用户填写的距离/填挖量计算目标高度 |
| 6 | App → TCU | `0x11` | 下发 `TargetHeight`（int32，0.1 cm） |
| 7 | TCU → App | `0x91` | 回传 `AcceptedTargetHeight` |
| 8 | App → TCU | `0x40` | 任务确认：Feature=找平，Action=确认(0x01) |
| 9 | TCU → App | `0xC0` | 成功判据：`Result=0` 且 `TaskState=0x01`（已激活） |

帧格式：`0x55 0xAA` + MsgID + DataLen + Data + CRC16-MODBUS + `0xFF`。  
与 100ms 实时流 `0xFA 0xFA`（IMU/RTK）独立，共用 UDP 管道。

## 2. 代码结构

```
TcuBusinessCodec.java      # 业务帧组包 / 解析 / 单位换算
TcuLinkHub.java            # UDP 发送与帧分发（MainActivity 注册 Sender）
LevelTcuWorkflow.java      # 找平状态机 + 请求/应答/超时（核心逻辑）
LevelTaskState.java        # UI 参数 + TCU 会话数据（测点高、确认高等）
```

### 2.1 `LevelTcuWorkflow` 状态

```
IDLE → FEATURE_ACTIVE → SURVEY_DONE → PARAMS_ACCEPTED → TASK_ACTIVE
```

- 单例，实现 `TcuLinkHub.BusinessFrameListener`
- 每次请求等待对应 ACK，超时 8s
- 目标高度算法：`TargetHeight = SurveyHeight(0x90) + (距离+填挖量)×1000`（0.1cm 整数）

### 2.2 传输层接入（MainActivity）

- UDP `onConnectSuccess` → `TcuLinkHub.setSender(...)` → `udpPipeline.writeData`
- `onReadData`：先 `0x50`→`0xD0`（`TcuInitHandshake`），再 `0x51` 位图，再 `TcuLinkHub.dispatch` 找平帧，再 `0xFA` 实时流
- 断开连接时 `TcuLinkHub.setSender(null)`

## 3. UI 映射

### LevelSettingActivity

| 用户操作 | 协议动作 |
|----------|----------|
| 进入页面 | `enterFeature()` → 0x04/0x84 |
| 切换参考斗尖 | `requestSurvey(pointMode)` → 0x10/0x90，回填测点高度/经纬度 |
| 填写目标高度、填挖量 | 本地 `LevelTaskState`（步骤 5） |
| 下一步 | `submitLevelParams()` → 0x11/0x91，成功后进入检查页 |
| 返回 | `exitFeature()` → 0x04 退出 |

### LevelPrecheckActivity

| 用户操作 | 协议动作 |
|----------|----------|
| 展示 | 参考点、填挖量、**TCU 确认目标高度**（0x91） |
| 开始作业 | `confirmTaskStart()` → 0x40/0xC0，成功后 `TaskType=LEVEL` + `RUNNING` 回主页 |
| 返回 | `exitFeature()` 并回主页 |

### MainActivity（作业中）

- 进入 LEVEL+RUNNING 时 `snapshotLevelDesignSurface()`：
  - 优先用 `AcceptedTargetHeight − SurveyHeight` 作为设计面偏移（米）
  - 否则回退 UI 的 `距离+填挖量`
- `leftActivityGauge` / `rightActivityGauge`：`dz = z_tip − z_design`（cm）
  - `z_tip` 来自 `0xFA` 实时 IMU + `ArmForwardKinematics`（100ms 刷新）

## 4. 数据字段（LevelTaskState）

| 字段 | 来源 |
|------|------|
| `surveyHeightTenthCm` | 0x90 Height |
| `surveyLat` / `surveyLon` | 0x90 |
| `acceptedTargetHeightTenthCm` | 0x91 |
| `tcuTaskActive` | 0xC0 TaskState=1 |
| `targetHeight` / `fillCut` | 用户输入（米） |

## 5. 错误与前置条件

- **接收机未连接**：`TcuLinkHub.isConnected()==false`，各步骤直接失败并 Toast
- **测点失败**：0x90 Result≠0（RTK 未固定、IMU 未稳、急停等，见协议 §4.4）
- **参数非法**：0x91 Result≠0，常见为未测点或高度超范围
- **任务未激活**：0xC0 TaskState≠1

## 6. 上电初始化（§6.1，MainActivity）

| 步骤 | 说明 |
|------|------|
| TCU → App | `0x50` InitBitmap / 协议版本 |
| App → TCU | `0xD0` RetryReason：`0x00` 全位正常；否则联调默认 `0x02` 忽略继续 |
| 之后 | TCU 发 `0x51` + `0xFA` 100ms 实时流 |

实现：`TcuInitHandshake.tryHandle()`，在 `onReadData` 中优先于 `0x51`/找平业务帧处理。

## 7. 尚未实现（后续可扩展）

- 机型参数 `0x02/0x82`（进入功能前可选）
- 坐标定点模式与 0x11 的 TCU 侧语义对齐（当前 0x11 仅 TargetHeight）
- 挖沟/修坡复用 `TcuBusinessCodec` + 独立 `DitchTcuWorkflow` / `SlopeTcuWorkflow`

## 7. 调试建议

1. 确认主页顶栏「已连接」（UDP 管道成功）
2. Logcat 过滤 `LevelTcuWorkflow`、`TcuLinkHub`、`UDP`
3. 进入找平页应看到：进入找平 → 测点 → 填参数 → 检查页显示 TCU 高度 → 开始作业
4. 作业中观察 `LevelGauge` 日志：`snapshot z_design=...`
