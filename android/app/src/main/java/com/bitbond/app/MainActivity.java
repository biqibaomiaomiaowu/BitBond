package com.bitbond.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private WebView webView;
    private volatile boolean paired = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        configureWebViewDebugging();

        webView = new WebView(this);
        webView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        configureWebView(webView);
        setContentView(webView);

        webView.loadUrl("file:///android_asset/index.html");
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView(WebView targetWebView) {
        WebSettings settings = targetWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccess(true);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);

        targetWebView.setWebViewClient(new WebViewClient());
        targetWebView.addJavascriptInterface(new BitBondBridge(), "BitBondBridge");
    }

    private void configureWebViewDebugging() {
        boolean debuggable = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        WebView.setWebContentsDebuggingEnabled(debuggable);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }

        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }

        super.onDestroy();
    }

    private String buildStateJson() throws JSONException {
        JSONObject root = new JSONObject();
        root.put("bridge", new JSONObject()
                .put("ready", true)
                .put("message", "Android WebView bridge connected"));
        root.put("self", new JSONObject()
                .put("sharing", true)
                .put("statusText", "你正在共享抽象状态"));
        root.put("pair", new JSONObject()
                .put("paired", paired)
                .put("nickname", paired ? "小禾" : ""));

        if (paired) {
            root.put("partner", new JSONObject()
                    .put("statusCode", "music")
                    .put("statusText", "正在听音乐")
                    .put("updatedAt", "刚刚")
                    .put("areaLabel", "音响旁"));
        } else {
            root.put("partner", new JSONObject()
                    .put("statusCode", "offline")
                    .put("statusText", "暂时离线")
                    .put("updatedAt", "已解除配对")
                    .put("areaLabel", "门口"));
        }

        return root.toString();
    }

    private String buildUnlinkResultJson() throws JSONException {
        return new JSONObject()
                .put("ok", true)
                .put("reason", "user_unlinked")
                .toString();
    }

    public final class BitBondBridge {
        @JavascriptInterface
        public String ping() {
            return "Android WebView bridge connected";
        }

        @JavascriptInterface
        public String getInitialState() {
            try {
                return buildStateJson();
            } catch (JSONException exception) {
                return "{}";
            }
        }

        @JavascriptInterface
        public String unlink(String payload) {
            paired = false;

            try {
                return buildUnlinkResultJson();
            } catch (JSONException exception) {
                return "{\"ok\":true,\"reason\":\"user_unlinked\"}";
            }
        }
    }
}
