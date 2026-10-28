package com.masjidph.navigator;
import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.GeolocationPermissions;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Native Android app shell. Existing feature screens remain embedded while they are migrated to native SDKs. */
public class MainActivity extends Activity {
  private WebView webView;
  private TextView subtitle;
  private final int green=Color.rgb(6,78,59), mint=Color.rgb(226,247,235);
  private final String[] tabs={"Home","Explore","Community","Qibla","Profile"};
  private final String[] symbols={"⌂","⌕","◎","◉","♙"};
  private final String[] ids={"home","explore","community","qibla","profile"};
  private final String[] hints={"Your masjid and salah companion","Discover nearby masjids","Connect around your local masjid","Find the direction to Makkah","Your community identity"};
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    getWindow().setStatusBarColor(green); getWindow().setNavigationBarColor(green);
    LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(244,249,246));
    LinearLayout header=new LinearLayout(this); header.setOrientation(LinearLayout.VERTICAL); header.setPadding(dp(18),dp(12),dp(18),dp(12)); header.setBackgroundColor(green);
    TextView brand=new TextView(this); brand.setText("🕌  MasjidPH Navigator"); brand.setTextColor(Color.WHITE); brand.setTextSize(21); brand.setTypeface(Typeface.DEFAULT,Typeface.BOLD); header.addView(brand);
    subtitle=new TextView(this); subtitle.setText(hints[0]); subtitle.setTextColor(Color.rgb(218,241,231)); subtitle.setTextSize(13); LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2); sp.topMargin=dp(4); header.addView(subtitle,sp);
    root.addView(header,new LinearLayout.LayoutParams(-1,-2));
    webView=new WebView(this); root.addView(webView,new LinearLayout.LayoutParams(-1,0,1f));
    LinearLayout nav=new LinearLayout(this); nav.setGravity(Gravity.CENTER); nav.setBackgroundColor(Color.WHITE); nav.setElevation(dp(10)); root.addView(nav,new LinearLayout.LayoutParams(-1,dp(64)));
    for(int i=0;i<tabs.length;i++){ final int index=i; LinearLayout item=new LinearLayout(this); item.setOrientation(LinearLayout.VERTICAL); item.setGravity(Gravity.CENTER); item.setBackground(round(mint,13)); TextView icon=new TextView(this); icon.setText(symbols[i]); icon.setTextColor(green); icon.setTextSize(20); icon.setGravity(Gravity.CENTER); TextView label=new TextView(this); label.setText(tabs[i]); label.setTextColor(green); label.setTextSize(10); label.setTypeface(Typeface.DEFAULT,Typeface.BOLD); item.addView(icon); item.addView(label); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1f); p.setMargins(dp(3),dp(5),dp(3),dp(5)); nav.addView(item,p); item.setOnClickListener(v->navigate(index)); }
    setContentView(root);
    WebSettings settings=webView.getSettings(); settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true); settings.setGeolocationEnabled(true); webView.setOverScrollMode(View.OVER_SCROLL_NEVER); webView.setBackgroundColor(Color.rgb(244,249,246));
    webView.setWebChromeClient(new WebChromeClient(){ @Override public void onGeolocationPermissionsShowPrompt(String origin,GeolocationPermissions.Callback callback){ boolean ok=checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED||checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED; callback.invoke(origin,ok,false); }});
    webView.setWebViewClient(new WebViewClient(){ @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return openExternal(request.getUrl().toString());} @Override public boolean shouldOverrideUrlLoading(WebView view,String url){return openExternal(url);} });
    if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},1001);
    webView.loadUrl("file:///android_asset/www/index.html");
  }
  private void navigate(int i){ subtitle.setText(hints[i]); String js="if(typeof showTab==='function'){showTab('"+ids[i]+"');}"; webView.evaluateJavascript(js,null); }
  private GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
  private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
  private boolean openExternal(String url){Uri uri=Uri.parse(url);String scheme=uri.getScheme();if("https".equalsIgnoreCase(scheme)&&uri.getHost()!=null&&(uri.getHost().equalsIgnoreCase("www.google.com")||uri.getHost().equalsIgnoreCase("maps.google.com"))&&uri.getPath()!=null&&uri.getPath().startsWith("/maps")){try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(Exception ignored){}return true;}if("intent".equalsIgnoreCase(scheme)||"geo".equalsIgnoreCase(scheme)){try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(Exception ignored){}return true;}return false;}
  @Override public void onBackPressed(){if(webView!=null&&webView.canGoBack())webView.goBack();else super.onBackPressed();}
}
