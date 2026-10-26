# Plannie ProGuard Rules

# Keep WebView JavaScript interface
-keepclassmembers class com.havila.plannie.MainActivity$PlannieBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep FileProvider
-keep class androidx.core.content.FileProvider { *; }
