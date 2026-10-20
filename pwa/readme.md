# PWA 版 · 阿Z混淆

在浏览器中「安装到桌面 / 添加到主屏幕」使用，支持离线打开。  
本目录为独立副本，**不修改**仓库根目录的 `index.html`。

## 目录结构

```
pwa/
  index.html              # 业务页副本 + PWA meta / SW 注册
  manifest.webmanifest    # 应用名称、图标、standalone
  sw.js                   # 离线缓存（HTML 网络优先，静态资源缓存优先）
  icon-192.png            # 192 图标
  icon-512.png            # 512 图标
  readme.md
```

## 本地预览

PWA 需通过 HTTP 访问（不要直接双击打开 HTML）：

```bash
# 在仓库根目录执行
npx --yes serve pwa -p 5173
```

浏览器打开 `http://localhost:5173`：

- **Chrome / Edge**：地址栏「安装应用」，或菜单 → 安装
- **手机**：用同一局域网地址打开 → 浏览器菜单 →「添加到主屏幕」

> 正式环境请部署到 HTTPS；`localhost` 可免 HTTPS 调试。

## 同步网页逻辑

根目录 `index.html` 有改动时，把内容同步到 `pwa/index.html`，并保留其中的：

- `<link rel="manifest" …>` 等 PWA meta
- 文末 `serviceWorker.register('./sw.js')` 注册逻辑

改完静态资源后，如需强制刷新缓存，把 `sw.js` 里的 `CACHE_NAME` 版本号加一（例如 `az-img-pwa-v1` → `v2`）。

## 说明

| 项 | 说明 |
|----|------|
| 离线 | 首次打开后缓存页面与图标，断网可再开 |
| 上架 | 不能直接上应用商店，需商店分发请看 `android/` |
| 算法 | 与根目录网页一致，纯前端处理，不上传服务器 |
