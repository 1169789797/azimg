# 版本号管理

全仓库以根目录 **[version.properties](version.properties)** 为**唯一权威来源**，不要用多处手写相同数字。

| 字段 | 含义 | 谁在用 |
|------|------|--------|
| `VERSION_NAME` | 用户可见语义化版本，如 `1.1.0` | 网页/PWA 页脚、Android `versionName`、APK「版本名称」 |
| `VERSION_CODE` | **整数**，每次发 Android 安装包必须 **+1** | Android `versionCode`、应用商店判断「是否新版本」 |

## 发新版（推荐）

在项目根目录 PowerShell：

```powershell
# 补丁 +1（1.1.0 → 1.1.1），并自动 VERSION_CODE +1、同步 json / PWA 缓存名
.\scripts\bump-version.ps1 patch

# 或 minor / major
.\scripts\bump-version.ps1 minor
.\scripts\bump-version.ps1 major
```

脚本会：

1. 更新 `version.properties`
2. 运行 `sync-version.ps1`，写入各端 `version.json`、更新 PWA `sw.js` 缓存名
3. 在终端打印当前 **versionName** 与 **versionCode**（发 APK 前核对）

然后：

1. 在 **[CHANGELOG.md](CHANGELOG.md)** 写本条更新说明  
2. 若改了网页逻辑，按 [readme.md](readme.md) 同步 `index.html` 到 `pwa/`、`android/.../www/`  
3. 打 Android 包：`cd android` → `gradlew.bat assembleRelease`（或 `assembleDebug`）

## 只改数字、不自动 bump

编辑 `version.properties` 后执行：

```powershell
.\scripts\sync-version.ps1
```

**注意：** 只要对外发新 APK，`VERSION_CODE` 必须大于上一版，否则无法覆盖安装/上架。

## Android 两个版本号对照

| Gradle / 属性 | 安装包 / 商店里常见名称 |
|---------------|-------------------------|
| `versionName` ← `VERSION_NAME` | 版本名称（如 1.1.0） |
| `versionCode` ← `VERSION_CODE` | 版本号 / 内部版本（整数，只增不减） |

配置位置：[android/app/build.gradle.kts](android/app/build.gradle.kts) 从 `../version.properties` 读取，**不要**在 `build.gradle.kts` 里单独写死版本。

## 网页/PWA 显示

各端目录下的 `version.json` 由同步脚本生成；页面加载后请求同目录 `version.json`，在页脚显示 `v{versionName} ({versionCode})`。
