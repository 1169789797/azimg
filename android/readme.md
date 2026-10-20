# Android 版 · 阿Z混淆（Kotlin）

Kotlin + WebView 壳，加载本地 `assets/www` 中的网页，复用现有混淆算法。  
本目录为独立工程，**不修改**仓库根目录与 `pwa/`。

## 环境要求

- JDK 17+
- Android SDK（`ANDROID_HOME` 或本目录 `local.properties` 中的 `sdk.dir`）
- 可选：Android Studio

`local.properties` 仅本机使用，已加入 `.gitignore`，示例：

```properties
sdk.dir=D\:\\Android\\Sdk
```

## 目录结构

```
android/
  app/
    src/main/
      java/com/qyan/azimg/MainActivity.kt   # WebView、相册选择、保存到相册
      assets/www/                           # 内嵌网页（index.html + ico.png）
      res/                                  # 图标、主题、布局
      AndroidManifest.xml
    build.gradle.kts
  gradle/wrapper/
  gradlew.bat
  settings.gradle.kts
  readme.md
```

应用包名：`com.qyan.azimg`  
最低系统：Android 7.0（API 24）

## 编译与安装

在 `android/` 目录下：

```bat
gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Debug APK 输出路径：

```
app/build/outputs/apk/debug/app-debug.apk
```

也可用 Android Studio 打开本目录后直接 Run。

## 原生侧能力

| 能力 | 实现 |
|------|------|
| 多选上传 | `WebChromeClient.onShowFileChooser` |
| 下载保存 | 拦截 `data:image`，写入相册 `Pictures/AzImg` |
| UI | 深色主题，适配系统栏 inset |

## 同步网页逻辑

根目录 `index.html` 有改动时，复制到：

```
app/src/main/assets/www/index.html
```

图标可继续使用同目录下的 `ico.png`（体积宜小，勿用超大原图）。

## 说明

- 当前为 **WebView 壳**，不是算法的纯 Kotlin 重写；后续若换 Capacitor 等方案可另开目录。
- 未包含 iOS 工程。
- Release 签名上架需自行配置 `signingConfigs`。
