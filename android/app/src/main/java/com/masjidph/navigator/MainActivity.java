package com.masjidph.navigator;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.GeolocationPermissions;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;

public class MainActivity extends Activity {
  private WebView webView;
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    webView = new WebView(this);
    setContentView(webView);
    WebSettings s = webView.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setGeolocationEnabled(true);
    webView.setWebChromeClient(new WebChromeClient() {
      @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
        boolean granted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        callback.invoke(origin, granted, false);
      }
    });
    webView.setWebViewClient(new WebViewClient() {
      @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        return openExternal(request.getUrl().toString());
      }
      @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
        return openExternal(url);
      }
    });
    if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1001);
    }
    webView.loadUrl("file:///android_asset/www/index.html");
  }
  private boolean openExternal(String url) {
    Uri uri = Uri.parse(url);
    String scheme = uri.getScheme();
    if ("https".equalsIgnoreCase(scheme) && uri.getHost() != null &&
        (uri.getHost().equalsIgnoreCase("www.google.com") || uri.getHost().equalsIgnoreCase("maps.google.com")) &&
        uri.getPath() != null && uri.getPath().startsWith("/maps")) {
      try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
      catch (Exception e) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps"))); }
        catch (Exception ignored) { }
      }
      return true;
    }
    if ("intent".equalsIgnoreCase(scheme) || "geo".equalsIgnoreCase(scheme)) {
      try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) { }
      return true;
    }
    return false;
  }
  @Override public void onBackPressed() { if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed(); }
}