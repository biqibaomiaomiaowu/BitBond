# BitBond 状态同步真机测试报告

测试日期：2026-05-27

## 测试环境

- 发送端/接收端设备：
  - `89df7d9e`，Xiaomi `23113RKC6C`，Android 16
  - `a0712f54`，Xiaomi `23127PN0CC`，Android 16
- APK：`android/app/build/outputs/apk/debug/app-debug.apk`
- 服务端：自建 Supabase，经服务器 `82.156.76.253` 上的 Kong/PostgREST/Postgres 提供接口
- 数据核验：
  - 发送端 REST 查询 `current_status`
  - 接收端 WebView Bridge 调用 `refreshPartnerStatus()`
  - 服务端 Nginx access log 核对 `upsert_current_status` / `get_partner_status`

未记录或提交任何 access token、SSH 私钥、用户隐私内容。

## 基础功能烟测

设备：`89df7d9e`

| 操作 | 结果 |
| --- | --- |
| 启动 BitBond | 成功，Bridge ready |
| `getInitialState()` | 成功，已配对 |
| `checkUsageAccess()` | 成功，返回 `true` |
| `checkBatteryOptimization()` | 成功，返回 `true` |
| `listAvatars()` | 成功，返回 8 个头像 |
| `getPrivacySettings()` / `updatePrivacySettings()` | 成功 |
| `pauseSharing()` / `resumeSharing()` | 成功 |
| `sendHeart()` | 成功 |
| 底部 Tab 切换 | 房间、配对、头像、权限、设置、说明、调试均可切换 |

未执行破坏性确认操作：未确认解除配对，未删除账号，未清数据。

## 无障碍关闭，仅 Usage Access 后台路径

确认条件：

- `enabled_accessibility_services` 为空
- `GET_USAGE_STATS: allow`
- `StatusMonitorService` 前台服务运行中
- 目标 App 前台停留期间不打开发送端 BitBond
- 每 5 秒查询一次服务器 `current_status`

| 设备 | 切换路径 | 预期 | 服务器结果 | 耗时 | 接收端刷新 |
| --- | --- | --- | --- | --- | --- |
| `89df7d9e` | 微信 -> QQ 音乐 | `music` | 成功更新为 `music` | 10.2 秒 | 可见 |
| `89df7d9e` | QQ 音乐 -> 抖音 | `short_video` | 180 秒内仍为 `music` | 未成功 | 不可见 |
| `a0712f54` | QQ 阅读 -> 王者荣耀 | `gaming` | 180 秒内仍为 `reading` | 未成功 | 不可见 |

结论：不开无障碍时，纯后台轮询不是稳定实时能力；有的切换约 10 秒上传，有的超过 3 分钟仍未上传。打开 BitBond 后的校准路径可以立即识别并上传。

## 无障碍开启后的事件路径

确认条件：

- 两台设备 `enabled_accessibility_services` 均包含：
  - `com.bitbond.app/com.bitbond.app.status.StatusAccessibilityService`
- `GET_USAGE_STATS: allow`
- `StatusMonitorService` 前台服务运行中
- 目标 App 前台停留期间不打开发送端 BitBond

| 设备 | 切换路径 | 预期 | 服务器结果 | 耗时 | 备注 |
| --- | --- | --- | --- | --- | --- |
| `89df7d9e` | 微信 -> QQ 音乐 | `music` | 成功更新为 `music` | 2.1 秒 | 无障碍事件路径明显快于轮询 |
| `89df7d9e` | QQ 音乐 -> 抖音 | `short_video` | 45 秒内仍为 `music` | 未成功 | 说明抖音场景无障碍事件未稳定触发有效上传 |
| `a0712f54` | QQ 阅读 -> 王者荣耀 | `gaming` | 约 2 秒被写成 `online`，60 秒内未变成 `gaming` | 错误更新 | 说明某些窗口/包名噪音会抢先写默认状态 |

接收端验证：

- 当服务器写入正确状态时，另一台调用 `refreshPartnerStatus()` 可以看到对应状态。
- 王者荣耀误写 `online` 后，另一台刷新看到的也是 `online`，证明问题发生在发送端上传前，而不是接收端刷新。

## 打开 BitBond 校准路径

| 场景 | 调试读取 | 上传结果 | 接收端刷新 |
| --- | --- | --- | --- |
| 微信停留超过 5 分钟后打开 BitBond | `com.tencent.mm` | `social` / 或已去重 | 可见 |
| 抖音 -> 桌面 -> BitBond | `com.ss.android.ugc.aweme` | `short_video` / 或已去重 | 可见 |
| 王者荣耀后台未上传后打开 BitBond | `com.tencent.tmgp.sgame` | `gaming` | 可见 |

结论：打开 BitBond 后“最近外部 App 校准”路径工作正常；桌面、BitBond 自身不会覆盖真实外部 App。

## 服务端核验

服务器检查结果：

- `supabase-auth` healthy
- `supabase-kong` healthy
- `supabase-db` healthy
- `supabase-rest` running

日志观察：

- 成功场景能在 Nginx access log 看到 `POST /rest/v1/rpc/upsert_current_status`。
- 失败的纯后台窗口内没有对应发送端的有效 `upsert_current_status`，或出现错误状态写入。
- `get_partner_status` 正常返回，接收端刷新链路可用。

## 当前结论

已修复并验证正常的部分：

- 打开 BitBond 后可读取“上一个非 BitBond、非桌面、非系统的外部 App”。
- UsageEvents 2 小时回看可覆盖长时间停留后再打开 BitBond。
- 桌面 `com.miui.home` 不会覆盖真实外部 App。
- 服务器写入和接收端刷新链路正常。

仍存在的问题：

- 仅靠 Usage Access 后台轮询时，上传耗时不稳定，可能 10 秒内成功，也可能 3 分钟仍不更新。
- 开启无障碍后，部分 App 可在约 2 秒内上传，但抖音、王者荣耀场景仍不稳定。
- 王者荣耀场景出现过错误写入 `online`，疑似无障碍窗口事件中的非目标包名或未映射包名抢先上传。

建议后续修复方向：

1. 无障碍事件不要直接把未知包名映射成 `online` 上传；未知包名应跳过或回退到最近可信外部包。
2. 增加无障碍事件调试记录：event type、原始 package、过滤结果、映射状态、上传结果。
3. 对游戏/短视频常见中间包、SDK 包、启动 Activity 做专项过滤或映射补充。
4. 后台轮询不要作为实时同步承诺；产品口径应改为“无障碍实时，Usage Access 作为兜底和打开 BitBond 时校准”。
