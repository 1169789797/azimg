# 阿Z混淆

纯前端图片混淆 / 反混淆，支持小番茄、PicEncrypt、默认混淆、Ybzj 等算法。  
图片只在本地处理，不会上传到服务器。

## 项目一览

| 目录 | 形态 | 说明 |
|------|------|------|
| 根目录 `index.html` | 网页 | 原始单页，浏览器直接打开或静态托管即可 |
| [`pwa/`](pwa/readme.md) | PWA | 可安装到桌面 / 主屏幕，支持离线 |
| [`android/`](android/readme.md) | Android App | Kotlin WebView 壳，可打 APK 安装 |
| [`doc/`](doc/小番茄算法.md) | 文档 | 算法规则说明（如小番茄） |

各子项目相互独立，改其中一个不会自动改另一个。

## 快速开始

**网页（根目录）**

用浏览器打开 `index.html`，或任意静态服务器托管根目录。

**PWA**

```bash
npx --yes serve pwa -p 5173
```

详见 [pwa/readme.md](pwa/readme.md)。

**Android**

```bat
cd android
gradlew.bat assembleDebug
```

详见 [android/readme.md](android/readme.md)。

## 同步说明

业务逻辑目前以根目录 `index.html` 为准。更新算法或界面后，按需同步到：

1. `pwa/index.html`（保留 PWA 的 manifest / Service Worker 相关片段）
2. `android/app/src/main/assets/www/index.html`

## 相关文档

- [小番茄算法规则](doc/小番茄算法.md)
- [PWA 说明](pwa/readme.md)
- [Android 说明](android/readme.md)
