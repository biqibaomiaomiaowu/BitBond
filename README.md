# BitBond

情侣像素陪伴 App 的 Android 内测版工程。

## D1 骨架范围

- Android：Java 原生壳项目骨架，后续接 WebView、JSBridge、Usage Access、Widget。
- 网页端：Vue + Vite 纯界面骨架，后续打包进 Android WebView。
- 后端：D1 只记录服务器、域名、DNS、HTTPS 准备项；Supabase 和 Caddy 从 D2 开始落地。

## 本地目录

- `android/`：Android Java 应用。
- `web/`：Vue + Vite 前端界面。
- `情侣像素陪伴App_Android内测版开发计划_JavaVue自部署Supabase_HTTPS版.md`：30 天开发计划。

## 本地 `.env` 设置

从仓库根目录复制 `.env.example` 为本机 `.env`，只在本机或 CI Secret 中填写真实值，不要提交 `.env`，也不要把密钥输出到文档或日志。

需要准备的变量名：

- `SUPABASE_URL`
- `SUPABASE_ANON_KEY`
- `SUPABASE_SERVICE_ROLE_KEY`
- `DATABASE_URL`

Android debug 构建只应使用 `SUPABASE_URL` 和 `SUPABASE_ANON_KEY`；`SUPABASE_SERVICE_ROLE_KEY` 与 `DATABASE_URL` 仅用于本地迁移、验证或服务端管理脚本。

## 迁移与验证脚本

在根目录准备好本机 `.env` 后运行迁移。单个迁移示例：

```powershell
.\scripts\apply-supabase-migration.ps1 -MigrationPath .\supabase\migrations\20260525_010_schema.sql
```

按文件名顺序执行全部迁移：

```powershell
Get-ChildItem .\supabase\migrations\*.sql | Sort-Object Name | ForEach-Object { .\scripts\apply-supabase-migration.ps1 -MigrationPath $_.FullName }
```

迁移后运行 Supabase 冒烟验证：

```powershell
.\scripts\test-supabase-smoke.ps1
```

这些脚本只需要读取变量名对应的本地值；执行记录中不要打印真实密钥、token、密码或服务端凭据。

## D1-D14 / 前两周验收说明

D1-D14 验收覆盖本地 `.env`、Supabase 迁移/验证、Android `debug APK` 构建，以及两台 Android 设备上的配对闭环。Android 构建与双设备手工验收步骤见 `android/README.md`，其中包含 Usage Access、邀请码创建/接受、抽象状态刷新和解除配对回到未配对状态。

## D1 外部准备项

这些无法由本地代码直接完成，需要在云服务商和域名服务商控制台操作：

- 购买一台香港、新加坡或日本等非中国大陆节点服务器。
- 购买或准备域名。
- 创建 `api` 子域名，例如 `api.your-domain.com`。
- 将 `api` 子域名 DNS A 记录指向服务器公网 IP。
- D2 使用 Caddy 自动签发 HTTPS 证书并反向代理 Supabase。
