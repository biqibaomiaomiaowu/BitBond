# Pixel Asset Style Guide

生成日期：2026-05-25

这些素材来自 `imagegen` 生成结果，并经过子 agent 审查。当前版本可用于首页动态房间初稿；正式版本仍可继续统一角色锚点、道具视觉中心和像素硬边。

## 使用范围

- `room/room_main.png`：首页俯视房间底图。
- `avatars/avatar_cat_walk_down_strip.png`：小猫走向候选区域的 4 帧动画条。
- `avatars/avatar_cat_listen_music_strip.png`：小猫到达音乐区域后的听歌 4 帧动画条。
- `status_props/status_props_sheet.png`：10 个抽象状态道具图标条。

## 动画规范

- 角色 strip 为 4 帧横向排列。
- 每帧尺寸为 `96x96`。
- CSS/WebView 使用时按固定帧宽切换 `background-position`。
- 角色先移动到候选家具或区域，再切换到对应状态动作。
- 初稿已覆盖音乐状态：先走到音响区域，再播放戴耳机听歌动作。

## 状态图标顺序

`status_props/status_props_sheet.png` 为 10 帧横向排列，每帧 `80x80`，顺序如下：

1. `short_video`
2. `watching_show`
3. `reading`
4. `music`
5. `gaming`
6. `social`
7. `online`
8. `resting`
9. `offline`
10. `paused`

## 约束

- 不展示具体 App、聊天对象、浏览内容或使用时长。
- 只表达抽象状态。
- 保持透明 PNG 叠加，不加白色圆角卡片底。
- 页面渲染时使用 `image-rendering: pixelated`。
