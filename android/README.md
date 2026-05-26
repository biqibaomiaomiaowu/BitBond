# BitBond Android 壳工程

本目录是 BitBond App 的 Android 原生壳工程。

## 打开项目

在 Android Studio 中打开 `android` 目录。

## 构建

在本目录运行：

```powershell
gradle :app:assembleDebug
```

也可以使用项目自带的 Gradle wrapper：

```powershell
.\gradlew.bat :app:assembleDebug
```

首次使用 wrapper 时会从 `services.gradle.org` 下载 Gradle。如果网络请求超时，也可以使用全局安装的 `gradle` 命令构建项目。

当前检测到的 SDK 平台是 `android-36.1`，因此工程使用 Android 16 QPR2 的 `compileSdk` 写法和 Android Gradle Plugin 8.13.0。

`debug APK` 会生成在：

```text
app\build\outputs\apk\debug\app-debug.apk
```

在两台设备上安装 `debug APK`：

```powershell
adb -s <device-a> install -r app\build\outputs\apk\debug\app-debug.apk
adb -s <device-b> install -r app\build\outputs\apk\debug\app-debug.apk
```

Supabase 配置值保存在仓库根目录 `.env`；不要把真实密钥粘贴到本 README。Android 构建只应打包 `SUPABASE_URL` 和 `SUPABASE_ANON_KEY`。

## D1-D14 / 前两周手工验收

使用两台 Android 设备，并分别登录不同测试用户。以下是 D1-D14 / 前两周针对 Android 配对与抽象状态闭环的验收流程。

1. 构建 `debug APK`，并在两台设备上安装 `debug APK`。
2. 在两台设备的 Android 系统设置中授予 Usage Access。
3. 在设备 A 打开 BitBond，创建邀请码。
4. 在设备 B 接受来自 A 的邀请码。
5. 在设备 A 打开一个已映射的 App，使其产生伴侣状态。
6. 在设备 B 刷新首页，确认 B 只能看到抽象状态，看不到 App 名称、包名、使用时长或其他原始前台详情。
7. 在设备 A 解除配对。
8. 刷新两台设备，确认双方都回到未配对状态。

## 范围

D1 有意限定为最小 Java 原生 `Activity`。WebView、JSBridge、Supabase 和 Usage Access 集成会在后续里程碑落地。
