# BitBond

情侣像素陪伴 App 的 Android 内测版工程。

## D1 骨架范围

- Android：Java 原生壳项目骨架，后续接 WebView、JSBridge、Usage Access、Widget。
- Web：Vue + Vite 纯界面骨架，后续打包进 Android WebView。
- Backend：D1 只记录服务器、域名、DNS、HTTPS 准备项；Supabase 和 Caddy 从 D2 开始落地。

## 本地目录

- `android/`：Android Java App。
- `web/`：Vue + Vite 前端界面。
- `情侣像素陪伴App_Android内测版开发计划_JavaVue自部署Supabase_HTTPS版.md`：30 天开发计划。

## D1 外部准备项

这些无法由本地代码直接完成，需要在云服务商和域名服务商控制台操作：

- 购买一台香港、新加坡或日本等非中国大陆节点服务器。
- 购买或准备域名。
- 创建 `api` 子域名，例如 `api.your-domain.com`。
- 将 `api` 子域名 DNS A 记录指向服务器公网 IP。
- D2 使用 Caddy 自动签发 HTTPS 证书并反向代理 Supabase。
