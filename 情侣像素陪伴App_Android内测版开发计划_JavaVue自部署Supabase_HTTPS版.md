# 情侣像素陪伴 App：Android 内测版完整开发计划

> 版本：v1.1 内部测试版
> 平台：Android
> 周期：30 天
> 开发方式：个人开发 + AI 辅助
> 分发方式：APK 安装包直接安装，不上架应用商店
> 客户端：Java 原生壳 + WebView + Vue 纯界面
> 后端：自部署 Supabase
> 服务器：香港 / 新加坡 / 日本等非中国大陆节点优先
> 域名与 HTTPS：保留，使用 `api.xxx.com` + Caddy + Let’s Encrypt
> 推送：第一版不接系统推送，第二版再做
> 像素资产：开发期统一使用 Codex / ImageGen 生成，App 内不提供 AI 生成能力

---

## 0. 当前最终决策

### 0.1 第一版目标

第一版不是正式商业上线版本，而是一个可以在内部小范围真实测试的 Android MVP。

第一版要验证的核心问题是：

1. 用户是否愿意授权 Android Usage Access。
2. 本机前台 App 是否能稳定映射成抽象状态。
3. “对方正在生活”的像素陪伴感是否成立。
4. 首页和 Widget 是否足够形成低打扰日常陪伴。
5. 用户是否会把产品理解成陪伴，而不是监控。

### 0.2 第一版技术口径

```text
Android APK
  ↓
Java 原生壳
  ↓
WebView 加载本地 Vue 页面
  ↓
JSBridge 调 Java
  ↓
Java 负责业务、权限、网络、后台、Widget
  ↓
HTTPS
  ↓
自部署 Supabase
  ↓
PostgreSQL current_status 状态中转
```

核心原则：

> **Vue 只做界面，Java 是唯一业务层。**

Vue 不直接请求 Supabase，不保存 token，不读取 Android 系统状态，不做后台任务，不参与 Widget。

### 0.3 第一版功能范围

| 模块 | 是否做 | 第一版实现形态 |
|---|---:|---|
| Android App | 做 | Java 原生壳 + Vue 纯界面 |
| 注册 / 登录 | 做 | Supabase 匿名账号 |
| 情侣配对 | 做 | 邀请码配对 |
| 解绑 | 做 | 单方立即解绑 |
| 像素形象选择 | 做 | 8 个预设角色 |
| 状态识别 | 做 | Java 调用 UsageStats / UsageEvents |
| 状态映射 | 做 | 本地包名映射为抽象状态码 |
| 首页展示 | 做 | Vue 渲染对方像素状态和最近更新时间 |
| 最近更新时间 | 做 | 只展示当前状态更新时间 |
| 暂停共享 | 做 | 对方明确看到“已暂停共享” |
| 简单互动 | 做 | 爱心，App 内未读提醒 |
| Widget | 做 | Java 原生 AppWidgetProvider + RemoteViews |
| 推送通知 | 第一版不做 | 第二版接极光 / 个推 / 厂商通道 |
| 隐私设置 | 做 | 权限说明、类别开关、暂停、解绑、注销 |
| 账号注销 / 数据删除 | 做 | 后端删除业务数据 |
| 基础埋点 | 做 | Supabase `analytics_events` 表 |
| 像素资产 | 做 | Codex / ImageGen 生成开发期素材 |

### 0.4 第一版不做内容

| 不做内容 | 原因 |
|---|---|
| iOS | 第一版只验证 Android 自动状态识别 |
| 应用商店上架 | 内部测试，避免商店审核和备案材料 |
| 系统推送 | 厂商通道申请复杂，延期到第二版 |
| 手机号登录 | 避免短信服务申请和实名主体流程 |
| 微信 / QQ / Apple 登录 | 避免开放平台申请 |
| 聊天 | 会改变产品定位 |
| 自由文本留言 | 会引入内容治理 |
| 状态历史明细 | 容易变成监控工具 |
| 具体 App 名称展示 | 隐私风险高 |
| 具体使用时长展示 | 容易引发关系审计 |
| 地图定位 | 与产品核心无关且风险高 |
| 情侣房间 | 第二版留存功能 |
| 纪念日系统 | 非核心验证点 |
| App 内 AI 生成头像 | 开发期生成素材即可 |
| 会员 / 支付 / 广告 | 内测阶段不商业化 |

---

## 1. 开发前准备清单

### 1.1 必须准备

| 项目 | 是否属于复杂申请 / 备案 | 说明 |
|---|---:|---|
| 云服务器账号 | 低 | 用于自部署 Supabase，建议香港 / 新加坡 / 日本节点 |
| 域名 | 低 | 推荐 `api.your-domain.com`，用于 HTTPS 和后续迁移 |
| HTTPS 证书 | 低 | 用 Caddy 自动申请 Let’s Encrypt 证书 |
| Android 签名证书 | 不是平台申请 | 本地生成 `.jks`，APK 必须签名 |
| OpenAI / Codex 账号 | 低 | 用于开发期生成像素资产 |
| Android 测试手机 | 不是申请 | 至少 3 台国产 Android 真机 |
| 内测隐私说明 | 自己撰写 | 首次启动展示，说明数据范围 |
| 内测安装说明 | 自己撰写 | 告知用户如何安装 APK 和授权 Usage Access |

### 1.2 第一版暂时不需要

| 项目 | 第一版处理 |
|---|---|
| Google Play 开发者账号 | 不需要 |
| 国内应用商店开发者账号 | 不需要 |
| 华为 / 小米 / OPPO / vivo / 荣耀开放平台账号 | 不需要 |
| 极光 / 个推账号 | 不需要 |
| 厂商推送参数 | 不需要 |
| FCM | 不需要 |
| 短信服务商 | 不需要 |
| 微信 / QQ 登录申请 | 不需要 |
| 支付商户号 | 不需要 |
| 广告平台账号 | 不需要 |
| 软著 | 内测先不做 |
| 对象存储 / CDN | 不需要，素材打包进 APK |
| 国内应用市场上架材料 | 不需要 |

### 1.3 备案最小化策略

第一版尽量减少备案和申请环节，因此服务器建议：

```text
香港 / 新加坡 / 日本云服务器
  ↓
域名解析到该服务器
  ↓
Caddy 自动 HTTPS
  ↓
APK 内部分发
```

不要在第一版使用中国大陆云服务器。使用中国大陆境内服务器并对外提供互联网信息服务时，通常会进入 ICP / App 备案语境。第一版内部测试阶段的重点是快速验证产品闭环，不要把时间耗在备案材料和应用商店审核上。

---

## 2. 域名与 HTTPS 决策

### 2.1 是否保留域名和 HTTPS

保留。

最终方案：

```text
https://api.your-domain.com
  ↓
Caddy / Nginx HTTPS 反向代理
  ↓
自部署 Supabase API Gateway
```

### 2.2 域名的作用

域名主要解决：

1. API 地址稳定。
2. 后续换服务器只改 DNS，不用重新打包 App。
3. HTTPS 证书申请简单。
4. Supabase 外部 URL 配置更规范。
5. 内测用户访问更稳定。

### 2.3 HTTPS 的作用

HTTPS 主要解决：

1. 加密 App 和服务器之间的数据传输。
2. 防止匿名账号 token、情侣关系、状态码被明文传输。
3. 防止中间人篡改状态数据。
4. 让 Android 网络请求更符合默认安全策略。

第一版虽然不上架，但会传输以下信息：

- 匿名账号 session。
- 情侣配对关系。
- 当前抽象状态。
- 暂停共享状态。
- 爱心互动。
- 删除账号请求。

因此不建议使用裸 HTTP。

### 2.4 推荐 Caddy 配置示例

假设你的 Supabase API gateway 在本机 `8000` 端口：

```caddyfile
api.your-domain.com {
    reverse_proxy localhost:8000
}
```

Caddy 会自动申请和续期 HTTPS 证书。正式配置时要根据 Supabase Docker 的实际端口和反向代理路径调整。

---

## 3. 总体系统架构

### 3.1 逻辑架构

```text
用户 A Android 手机
  ↓
Java StatusDetector 读取 UsageEvents
  ↓
Java StatusMapper 本地映射状态码
  ↓
Java StatusRepository 通过 HTTPS 上传 current_status
  ↓
自部署 Supabase PostgreSQL
  ↓
用户 B Android 手机打开 App / Widget 刷新
  ↓
Java StatusRepository 拉取 partner_status
  ↓
Vue 首页展示对方像素状态
```

### 3.2 状态同步链路

以“刷短视频中”为例：

```text
A 手机检测最近前台 App
  ↓
Java 本地判断属于 short_video
  ↓
上传：status_code = short_video
  ↓
服务器只保存 current_status
  ↓
B 打开首页或 Widget 刷新
  ↓
看到：TA 正在刷短视频中
```

B 不会看到：

- 抖音 / 快手 / 小红书等具体 App 名称。
- 包名。
- 使用时长。
- 打开时间线。
- 历史记录。
- 聊天对象、浏览内容。

### 3.3 第一版不做实时转发

不做：

```text
A 每秒上传 → 服务器实时推送给 B
```

做：

```text
A 状态变化时上传 → B 打开首页 / Widget 时拉取
```

理由：

- 省电。
- 降低后台保活难度。
- 减少监控感。
- 更符合内部测试阶段复杂度。

---

## 4. Android 客户端架构：Java 原生壳 + Vue 纯界面

### 4.1 核心原则

```text
Java = 业务层 + 系统能力 + 数据层 + 后台任务
Vue = 纯界面层
```

Vue 不直接接触：

- Supabase token。
- UsageStats / UsageEvents。
- Android 权限。
- WorkManager。
- Widget。
- 本地敏感缓存。
- 状态识别逻辑。
- 网络鉴权细节。

### 4.2 职责划分

| 模块 | Java 负责 | Vue 负责 |
|---|---:|---:|
| 匿名登录 | 是 | 否 |
| 保存登录状态 | 是 | 否 |
| 配对 API | 是 | 否 |
| 获取对方状态 | 是 | 否 |
| 上传自己的状态 | 是 | 否 |
| Usage Access 检测 | 是 | 否 |
| 跳转系统权限页 | 是 | Vue 只触发按钮 |
| UsageStats 状态识别 | 是 | 否 |
| 状态映射 | 是 | 否 |
| WorkManager 后台上传 | 是 | 否 |
| Widget | 是 | 否 |
| 首页 UI | 提供 JSON 数据 | 渲染界面 |
| 角色选择 UI | 提供角色数据 | 渲染网格 |
| 爱心互动 | 写入后端 | 播放动画 |
| 隐私说明 | 提供必要状态 | 展示文本 |
| 设置页 | 执行动作 | 展示按钮和结果 |

### 4.3 Java 模块结构

```text
android-app/
  app/src/main/java/com/pixelcouple/
    MainActivity.java
    WebViewActivity.java

    bridge/
      PixelBridge.java
      BridgeResult.java
      BridgeDispatcher.java

    auth/
      AuthRepository.java
      SessionManager.java

    pairing/
      PairingRepository.java
      PairingModels.java

    avatar/
      AvatarRepository.java
      AvatarModels.java

    status/
      StatusDetector.java
      UsageAccessHelper.java
      StatusMapper.java
      StatusRepository.java
      StatusUploadWorker.java
      StatusModels.java

    interaction/
      InteractionRepository.java
      InteractionModels.java

    widget/
      PixelWidgetProvider.java
      WidgetUpdateManager.java
      WidgetCache.java

    storage/
      LocalCache.java
      SecurePrefs.java

    network/
      ApiClient.java
      ApiResult.java
      AuthInterceptor.java

    analytics/
      AnalyticsRepository.java

    common/
      TimeFormatter.java
      JsonUtil.java
      AppConstants.java

  app/src/main/assets/
    vue/
      index.html
      assets/
    pixel/
      avatars/
      status_props/
      widget/
    status_map_cn.json
```

### 4.4 Vue 模块结构

```text
vue-ui/
  src/
    pages/
      Home.vue
      Pairing.vue
      AvatarSelect.vue
      PermissionGuide.vue
      Privacy.vue
      Settings.vue
      NotPaired.vue

    components/
      PixelAvatar.vue
      StatusCard.vue
      HeartButton.vue
      EmptyState.vue
      ErrorState.vue
      LoadingState.vue

    bridge/
      native.ts
      bridgeTypes.ts

    stores/
      appStore.ts

    assets/
      styles/
      images/

  package.json
  vite.config.ts
```

### 4.5 WebView 加载方式

Vue 构建产物放入：

```text
android-app/app/src/main/assets/vue/
```

推荐加载本地资源方式：

```text
https://appassets.androidplatform.net/assets/vue/index.html
```

优先使用 `WebViewAssetLoader`，不要直接用远程 Web 页面，也不要让 WebView 加载外部网页。

### 4.6 Vue Router 设置

Vue Router 建议使用 hash 模式：

```ts
createWebHashHistory()
```

原因：WebView 加载本地 HTML 时，普通 history 模式容易出现刷新路径解析问题。

### 4.7 WebView 安全原则

必须遵守：

1. 只加载本地 Vue 资源。
2. 禁止加载任意外部 URL。
3. JSBridge 只暴露必要方法。
4. token 只保存在 Java，不传给 Vue。
5. 后端请求由 Java Repository 统一发起。
6. Release 包关闭 WebView 调试。
7. 禁止混合内容。
8. 禁止不必要的文件访问能力。

建议设置：

```java
webView.getSettings().setJavaScriptEnabled(true);
webView.getSettings().setDomStorageEnabled(true);
webView.getSettings().setAllowFileAccess(false);
webView.getSettings().setAllowContentAccess(false);
webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

if (!BuildConfig.DEBUG) {
    WebView.setWebContentsDebuggingEnabled(false);
}
```

---

## 5. JSBridge 设计

### 5.1 Bridge 原则

Bridge 是 Vue 和 Java 的唯一通信通道。

原则：

1. 方法少。
2. 返回格式统一。
3. Vue 不直接知道接口细节。
4. Vue 不拿 token。
5. Java 是唯一数据源。
6. 所有敏感动作都在 Java 内校验。

### 5.2 第一版 Bridge 方法

```js
window.PixelBridge.getInitialState()
window.PixelBridge.createPairInvite()
window.PixelBridge.acceptPairInvite(code)
window.PixelBridge.refreshPartnerStatus()
window.PixelBridge.selectAvatar(avatarId)
window.PixelBridge.pauseSharing()
window.PixelBridge.resumeSharing()
window.PixelBridge.sendHeart()
window.PixelBridge.getUnreadInteractions()
window.PixelBridge.openUsageAccessSettings()
window.PixelBridge.checkUsageAccess()
window.PixelBridge.unlinkCouple()
window.PixelBridge.deleteAccount()
window.PixelBridge.logEvent(eventName, propertiesJson)
```

第一版控制在 10–15 个方法，不要无限增加。

### 5.3 统一返回格式

```json
{
  "success": true,
  "data": {},
  "errorCode": null,
  "message": null
}
```

错误示例：

```json
{
  "success": false,
  "data": null,
  "errorCode": "COUPLE_NOT_FOUND",
  "message": "还没有完成配对"
}
```

### 5.4 首页初始数据示例

```json
{
  "user": {
    "nickname": "我",
    "avatarId": "avatar_01"
  },
  "partner": {
    "nickname": "TA",
    "avatarId": "avatar_03",
    "statusCode": "watching_show",
    "statusText": "追剧中",
    "updatedText": "5 分钟前",
    "isPaused": false
  },
  "self": {
    "shareEnabled": true,
    "usageAccessGranted": true,
    "currentStatusCode": "short_video"
  },
  "ui": {
    "isPaired": true,
    "hasUnreadHeart": false
  }
}
```

---

## 6. 后端：自部署 Supabase

### 6.1 第一版启用组件

| 组件 | 是否启用 | 用途 |
|---|---:|---|
| PostgreSQL | 启用 | 保存业务数据 |
| Auth | 启用 | 匿名登录 |
| PostgREST | 启用 | 基础 REST API |
| Edge Functions | 启用 | 配对、解绑、删除等敏感操作 |
| Studio | 开发期启用 | 管理表结构和数据 |
| Realtime | 第一版可不开 | 首页主动拉取即可 |
| Storage | 第一版不开 | 素材打包进 APK |
| Logflare / 高级 Analytics | 第一版不开 | 用 `analytics_events` 表代替 |

### 6.2 服务器建议

| 项目 | 建议 |
|---|---|
| 地区 | 香港 / 新加坡 / 日本 |
| CPU | 2 核起步，推荐 4 核 |
| 内存 | 4GB 起步，推荐 8GB |
| 硬盘 | 80GB SSD 推荐 |
| 系统 | Ubuntu 22.04 / 24.04 LTS |
| 部署方式 | Docker Compose |
| 反向代理 | Caddy |
| HTTPS | Let’s Encrypt 自动证书 |

### 6.3 最小部署步骤

```bash
git clone --depth 1 https://github.com/supabase/supabase
mkdir supabase-project
cp -rf supabase/docker/* supabase-project
cp supabase/docker/.env.example supabase-project/.env
cd supabase-project
```

修改 `.env` 中至少这些项：

```env
POSTGRES_PASSWORD=强密码
JWT_SECRET=强随机字符串
DASHBOARD_USERNAME=强用户名
DASHBOARD_PASSWORD=强密码
SUPABASE_PUBLIC_URL=https://api.your-domain.com
API_EXTERNAL_URL=https://api.your-domain.com
SITE_URL=https://api.your-domain.com
```

启动：

```bash
docker compose pull
docker compose up -d
docker compose ps
```

### 6.4 安全配置原则

1. 只开放 80 / 443 到公网。
2. 不开放 PostgreSQL 5432 到公网。
3. Studio 不建议长期公开暴露。
4. 所有默认密码必须修改。
5. `.env` 不提交到 Git。
6. 每日自动备份 PostgreSQL。
7. 第一个月锁定镜像版本，不频繁升级。

---

## 7. 状态类别设计

### 7.1 第一版状态枚举

| status_code | 中文展示 | 像素表现 |
|---|---|---|
| `short_video` | 刷短视频中 | 小人躺着 / 坐着刷手机 |
| `watching_show` | 追剧中 | 小人抱抱枕看电视 |
| `reading` | 阅读中 | 拿书 / 电子书 |
| `music` | 听歌中 | 戴耳机，有音符 |
| `gaming` | 游戏中 | 拿手柄 / 键盘 |
| `social` | 在线中 | 小气泡，不显示聊天对象 |
| `online` | 在线 | 普通站立 / 呼吸 |
| `resting` | 休息中 | 睡觉 / 月亮 |
| `offline` | 暂未更新 | 灰色云朵 |
| `paused` | 已暂停共享 | 小锁 / 暂停符号 |

### 7.2 映射方式

```text
具体包名
  ↓ Java 本地 status_map_cn.json
抽象 status_code
  ↓ 上传后端
对方只看到中文状态 + 像素动作
```

示例：

```json
{
  "version": 1,
  "apps": [
    {
      "packages": ["com.ss.android.ugc.aweme"],
      "status": "short_video"
    },
    {
      "packages": ["com.qiyi.video"],
      "status": "watching_show"
    },
    {
      "packages": ["com.netease.cloudmusic"],
      "status": "music"
    }
  ],
  "defaultStatus": "online"
}
```

### 7.3 隐私规则

服务器不保存：

- 包名。
- App 名称。
- 使用时长。
- 状态历史。
- 聊天对象。
- 浏览内容。

服务器只保存：

- `user_id`
- `couple_id`
- `status_code`
- `status_updated_at`
- `expires_at`
- `is_paused`

---

## 8. 数据库设计

### 8.1 第一版表清单

| 表名 | 是否需要 | 用途 |
|---|---:|---|
| `users` | 需要 | 用户资料 |
| `couples` | 需要 | 情侣关系 |
| `pair_invites` | 需要 | 配对邀请码 |
| `avatars` | 需要 | 预设角色 |
| `user_avatar` | 需要 | 用户当前角色 |
| `current_status` | 需要 | 当前抽象状态 |
| `status_privacy_settings` | 需要 | 共享和类别开关 |
| `interactions` | 需要 | 爱心互动 |
| `analytics_events` | 需要 | 基础埋点 |
| `device_tokens` | 第一版不需要 | 第二版推送再加 |

### 8.2 表结构草案

#### users

```sql
create table users (
  id uuid primary key default gen_random_uuid(),
  auth_user_id uuid unique not null,
  nickname text,
  created_at timestamptz not null default now(),
  deleted_at timestamptz
);
```

#### couples

```sql
create table couples (
  id uuid primary key default gen_random_uuid(),
  user_a_id uuid not null references users(id),
  user_b_id uuid not null references users(id),
  status text not null default 'active',
  created_at timestamptz not null default now(),
  unlinked_at timestamptz,
  unlinked_by uuid references users(id),
  constraint couples_status_check check (status in ('active', 'unlinked'))
);

create index idx_couples_user_a on couples(user_a_id);
create index idx_couples_user_b on couples(user_b_id);
create index idx_couples_status on couples(status);
```

#### pair_invites

```sql
create table pair_invites (
  id uuid primary key default gen_random_uuid(),
  code_hash text unique not null,
  created_by uuid not null references users(id),
  accepted_by uuid references users(id),
  expires_at timestamptz not null,
  used_at timestamptz,
  created_at timestamptz not null default now()
);

create unique index idx_pair_invites_code_hash on pair_invites(code_hash);
create index idx_pair_invites_expires_at on pair_invites(expires_at);
```

#### avatars

```sql
create table avatars (
  id text primary key,
  name text not null,
  asset_key text not null,
  is_active boolean not null default true,
  sort_order int not null default 0
);
```

#### user_avatar

```sql
create table user_avatar (
  user_id uuid primary key references users(id),
  avatar_id text not null references avatars(id),
  variant_id text,
  updated_at timestamptz not null default now()
);
```

#### current_status

```sql
create table current_status (
  user_id uuid primary key references users(id),
  couple_id uuid references couples(id),
  status_code text not null,
  source text not null default 'usage_events',
  status_updated_at timestamptz not null,
  expires_at timestamptz not null,
  is_paused boolean not null default false,
  updated_at timestamptz not null default now(),
  constraint current_status_code_check check (
    status_code in (
      'short_video',
      'watching_show',
      'reading',
      'music',
      'gaming',
      'social',
      'online',
      'resting',
      'offline',
      'paused'
    )
  )
);

create index idx_current_status_couple_id on current_status(couple_id);
create index idx_current_status_expires_at on current_status(expires_at);
```

#### status_privacy_settings

```sql
create table status_privacy_settings (
  user_id uuid primary key references users(id),
  share_enabled boolean not null default true,
  allowed_statuses jsonb not null default '["short_video", "watching_show", "reading", "music", "gaming", "social", "online", "resting"]',
  hide_unknown boolean not null default true,
  updated_at timestamptz not null default now()
);
```

#### interactions

```sql
create table interactions (
  id uuid primary key default gen_random_uuid(),
  couple_id uuid not null references couples(id),
  from_user_id uuid not null references users(id),
  to_user_id uuid not null references users(id),
  type text not null default 'heart',
  created_at timestamptz not null default now(),
  seen_at timestamptz,
  expires_at timestamptz not null default now() + interval '30 days',
  constraint interaction_type_check check (type in ('heart'))
);

create index idx_interactions_to_user on interactions(to_user_id);
create index idx_interactions_couple_created on interactions(couple_id, created_at desc);
```

#### analytics_events

```sql
create table analytics_events (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references users(id),
  event_name text not null,
  properties jsonb not null default '{}',
  created_at timestamptz not null default now()
);

create index idx_analytics_event_name on analytics_events(event_name);
create index idx_analytics_created_at on analytics_events(created_at);
```

---

## 9. API / Edge Function 清单

第一版可以优先使用 Edge Functions 包装敏感业务逻辑。

| 模块 | 接口 | 方法 | 路径 | 说明 | 第一版是否必须 |
|---|---|---:|---|---|---:|
| Auth | 匿名登录 | Java 调 Supabase Auth | `/auth/v1` | 创建匿名账号 | 是 |
| Me | 获取当前用户 | GET | `/me` | 获取用户、角色、隐私设置 | 是 |
| Pairing | 创建邀请码 | POST | `/pair-invites` | 生成 8 位邀请码 | 是 |
| Pairing | 接受邀请码 | POST | `/pair-invites/accept` | 创建情侣关系 | 是 |
| Pairing | 获取情侣关系 | GET | `/couple` | 获取 partner | 是 |
| Pairing | 解绑 | POST | `/couple/unlink` | 单方解除配对 | 是 |
| Avatar | 获取角色列表 | GET | `/avatars` | 返回预设角色 | 是 |
| Avatar | 设置角色 | PUT | `/me/avatar` | 保存用户角色 | 是 |
| Status | 上传当前状态 | PUT | `/status/current` | 上传抽象状态码 | 是 |
| Status | 获取对方状态 | GET | `/status/partner` | 首页展示 | 是 |
| Sharing | 暂停共享 | POST | `/sharing/pause` | 停止共享 | 是 |
| Sharing | 恢复共享 | POST | `/sharing/resume` | 恢复共享 | 是 |
| Interaction | 发送爱心 | POST | `/interactions/heart` | 写入互动记录 | 是 |
| Interaction | 获取未读互动 | GET | `/interactions/latest` | App 内提示 | 是 |
| Widget | 获取 Widget 数据 | GET | `/widget` | Widget 展示 | 是 |
| Privacy | 更新隐私设置 | PUT | `/privacy/status` | 类别开关 | 是 |
| Account | 注销账号 | DELETE | `/me/account` | 删除账号和数据 | 是 |
| Analytics | 写埋点 | POST | `/analytics/events` | 记录关键事件 | 是 |
| Push | 注册 token | PUT | `/device-tokens` | 第二版推送 | 否 |

---

## 10. 像素资产方案

### 10.1 原则

第一版不是 App 内 AI 生成角色，而是开发期使用 Codex / ImageGen 生成素材，再打包进 APK。

### 10.2 资产压缩方案

| 资产类型 | 数量 | 说明 |
|---|---:|---|
| 基础角色 | 8 个 | 每个角色 1 张基础站立图 |
| 状态道具 | 10 个 | 手机、电视、书、耳机、手柄、气泡、月亮、锁、云朵、爱心 |
| 关键状态全身图 | 3–4 类 | `short_video`、`watching_show`、`gaming`、`resting` 优先 |
| Widget 小图 | 10 个 | 每个状态一张简化图 |
| 爱心动效 | 3 帧 | App 内互动动画 |

### 10.3 资产目录

```text
app/src/main/assets/pixel/
  style_guide.md

  avatars/
    avatar_01_base.png
    avatar_02_base.png
    ...
    avatar_08_base.png

  status_props/
    prop_short_video_phone.png
    prop_watching_show_tv.png
    prop_reading_book.png
    prop_music_headphone.png
    prop_gaming_gamepad.png
    prop_social_bubble.png
    prop_resting_moon.png
    prop_paused_lock.png
    prop_offline_cloud.png
    prop_heart.png

  widget/
    widget_short_video.png
    widget_watching_show.png
    widget_reading.png
    widget_music.png
    widget_gaming.png
    widget_social.png
    widget_online.png
    widget_resting.png
    widget_offline.png
    widget_paused.png
```

### 10.4 ImageGen 提示词模板

```text
Create a transparent-background pixel art sprite sheet for a cute couple companion Android app.

Style:
- 32-bit pixel art
- soft pastel palette
- cozy, romantic, low-pressure mood
- no text inside the image
- transparent background
- clean silhouette
- suitable for Android widget and app UI
- consistent character proportions across all frames

Character:
- small cute pixel person
- neutral youthful style
- oversized hoodie
- simple face
- rounded shape

States:
1. short video: sitting with phone, relaxed, small vertical-video icon
2. watching show: sitting with pillow and popcorn, small TV glow
3. reading: holding book
4. music: wearing headphones
5. gaming: holding game controller
6. resting: sleeping with moon icon
7. online: idle standing
8. paused: lock icon overlay
9. offline: gray cloud icon
10. heart interaction: small floating heart animation

Output:
- transparent PNG
- 256x256 per sprite
- no text
- consistent pixel grid
```

---

## 11. 首页与页面设计

### 11.1 首页布局

Vue 首页一屏展示：

1. 顶部：情侣状态、最近更新时间。
2. 中间：对方像素小人 + 当前状态文案。
3. 次级：自己的共享状态卡片。
4. 底部：爱心按钮、暂停共享、隐私设置入口。
5. 异常态：未配对、未授权、对方暂停、对方离线、网络错误。

### 11.2 配对页

支持：

- 创建邀请码。
- 输入邀请码。
- 展示邀请码有效期。
- 错误态：过期、已使用、自己配自己、任一方已配对。

### 11.3 权限说明页

必须说明：

```text
我们需要“使用情况访问权限”，是为了把你的手机使用状态抽象成“刷短视频中 / 追剧中 / 阅读中 / 听歌中 / 游戏中”等像素状态。

我们不会向对方展示具体 App 名称、使用内容、聊天对象、浏览记录或使用时长。

你可以随时暂停共享、解绑或删除数据。
```

### 11.4 隐私设置页

包含：

- 当前共享状态开关。
- 状态类别开关。
- Usage Access 权限状态。
- 暂停共享。
- 解绑。
- 删除账号。
- 内测数据说明。

---

## 12. Widget 设计

### 12.1 技术方案

```text
AppWidgetProvider + RemoteViews
```

Vue 不参与 Widget。

### 12.2 第一版能力

| 项 | 实现 |
|---|---|
| 尺寸 | 2x2 |
| 内容 | 对方像素头像、状态文案、最近更新时间 |
| 数据来源 | Java 本地缓存 + 后端 `/widget` 拉取 |
| 刷新 | 点击刷新 / App 打开刷新 / 系统低频刷新 |
| 点击 | 打开 App 首页 |
| 失败态 | “暂未更新，点按刷新” |

### 12.3 不做内容

- Widget 动画。
- 多尺寸复杂适配。
- Widget 直接互动。
- Widget 实时刷新。

---

## 13. 第一版无推送设计

### 13.1 为什么不做推送

第一版为了减少申请和平台接入，不做：

- FCM。
- 极光。
- 个推。
- 华为 / 小米 / OPPO / vivo / 荣耀厂商通道。
- 通知权限。
- device token 注册。

状态展示不依赖推送，靠首页和 Widget 拉取即可。

### 13.2 爱心互动替代方案

```text
A 点击爱心
  ↓
Java 调 POST /interactions/heart
  ↓
服务器写入 interactions
  ↓
B 下次打开 App 调 GET /interactions/latest
  ↓
Vue 首页播放爱心动画 / 显示未读提示
```

---

## 14. 4 周开发排期

### 第 1 周：基础设施 + 客户端壳 + 技术验证

| 日程 | 任务 |
|---|---|
| D1 | 购买服务器和域名，配置 DNS，准备 Android 项目和 Vue 项目 |
| D2 | 部署 Supabase Docker，配置 Caddy HTTPS，跑通 Studio |
| D3 | Java WebView 壳加载本地 Vue 页面，建立 JSBridge 最小通信 |
| D4 | Supabase 匿名登录，Java 保存 session，Vue 调 getInitialState |
| D5 | Usage Access 权限检测和跳转系统设置 |
| D6 | UsageEvents 读取最近前台 App PoC |
| D7 | Codex/ImageGen 生成基础像素素材，第一周整合测试 |

交付物：

- `https://api.xxx.com` 可访问。
- 自部署 Supabase 可用。
- APK 可启动并加载 Vue 首页。
- JSBridge 可通信。
- 本机可识别 Usage Access 状态。
- 可打印最近前台 App 包名。

风险：

- Supabase 自部署卡住。
- WebView 与 Vue 本地加载配置卡住。
- Usage Access 在国产机入口不同。

降级：

- 若 D3 仍未跑通自部署 Supabase，临时用托管 Supabase 完成客户端主链路，后续再迁回。

### 第 2 周：配对 + 状态主链路

| 日程 | 任务 |
|---|---|
| D8 | `users`、`pair_invites`、`couples` 表和创建邀请码函数 |
| D9 | 接受邀请码，创建情侣关系，处理异常态 |
| D10 | 解绑逻辑和设置页入口 |
| D11 | 角色表、角色选择页、角色保存 |
| D12 | `status_map_cn.json` 和状态映射逻辑 |
| D13 | `current_status` 上传，前台状态变化触发上传 |
| D14 | 获取对方状态，Vue 首页展示真实数据 |

交付物：

- 两台 Android 手机可以匿名登录。
- 可以通过邀请码配对。
- 可以选择像素形象。
- 一方状态可上传，另一方首页可看到。

### 第 3 周：体验闭环 + Widget + 隐私

| 日程 | 任务 |
|---|---|
| D15 | 暂停共享 / 恢复共享 |
| D16 | 隐私设置页和类别开关 |
| D17 | 爱心互动，App 内未读提醒和动画 |
| D18 | Widget 原生实现，读取本地缓存展示状态 |
| D19 | Widget 点击打开 App / 点击刷新 |
| D20 | WorkManager 低频状态上传，后台降级策略 |
| D21 | 全链路错误态和空状态补齐 |

交付物：

- 暂停共享可用。
- 爱心互动可用。
- Widget 可显示状态。
- 权限和隐私说明完整。

### 第 4 周：测试、修复、内测准备

| 日程 | 任务 |
|---|---|
| D22 | 状态识别准确率测试，补充短视频 / 追剧包名映射 |
| D23 | 小米 / Redmi 测试 |
| D24 | 华为 / 荣耀 测试 |
| D25 | OPPO / vivo 测试 |
| D26 | 注销账号 / 删除数据联调 |
| D27 | 埋点补齐，`analytics_events` 验证 |
| D28 | 隐私说明、安装说明、内测说明文案 |
| D29 | Release APK 签名，安装包测试 |
| D30 | 双机全链路验收，整理 known issues |

交付物：

- 可内部测试的 APK。
- 内测安装说明。
- 隐私说明。
- 已知问题列表。
- 验收记录。

---

## 15. 30 天每日计划表

| 天数 | 当天目标 | Android / Java 任务 | Vue 任务 | 后端任务 | 验收标准 |
|---:|---|---|---|---|---|
| D1 | 项目启动 | 创建 Java Android 项目 | 创建 Vue + Vite 项目 | 购买服务器 / 域名 | 空 App 和空 Vue 可运行 |
| D2 | HTTPS 后端 | 配置 API 地址 | 无 | Supabase + Caddy HTTPS | `https://api.xxx.com` 可访问 |
| D3 | WebView 壳 | 加载本地 Vue，JSBridge PoC | 首页 mock | 无 | Vue 可调用 Java 返回字符串 |
| D4 | 登录 | Java 接 Supabase Auth | 登录加载页 | users 初始化 | 可匿名登录并保存 session |
| D5 | 权限 | Usage Access 检测和跳转 | 权限说明页 | 无 | 能判断权限是否开启 |
| D6 | 状态 PoC | UsageEvents 读取包名 | Debug 展示 | 无 | 可打印最近前台包名 |
| D7 | 素材 | 资产加载器 | 像素展示组件 | avatars seed | Vue 能展示像素角色 |
| D8 | 邀请码 | PairingRepository | 创建邀请码页 | pair_invites + function | A 可生成邀请码 |
| D9 | 接受配对 | accept invite | 输入邀请码页 | couples 创建 | A/B 可配对 |
| D10 | 解绑 | unlink | 设置页按钮 | unlink function | 单方可解绑 |
| D11 | 角色选择 | AvatarRepository | 角色选择页 | user_avatar | 双方可选角色 |
| D12 | 状态映射 | StatusMapper | 状态展示组件 | 无 | 包名可变成状态码 |
| D13 | 上传状态 | StatusRepository 上传 | 自己状态卡 | current_status | A 状态写入服务端 |
| D14 | 对方状态 | partner status 拉取 | 首页真实展示 | 查询函数 | B 可看到 A 状态 |
| D15 | 暂停共享 | pause/resume | 暂停态 UI | privacy update | 对方看到已暂停 |
| D16 | 隐私设置 | 类别开关逻辑 | 隐私页 | settings 表 | 类别开关生效 |
| D17 | 爱心 | InteractionRepository | 爱心动画 | interactions | A 可发，B 可见未读 |
| D18 | Widget | WidgetProvider | 无 | widget API | Widget 可显示缓存状态 |
| D19 | Widget 刷新 | WidgetUpdateManager | 无 | 无 | 点击 Widget 打开 App |
| D20 | 后台任务 | WorkManager | 后台状态提示 | 无 | 低频上传可运行 |
| D21 | 整合 | 修主链路 bug | 错误态补齐 | 修函数 | 全链路可演示 |
| D22 | 准确率 | 补映射和日志 | Debug 页 | 无 | 常见 App 分类基本可用 |
| D23 | 小米测试 | 权限/后台/Widget | 适配提示 | 无 | 小米流程可跑通 |
| D24 | 华为测试 | 权限/后台/Widget | 适配提示 | 无 | 华为流程可跑通 |
| D25 | OPPO/vivo 测试 | 权限/后台/Widget | 适配提示 | 无 | 至少一台跑通 |
| D26 | 删除数据 | deleteAccount | 二次确认页 | 删除函数 | 数据可删除 |
| D27 | 埋点 | AnalyticsRepository | 无 | analytics_events | 关键事件可记录 |
| D28 | 文案 | 内测说明入口 | 隐私/安装说明页 | 无 | 文案完整 |
| D29 | 打包 | release 签名 | 静态资源压缩 | 环境检查 | APK 可安装 |
| D30 | 验收 | 双机回归 | UI 修补 | 数据清理 | 达到验收清单 |

---

## 16. 验收标准

### 16.1 功能验收

- [ ] APK 可以直接安装。
- [ ] App 启动后能加载本地 Vue 页面。
- [ ] Java 与 Vue 通过 JSBridge 正常通信。
- [ ] 两台 Android 手机可以匿名注册 / 登录。
- [ ] A 可以创建邀请码。
- [ ] B 可以输入邀请码配对。
- [ ] 任意一方可以解绑。
- [ ] 双方可以选择像素形象。
- [ ] 用户可以授权 Usage Access。
- [ ] Java 可以读取最近前台 App。
- [ ] 本地可以将前台 App 映射为抽象状态。
- [ ] 状态上传到 `current_status`。
- [ ] 对方首页可以看到状态变化。
- [ ] 支持 `short_video` 和 `watching_show`。
- [ ] 对方看不到具体 App 名称。
- [ ] 对方看不到使用时长。
- [ ] 不存在状态历史页面。
- [ ] 用户可以暂停共享。
- [ ] 暂停后对方看到“已暂停共享”。
- [ ] 用户可以发送爱心。
- [ ] 对方打开 App 后可以看到未读爱心。
- [ ] Widget 可以显示对方状态。
- [ ] 用户可以注销账号并删除数据。
- [ ] 基础埋点可记录关键事件。

### 16.2 隐私验收

- [ ] 授权 Usage Access 前有显著说明。
- [ ] 文案明确“不展示具体 App 名称”。
- [ ] 文案明确“不展示使用内容、聊天对象、浏览记录、使用时长”。
- [ ] 服务器不保存包名。
- [ ] 服务器不保存状态历史。
- [ ] 服务器只保存当前抽象状态。
- [ ] 用户可以暂停共享。
- [ ] 用户可以解绑。
- [ ] 用户可以删除账号数据。

### 16.3 技术验收

- [ ] API 通过 HTTPS 访问。
- [ ] Android App 不允许明文 HTTP。
- [ ] Release 包关闭 WebView 调试。
- [ ] WebView 不加载外部网页。
- [ ] token 不传给 Vue。
- [ ] PostgreSQL 不暴露到公网。
- [ ] Supabase 默认密码全部修改。
- [ ] 有数据库备份脚本。

---

## 17. 风险与降级方案

| 风险 | 等级 | 触发条件 | 解决方案 | 降级方案 | 最晚决策时间 |
|---|---|---|---|---|---|
| 自部署 Supabase 卡住 | 高 | D3 仍不能稳定读写 | 简化组件，只保留核心服务 | 临时用托管 Supabase | D3 |
| HTTPS 配置卡住 | 中 | 域名访问失败 | 检查 DNS / Caddy / 防火墙 | 临时本机调试 HTTP，不给内测用户 | D2 |
| WebView + Vue 通信混乱 | 中 | JSBridge 方法增多失控 | 统一 JSON 协议 | 关键页改 Java 原生 | D7 |
| Usage Access 不稳定 | 高 | 不同机型读不到事件 | 扩大查询窗口，做 debug 页 | 只在 App 打开时刷新状态 | D6 |
| 国产机后台限制 | 高 | 锁屏后不更新 | WorkManager + 打开 App 主动刷新 | 明确非实时，过期显示暂未更新 | D20 |
| Widget 更新不稳定 | 中 | 不自动刷新 | 本地缓存 + 点击刷新 | 只支持点击打开 App 刷新 | D19 |
| 素材来不及 | 中 | 状态动作图不完整 | 基础角色 + 状态道具覆盖 | 8 角色静态图 + 10 状态 icon | D7 |
| 状态分类不准 | 中 | 未识别 App 多 | 扩充本地 map | 未识别统一 online | D22 |
| UI 做不完 | 中 | Vue 页面过多 | 首页、配对、隐私优先 | 设置页先做简单列表 | D21 |
| 隐私文案不清楚 | 高 | 用户不敢授权 | 授权前解释抽象、对等、可撤回 | 内测前重新写文案 | D28 |
| 一个月测试不足 | 高 | 多机型 bug 多 | 缩小测试机型范围 | 只支持 3–5 台内测机 | D30 |

---

## 18. 埋点设计

第一版使用 `analytics_events` 表，不接第三方统计 SDK。

必埋事件：

- `app_opened`
- `auth_created`
- `pair_invite_created`
- `pair_invite_accepted`
- `pair_success`
- `usage_access_prompt_viewed`
- `usage_access_granted`
- `usage_access_denied`
- `avatar_selected`
- `status_detected`
- `status_uploaded`
- `partner_status_viewed`
- `share_paused`
- `share_resumed`
- `heart_sent`
- `heart_seen`
- `widget_added`
- `widget_refreshed`
- `unlink_done`
- `account_deleted`

核心指标：

- 配对完成率。
- Usage Access 授权率。
- 状态上传成功率。
- 对方状态查看频次。
- D1 / D7 内测留存。
- 爱心互动率。
- Widget 添加率。
- 暂停共享率。
- 解绑率。
- 注销率。

---

## 19. AI 辅助开发方法

### 19.1 适合让 AI 生成的内容

- Supabase SQL migration。
- Edge Function 初稿。
- Java Repository 模板。
- Java JSBridge 模板。
- Vue 页面组件。
- UsageStats detector 初稿。
- WorkManager Worker 初稿。
- Widget RemoteViews 初稿。
- `status_map_cn.json` 初稿。
- ImageGen 像素素材提示词。
- 测试 checklist。
- 隐私说明文案。

### 19.2 必须自己检查的内容

- 服务器是否暴露敏感端口。
- `.env` 是否泄露。
- HTTPS 是否真正生效。
- Vue 是否拿到了 token。
- JSBridge 是否暴露过多能力。
- 是否真的没有上传包名。
- 删除账号是否删除完整。
- Widget 是否真机可用。
- Usage Access 文案是否清楚。
- 状态是否会被用户理解成监控。

### 19.3 每天给 AI 的任务格式

```text
目标：实现 Java Bridge 的 getInitialState。
现有结构：bridge/PixelBridge.java, status/StatusRepository.java, auth/AuthRepository.java。
输入：无。
输出：统一 JSON 字符串。
约束：不要把 token 返回给 Vue；错误码必须枚举；方法数量控制；不可访问外部网页。
请生成：Java 方法、返回模型、Vue native.ts 调用示例、基本测试步骤。
不要生成：无关页面、无关依赖。
```

---

## 20. 第二版延期内容

第二版再考虑：

| 内容 | 第二版处理 |
|---|---|
| 系统推送 | 接极光 / 个推或腾讯移动推送 |
| 厂商通道 | 华为 / 小米 / OPPO / vivo / 荣耀逐个申请 |
| 通知权限 | 加入通知权限引导 |
| 应用商店上架 | 准备隐私政策、备案、软著等材料 |
| 手机号 / 第三方登录 | 看内测反馈再决定 |
| 状态 Realtime | 有必要再加，不作为第一版核心 |
| 像素房间 | 作为留存功能 |
| 会员 / 装扮付费 | 验证后再做 |
| iOS | Android 验证成立后再设计手动状态方案 |

---

## 21. 最终执行顺序

第一阶段只做 6 件事：

```text
1. 买香港 / 新加坡云服务器
2. 买域名并解析到服务器
3. 用 Caddy 配 HTTPS
4. Docker Compose 自部署 Supabase
5. 做 Java 壳 + Vue 纯界面 + JSBridge
6. 打包 APK 给内部测试用户安装
```

核心闭环：

```text
匿名登录
→ 邀请码配对
→ 选择像素形象
→ 授权 Usage Access
→ Java 本地识别前台 App
→ 本地映射成抽象状态
→ HTTPS 上传 current_status
→ 对方首页 / Widget 拉取
→ Vue 展示像素状态
→ 可暂停共享
→ 可发爱心
→ 可解绑 / 删除数据
```

第一版的成功标准不是“实时、完整、商用”，而是：

> **两台 Android 手机可以通过 APK 内测安装，在不展示具体 App 和使用时长的前提下，通过抽象像素状态感受到对方的日常陪伴。**
