# BitBond 状态识别测试表

测试日期：2026-05-26

## 测试目标

逐一验证已映射应用在真机前台使用后，BitBond 后台能否识别并上传正确的抽象状态。

## 通用测试步骤

1. 两台设备均安装最新 debug APK，并确认已完成登录、配对、通知授权。
2. 按测试模式分别配置上传端权限：
   - 实时模式：开启 BitBond 无障碍服务；Usage Access 可不开。
   - 轮询模式：不开 BitBond 无障碍服务；开启 Usage Access。
   - 轮询增强：在轮询模式基础上，将 BitBond 设为电池优化不限制/允许后台运行。
3. 在上传端打开 BitBond 一次，确认 `StatusMonitorService` 已启动。
4. 切换到待测应用并保持前台至少 90 秒。
5. 记录上传端 `BitBondStatus` 日志中识别到的包名、状态码、上传结果。
6. 查询数据库 `current_status`，记录 `status_code` 和 `updated_at` 是否更新。
7. 在接收端等待自动刷新或手动刷新伴侣状态，记录接收端是否显示正确抽象状态。

建议 logcat 过滤：

```powershell
adb -s <device-id> logcat -v time -s BitBondStatus
```

## 结果标记

- 通过：后台识别包名正确，数据库写入正确状态，接收端刷新后显示正确。
- 识别失败：没有读到包名，或读到其他包名。
- 映射失败：包名正确，但状态码不正确。
- 上传失败：包名和状态码正确，但 RPC/网络失败，数据库未更新。
- 接收端失败：数据库已更新，但接收端刷新后没有显示正确状态。

## 应用测试矩阵

| 序号 | 预期状态 | 应用 | 包名 | 测试模式 | 测试设备 | 前台停留时长 | 日志识别包名 | 日志状态码 | 上传日志结果 | 数据库状态码 | 数据库更新时间 | 接收端显示 | 结果 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | short_video | 抖音 | `com.ss.android.ugc.aweme` |  |  |  |  |  |  |  |  |  |  |  |
| 2 | short_video | 快手极速版 | `com.kuaishou.nebula` |  |  |  |  |  |  |  |  |  |  |  |
| 3 | watching_show | 爱奇艺 | `com.qiyi.video` |  |  |  |  |  |  |  |  |  |  |  |
| 4 | watching_show | 腾讯视频 | `com.tencent.qqlive` |  |  |  |  |  |  |  |  |  |  |  |
| 5 | watching_show | 优酷 | `com.youku.phone` |  |  |  |  |  |  |  |  |  |  |  |
| 6 | watching_show | 哔哩哔哩 | `tv.danmaku.bili` |  |  |  |  |  |  |  |  |  |  |  |
| 7 | reading | 番茄小说 | `com.dragon.read` |  |  |  |  |  |  |  |  |  |  |  |
| 8 | reading | QQ 阅读 | `com.qq.reader` |  |  |  |  |  |  |  |  |  |  |  |
| 9 | music | 网易云音乐 | `com.netease.cloudmusic` |  |  |  |  |  |  |  |  |  |  |
| 10 | music | QQ 音乐 | `com.tencent.qqmusic` |  |  |  |  |  |  |  |  |  |  |  |
| 11 | music | 酷狗音乐 | `com.kugou.android` |  |  |  |  |  |  |  |  |  |  |  |
| 12 | gaming | 王者荣耀 | `com.tencent.tmgp.sgame` |  |  |  |  |  |  |  |  |  |  |  |
| 13 | gaming | 和平精英 | `com.tencent.tmgp.pubgmhd` |  |  |  |  |  |  |  |  |  |  |  |
| 14 | social | 微信 | `com.tencent.mm` |  |  |  |  |  |  |  |  |  |  |  |
| 15 | social | QQ | `com.tencent.mobileqq` |  |  |  |  |  |  |  |  |  |  |  |
| 16 | social | 微博 | `com.sina.weibo` |  |  |  |  |  |  |  |  |  |  |  |
| 17 | social | 小红书 | `com.xingin.xhs` |  |  |  |  |  |  |  |  |  |  |  |

## 额外回归测试

| 序号 | 场景 | 测试设备 | 前台停留时长 | 预期结果 | 实际结果 | 结果 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| R1 | 未映射应用前台使用 |  | 90 秒 | 上传 `online` |  |  |  |
| R2 | 同一应用持续前台超过 15 分钟 |  | 16 分钟 | 至少续传一次，避免过期离线 |  |  |  |
| R3 | BitBond 后台，目标应用全屏前台 |  | 90 秒 | 后台识别并上传 |  |  |  |
| R4 | BitBond 与目标应用分屏 |  | 90 秒 | 识别并上传，接收端可刷新看到 |  |  |  |
| R5 | 切换多个同状态应用，例如微信到微博 |  | 各 90 秒 | 即使状态同为 `social`，包名变化后应上传 |  |  |  |
| R6 | 断网后恢复网络 |  | 90 秒 | 恢复后下一轮可上传 |  |  |  |
| R7 | 开启无障碍实时模式，不开启 Usage Access |  | 30 秒 | 切换应用后通过 `status accessibility event package=...` 上传 |  |  |  |
| R8 | 关闭无障碍，仅开启 Usage Access |  | 90 秒 | 通过 `status monitor poll` 尽量在 1 分钟内上传；若系统延迟需记录 |  |  |  |
| R9 | 关闭无障碍，开启 Usage Access，并放行电池优化 |  | 90 秒 | 后台轮询比未放行时更稳定；仍需记录是否超过 1 分钟 |  |  |  |

## 当前待关注问题

| 时间 | 设备 | 现象 | 初步判断 | 后续证据 |
| --- | --- | --- | --- | --- |
| 2026-05-26 | 小米 14 | 打开王者、微信、微博后等待 1 分钟以上，接收端没有刷新状态 | 需要区分上传端未写库、RPC 网络失败、接收端未自动刷新三类问题 | 补充 `BitBondStatus` 日志、数据库 `current_status` 最新行、接收端手动刷新结果 |
