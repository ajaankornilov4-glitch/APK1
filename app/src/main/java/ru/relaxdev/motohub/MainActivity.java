package ru.relaxdev.motohub;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String HOME_URL = "https://motohub.relaxdev.ru/";
    private static final String CHANNEL_ID = "motohub_general";
    private static final int FILE_CHOOSER = 1001;
    private static final int NOTIFICATION_PERMISSION = 2001;

    private WebView webView;
    private ProgressBar progress;
    private View splash;
    private View offlineView;
    private ValueCallback<Uri[]> fileCallback;
    private final Handler handler = new Handler();
    private boolean pageFailed = false;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        configureWindow();
        createNotificationChannel();
        requestNotificationPermission();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setUserAgentString(s.getUserAgentString() + " MotoHubAndroid/1.1");

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new MotoHubBridge(), "MotoHub");

        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(64, 64);
        pp.gravity = Gravity.CENTER;
        root.addView(progress, pp);

        TextView loading = new TextView(this);
        loading.setText("ЗАГРУЗКА MOTOHUB");
        loading.setTextColor(Color.WHITE);
        loading.setTextSize(15);
        loading.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams loadingLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, 60);
        loadingLp.gravity = Gravity.CENTER;
        loadingLp.topMargin = 100;
        root.addView(loading, loadingLp);

        offlineView = createOfflineView();
        offlineView.setVisibility(View.GONE);
        root.addView(offlineView, new FrameLayout.LayoutParams(-1, -1));

        splash = createSplashView();
        root.addView(splash, new FrameLayout.LayoutParams(-1, -1));

        setContentView(root);

        // Android 15/target 35 uses edge-to-edge by default. Apply real system-bar
        // insets so the web content never hides under the status bar.
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                    WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            webView.setPadding(0, bars.top, 0, bars.bottom);
            return insets;
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(Uri.parse(url));
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                pageFailed = false;
                progress.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                pageFailed = false;
                progress.setVisibility(View.GONE);
                loading.setVisibility(View.GONE);
                offlineView.setVisibility(View.GONE);
                installNativeMenuButton(view);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    pageFailed = true;
                    progress.setVisibility(View.GONE);
                    if (!isOnline()) offlineView.setVisibility(View.VISIBLE);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                              FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;

                Intent intent;
                try {
                    intent = params.createIntent();
                    startActivityForResult(intent, FILE_CHOOSER);
                } catch (ActivityNotFoundException e) {
                    fileCallback = null;
                    Toast.makeText(MainActivity.this,
                            "Не удалось открыть выбор файла", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception ignored) {}
        });

        webView.loadUrl(HOME_URL);

        // Splash is shown once per application process, not when the activity resumes.
        startSplashAnimation();

        // Check GitHub releases in the background. If no release exists, nothing happens.
        checkForUpdate();
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(Color.BLACK);
        window.setNavigationBarColor(Color.BLACK);

        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
        }

        if (Build.VERSION.SDK_INT >= 23) {
            window.getDecorView().setSystemUiVisibility(0);
        }
    }

    private View createSplashView() {
        FrameLayout splashRoot = new FrameLayout(this);
        splashRoot.setBackgroundColor(Color.BLACK);

        View glow = new View(this);
        android.graphics.drawable.GradientDrawable glowBg =
                new android.graphics.drawable.GradientDrawable();
        glowBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        glowBg.setColor(Color.rgb(150, 0, 0));
        glow.setBackground(glowBg);
        FrameLayout.LayoutParams glowLp = new FrameLayout.LayoutParams(260, 260);
        glowLp.gravity = Gravity.CENTER;
        splashRoot.addView(glow, glowLp);

        TextView bike = new TextView(this);
        bike.setText("🏍");
        bike.setTextSize(62);
        bike.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams bikeLp = new FrameLayout.LayoutParams(150, 100);
        bikeLp.gravity = Gravity.CENTER;
        bikeLp.leftMargin = -500;
        splashRoot.addView(bike, bikeLp);

        TextView logo = new TextView(this);
        logo.setText("MOTOHUB");
        logo.setTextColor(Color.WHITE);
        logo.setTextSize(30);
        logo.setGravity(Gravity.CENTER);
        logo.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        FrameLayout.LayoutParams logoLp = new FrameLayout.LayoutParams(-1, 70);
        logoLp.gravity = Gravity.CENTER;
        logoLp.topMargin = 115;
        splashRoot.addView(logo, logoLp);

        TextView region = new TextView(this);
        region.setText("РЕСПУБЛИКА САХА (ЯКУТИЯ)");
        region.setTextColor(Color.LTGRAY);
        region.setTextSize(11);
        region.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams regionLp = new FrameLayout.LayoutParams(-1, 40);
        regionLp.gravity = Gravity.CENTER;
        regionLp.topMargin = 170;
        splashRoot.addView(region, regionLp);

        splashRoot.setTag(bike);
        return splashRoot;
    }

    private void startSplashAnimation() {
        if (splash == null) return;

        View bike = (View) splash.getTag();
        bike.post(() -> {
            float start = -getResources().getDisplayMetrics().widthPixels - 150f;
            float end = getResources().getDisplayMetrics().widthPixels + 150f;
            bike.setTranslationX(start);
            bike.animate()
                    .translationX(end)
                    .setDuration(1450)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
        });

        splash.setAlpha(1f);
        handler.postDelayed(() -> {
            splash.animate()
                    .alpha(0f)
                    .setDuration(450)
                    .withEndAction(() -> splash.setVisibility(View.GONE))
                    .start();
        }, 1650);
    }

    private View createOfflineView() {
        FrameLayout box = new FrameLayout(this);
        box.setBackgroundColor(Color.BLACK);

        TextView title = new TextView(this);
        title.setText("НЕТ СОЕДИНЕНИЯ");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        FrameLayout.LayoutParams titleLp = new FrameLayout.LayoutParams(-1, 70);
        titleLp.gravity = Gravity.CENTER;
        titleLp.topMargin = -60;
        box.addView(title, titleLp);

        TextView retry = new TextView(this);
        retry.setText("↻  ПОВТОРИТЬ");
        retry.setTextColor(Color.WHITE);
        retry.setTextSize(16);
        retry.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.rgb(225, 6, 0));
        bg.setCornerRadius(18);
        retry.setBackground(bg);

        FrameLayout.LayoutParams retryLp = new FrameLayout.LayoutParams(260, 58);
        retryLp.gravity = Gravity.CENTER;
        retry.setOnClickListener(v -> {
            offlineView.setVisibility(View.GONE);
            progress.setVisibility(View.VISIBLE);
            webView.reload();
        });
        box.addView(retry, retryLp);

        return box;
    }

    private boolean handleUrl(Uri uri) {
        String scheme = uri.getScheme();
        String host = uri.getHost();

        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            if (host != null &&
                    (host.equals("motohub.relaxdev.ru") ||
                     host.endsWith(".motohub.relaxdev.ru"))) {
                return false;
            }

            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (Exception ignored) {}
            return true;
        }

        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception ignored) {}
        return true;
    }

    private boolean isOnline() {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkCapabilities nc =
                cm.getNetworkCapabilities(cm.getActiveNetwork());
        return nc != null &&
                (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                 nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                 nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "MotoHub",
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Уведомления MotoHub");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION);
        }
    }

    private void showNotification(String title, String body) {
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (manager == null) return;

        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT |
                        (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

        android.app.Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            builder = new android.app.Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new android.app.Notification.Builder(this);
        }

        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new android.app.Notification.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }

    private void installNativeMenuButton(WebView view) {
        String js = "(function(){"
                + "if(document.getElementById('motohub-native-menu')) return;"
                + "var b=document.createElement('button');"
                + "b.id='motohub-native-menu';"
                + "b.innerHTML='⚙';"
                + "b.title='Настройки MotoHub';"
                + "b.style='position:fixed;z-index:2147483647;right:12px;top:12px;width:44px;height:44px;border:1px solid rgba(255,255,255,.18);border-radius:50%;background:rgba(15,15,15,.88);color:#fff;font-size:22px;box-shadow:0 4px 18px rgba(0,0,0,.45);';"
                + "b.onclick=function(){MotoHub.openSettings();};"
                + "document.body.appendChild(b);"
                + "})();";
        view.evaluateJavascript(js, null);
    }

    private class MotoHubBridge {
        @JavascriptInterface
        public void openSettings() {
            runOnUiThread(() -> {
                try {
                    startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                } catch (Exception ignored) {}
            });
        }

        @JavascriptInterface
        public void notify(String title, String body) {
            runOnUiThread(() -> showNotification(
                    title == null || title.isEmpty() ? "MotoHub" : title,
                    body == null ? "" : body));
        }
    }

    private void checkForUpdate() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(
                        "https://api.github.com/repos/ajaankornilov4-glitch/APK/releases/latest");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setRequestProperty("Accept", "application/vnd.github+json");

                if (connection.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);
                    reader.close();

                    String json = response.toString();
                    int tagStart = json.indexOf("\"tag_name\"");
                    if (tagStart >= 0) {
                        int colon = json.indexOf(':', tagStart);
                        int q1 = json.indexOf('"', colon + 1);
                        int q2 = json.indexOf('"', q1 + 1);

                        if (q1 > 0 && q2 > q1) {
                            String latest = json.substring(q1 + 1, q2);
                            if (!BuildConfig.VERSION_NAME.equalsIgnoreCase(latest.replaceFirst("^v", "")) && !("v" + BuildConfig.VERSION_NAME).equalsIgnoreCase(latest)) {
                                runOnUiThread(() -> showNotification(
                                        "MotoHub",
                                        "Доступна новая версия приложения: " + latest));
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (connection != null) connection.disconnect();
                executor.shutdown();
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FILE_CHOOSER && fileCallback != null) {
            Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            fileCallback.onReceiveValue(result);
            fileCallback = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) webView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onDestroy() {
        if (fileCallback != null) {
            fileCallback.onReceiveValue(null);
            fileCallback = null;
        }

        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.stopLoading();
            webView.destroy();
        }

        super.onDestroy();
    }
}
