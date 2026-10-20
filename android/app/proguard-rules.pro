# WebView / JS bridge keep rules (reserved for future minify)
-keepclassmembers class com.qyan.azimg.MainActivity$AndroidBridge {
    @android.webkit.JavascriptInterface <methods>;
}
