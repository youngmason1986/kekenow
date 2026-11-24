# KekeNow ProGuard rules
# Keep JavascriptInterface methods so the WebView bridge is not stripped
-keepclassmembers class com.blackempire.kekenow.util.MapBridge {
    @android.webkit.JavascriptInterface <methods>;
}
