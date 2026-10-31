package com.masjidph.navigator;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.*;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.Locale;

public class MainActivity extends Activity {
  private final int green=Color.rgb(6,78,59), teal=Color.rgb(15,118,110), pale=Color.rgb(226,247,235), ink=Color.rgb(28,48,39), muted=Color.rgb(103,122,112), bg=Color.rgb(245,249,246);
  private LinearLayout root, header, body, nav;
  private TextView headerSubtitle;
  private FirebaseAuth auth;
  private FirebaseFirestore db;
  private String tab="home";
  private boolean firebaseReady=false;
  private static final int GOOGLE_SIGN_IN_REQUEST = 9001;
  private static final String WEB_CLIENT_ID = BuildConfig.GOOGLE_WEB_CLIENT_ID;
  private EditText emailInput,passwordInput,nameInput,locationInput,bioInput;
  private TextView status;

  @Override public void onCreate(Bundle saved) {
    super.onCreate(saved);
    getWindow().setStatusBarColor(green); getWindow().setNavigationBarColor(Color.WHITE);
    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    initFirebase();
    showRoot();
    if(auth!=null) auth.addAuthStateListener(a->{ if(a.getCurrentUser()!=null) showApp(); else showLogin(); });
    else showLogin();
  }
  private void initFirebase(){
    try {
      if(FirebaseApp.getApps(this).isEmpty()){
        FirebaseOptions options=new FirebaseOptions.Builder()
          .setApiKey(BuildConfig.FIREBASE_API_KEY)
          .setApplicationId(BuildConfig.FIREBASE_APP_ID)
          .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
          .setGcmSenderId(BuildConfig.FIREBASE_MESSAGING_SENDER_ID)
          .setStorageBucket(BuildConfig.FIREBASE_STORAGE_BUCKET).build();
        FirebaseApp.initializeApp(this,options);
      }
      auth=FirebaseAuth.getInstance(); db=FirebaseFirestore.getInstance(); firebaseReady=true;
    } catch(Exception e){ firebaseReady=false; }
  }
  private void showRoot(){
    root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(bg);
    header=new LinearLayout(this);header.setOrientation(LinearLayout.VERTICAL);header.setPadding(dp(20),dp(16),dp(20),dp(16));header.setBackgroundColor(green);
    TextView brand=text("🕌  MasjidPH Navigator",22,Color.WHITE,true);header.addView(brand);
    headerSubtitle=text("Find your masjid. Connect with your community.",13,Color.rgb(218,241,231),false);
    LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.topMargin=dp(4);header.addView(headerSubtitle,hp);
    root.addView(header,new LinearLayout.LayoutParams(-1,-2));
    body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(16),dp(16),dp(16),dp(16));
    ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    setContentView(root);
  }
  private void showLogin(){
    if(root==null)showRoot(); body.removeAllViews(); if(nav!=null){root.removeView(nav);nav=null;}
    headerSubtitle.setText("Your Philippine Muslim community");
    LinearLayout hero=card();hero.setBackground(round(green,22));hero.addView(text("Assalamu Alaikum",25,Color.WHITE,true));hero.addView(gap(8));
    hero.addView(text("Discover masjids, follow local communities, and stay connected.",15,Color.rgb(226,247,235),false));body.addView(hero);
    body.addView(gap(12));LinearLayout form=card();form.addView(text("Welcome back",22,ink,true));form.addView(text("Sign in or create your community account.",14,muted,false));
    emailInput=input("Email address",false);form.addView(emailInput);passwordInput=input("Password",true);form.addView(passwordInput);
    Button sign=button("Sign in");form.addView(sign);sign.setOnClickListener(v->authenticate(false));
    Button create=button("Create account");create.setBackground(round(pale,14));create.setTextColor(green);form.addView(create);create.setOnClickListener(v->authenticate(true));
    Button google=button("Continue with Google");google.setBackground(round(Color.WHITE,14));google.setTextColor(green);form.addView(google);google.setOnClickListener(v->startGoogleSignIn());
    Button reset=button("Forgot password?");reset.setBackgroundColor(Color.TRANSPARENT);reset.setTextColor(teal);form.addView(reset);reset.setOnClickListener(v->resetPassword());
    status=text("",13,Color.rgb(170,45,45),false);form.addView(status);body.addView(form);
    body.addView(gap(12));body.addView(text("By continuing, you agree to follow each masjid community's rules. Be respectful and protect your personal information.",12,muted,false));
  }
  private void startGoogleSignIn(){
    if(!firebaseReady || auth==null){message("Firebase is unavailable. Check the app configuration and internet connection.");return;}
    GoogleSignInOptions options=new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
      .requestIdToken(WEB_CLIENT_ID)
      .requestEmail()
      .build();
    GoogleSignInClient client=GoogleSignIn.getClient(this,options);
    status.setText("Opening Google sign-in…");
    startActivityForResult(client.getSignInIntent(),GOOGLE_SIGN_IN_REQUEST);
  }

  @Override
  protected void onActivityResult(int requestCode,int resultCode,android.content.Intent data){
    super.onActivityResult(requestCode,resultCode,data);
    if(requestCode!=GOOGLE_SIGN_IN_REQUEST)return;
    if(resultCode!=RESULT_OK || data==null){message("Google sign-in was cancelled or could not be completed.");return;}
    try{
      GoogleSignInAccount account=GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException.class);
      if(account==null || account.getIdToken()==null){message("Google did not return an ID token. Verify the Web OAuth client ID and Android SHA fingerprints in Firebase.");return;}
      AuthCredential credential=GoogleAuthProvider.getCredential(account.getIdToken(),null);
      status.setText("Signing in with Google…");
      auth.signInWithCredential(credential).addOnCompleteListener(this,task->{
        if(!task.isSuccessful())message(authMessage(task.getException()));
      });
    }catch(ApiException e){
      message("Google sign-in failed (code "+e.getStatusCode()+"). Check the Android OAuth SHA-1/SHA-256 fingerprints and that Google is enabled in Firebase Authentication.");
    }
  }

  private void authenticate(boolean create){
    if(!firebaseReady){message("Firebase could not initialize. Check the Android Firebase configuration and internet connection.");return;}
    String email=emailInput.getText().toString().trim(), pass=passwordInput.getText().toString();
    if(email.isEmpty()||pass.length()<6){message("Enter a valid email and a password with at least 6 characters.");return;}
    status.setText(create?"Creating your account…":"Signing in…");
    if(create)auth.createUserWithEmailAndPassword(email,pass).addOnCompleteListener(this,t->{if(t.isSuccessful()){FirebaseUser u=auth.getCurrentUser();if(u!=null)u.sendEmailVerification();}else message(authMessage(t.getException()));});
    else auth.signInWithEmailAndPassword(email,pass).addOnCompleteListener(this,t->{if(!t.isSuccessful())message(authMessage(t.getException()));});
  }
  private void resetPassword(){String email=emailInput.getText().toString().trim();if(email.isEmpty()){message("Enter your email first.");return;}if(auth==null){message("Firebase is unavailable.");return;}auth.sendPasswordResetEmail(email).addOnCompleteListener(this,t->message(t.isSuccessful()?"Password reset email sent.":authMessage(t.getException())));}
  private void showApp(){
    if(root==null)showRoot(); if(nav!=null)root.removeView(nav);nav=null;
    headerSubtitle.setText("Your masjid and salah companion"); renderTab(tab); buildNav();
  }
  private void buildNav(){
    nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER);nav.setBackgroundColor(Color.WHITE);nav.setElevation(dp(12));
    String[] labels={"Home","Explore","Community","Qibla","Profile"};String[] ids={"home","explore","community","qibla","profile"};String[] icons={"⌂","⌕","◎","◉","♙"};
    for(int i=0;i<labels.length;i++){final String id=ids[i];LinearLayout item=new LinearLayout(this);item.setOrientation(LinearLayout.VERTICAL);item.setGravity(Gravity.CENTER);item.setPadding(dp(2),dp(4),dp(2),dp(4));item.setBackground(round(tab.equals(id)?pale:Color.WHITE,14));TextView ic=text(icons[i],20,green,true);ic.setGravity(Gravity.CENTER);TextView label=text(labels[i],10,tab.equals(id)?green:muted,true);label.setGravity(Gravity.CENTER);item.addView(ic);item.addView(label);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(58),1);p.setMargins(dp(2),dp(4),dp(2),dp(4));nav.addView(item,p);item.setOnClickListener(v->{tab=id;renderTab(id);buildNav();});}
    root.addView(nav,new LinearLayout.LayoutParams(-1,dp(66)));
  }
  private void renderTab(String which){
    if(body==null)return;body.removeAllViews();tab=which;
    switch(which){
      case "home": headerSubtitle.setText("Your masjid and salah companion");home();break;
      case "explore": headerSubtitle.setText("Discover masjids near you");explore();break;
      case "community": headerSubtitle.setText("Conversations rooted in your local masjid");community();break;
      case "qibla": headerSubtitle.setText("Find the direction to Makkah");qibla();break;
      case "profile": headerSubtitle.setText("Your community identity");profile();break;
    }
  }
  private void home(){
    body.addView(text("Assalamu Alaikum 👋",25,ink,true));body.addView(text("A calmer way to stay connected to your local Muslim community.",14,muted,false));body.addView(gap(14));
    LinearLayout c=card();c.setBackground(round(green,20));c.addView(text("TODAY'S PRAYER TIMES",12,Color.rgb(205,238,222),true));c.addView(gap(8));c.addView(text("Prayer times coming soon",20,Color.WHITE,true));c.addView(text("We’ll connect reliable location-based calculations and local masjid timetables in the next native feature pass.",13,Color.rgb(226,247,235),false));body.addView(c);
    body.addView(gap(12));LinearLayout shortcuts=card();shortcuts.addView(text("Your community",20,ink,true));shortcuts.addView(text("Find a masjid, then join the conversation for that location.",14,muted,false));Button b=button("Explore masjids");shortcuts.addView(b);b.setOnClickListener(v->{tab="explore";renderTab(tab);buildNav();});body.addView(shortcuts);
  }
  private void explore(){
    body.addView(text("Find a masjid",24,ink,true));body.addView(text("Search the directory. Listings shown here are starter data until verified local records are connected.",13,muted,false));EditText search=input("Search name, city, or province",false);body.addView(search);
    LinearLayout list=card();list.addView(text("Mosque directory",19,ink,true));TextView loading=text("Loading verified masjids…",14,muted,false);list.addView(loading);body.addView(list);
    if(db!=null)db.collection("mosques").limit(100).get().addOnSuccessListener(s->{list.removeAllViews();list.addView(text("Mosque directory · "+s.size()+" listings",19,ink,true));if(s.isEmpty())list.addView(text("No verified listings have been published yet. Search and map data will appear here once records are added.",14,muted,false));for(QueryDocumentSnapshot d:s){String name=d.getString("name");if(name==null)name=d.getString("mosqueName");if(name==null)name="Masjid";String city=d.getString("city");if(city==null)city=d.getString("addressCity");String province=d.getString("province");if(province==null)province=d.getString("region");String hay=(name+" "+(city==null?"":city)+" "+(province==null?"":province)).toLowerCase(Locale.ROOT);if(!search.getText().toString().trim().isEmpty()&&!hay.contains(search.getText().toString().trim().toLowerCase(Locale.ROOT)))continue;LinearLayout row=card();row.addView(text("🕌  "+name,17,ink,true));row.addView(text(((city==null)?"":city)+((province==null)?"":", "+province),13,muted,false));body.addView(row);}}).addOnFailureListener(e->loading.setText("Could not load mosque listings. Check your connection."));
    search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){if(db!=null)db.collection("mosques").limit(100).get().addOnSuccessListener(snap->{list.removeAllViews();list.addView(text("Mosque directory · "+snap.size()+" listings",19,ink,true));String q=s.toString().toLowerCase(Locale.ROOT);for(QueryDocumentSnapshot d:snap){String n=d.getString("name");if(n==null)n=d.getString("mosqueName");if(n==null)n="Masjid";String city=d.getString("city");String hay=(n+" "+(city==null?"":city)).toLowerCase(Locale.ROOT);if(!q.isEmpty()&&!hay.contains(q))continue;list.addView(text("🕌  "+n,16,ink,true));}});}public void afterTextChanged(android.text.Editable e){}});
  }
  private void community(){LinearLayout c=card();c.setBackground(round(green,20));c.addView(text("Every masjid can have its own community",22,Color.WHITE,true));c.addView(gap(8));c.addView(text("Local announcements, member conversations, and mosque-specific chat belong together—not in one giant group.",14,Color.rgb(226,247,235),false));body.addView(c);body.addView(gap(12));LinearLayout note=card();note.addView(text("Community chat is not enabled yet",19,ink,true));note.addView(text("We’re laying the native app foundation first. Next we’ll connect mosque membership, live messages, reporting, and secure Firestore rules.",14,muted,false));Button b=button("Find a masjid");note.addView(b);b.setOnClickListener(v->{tab="explore";renderTab(tab);buildNav();});body.addView(note);}
  private void qibla(){LinearLayout c=card();c.addView(text("Qibla direction",24,ink,true));c.addView(text("The Qibla compass is not yet connected in this native migration. It will use your device location and sensors with a clear permission prompt.",14,muted,false));Button b=button("Location permission");c.addView(b);b.setOnClickListener(v->{if(android.os.Build.VERSION.SDK_INT>=23)requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,android.Manifest.permission.ACCESS_COARSE_LOCATION},1001);});body.addView(c);}
  private void profile(){
    FirebaseUser u=auth==null?null:auth.getCurrentUser();LinearLayout c=card();c.addView(text("Your profile",23,ink,true));c.addView(text(u==null?"Signed-in member":(u.getEmail()==null?"Signed-in member":u.getEmail()),13,muted,false));
    nameInput=input("Display name",false);locationInput=input("City / province (optional)",false);bioInput=input("Short bio (optional)",false);c.addView(nameInput);c.addView(locationInput);c.addView(bioInput);
    if(u!=null&&db!=null)db.collection("users").document(u.getUid()).get().addOnSuccessListener(s->{if(s.exists()){nameInput.setText(s.getString("displayName")==null?"":s.getString("displayName"));locationInput.setText(s.getString("location")==null?"":s.getString("location"));bioInput.setText(s.getString("bio")==null?"":s.getString("bio"));}});
    Button save=button("Save profile");c.addView(save);TextView st=text("",13,muted,false);c.addView(st);save.setOnClickListener(v->{if(u==null||db==null){st.setText("Sign in to save your profile.");return;}String n=nameInput.getText().toString().trim();if(n.isEmpty()){st.setText("Enter a display name.");return;}java.util.Map<String,Object> p=new java.util.HashMap<>();p.put("displayName",n);p.put("location",locationInput.getText().toString().trim());p.put("bio",bioInput.getText().toString().trim());p.put("updatedAt",System.currentTimeMillis());db.collection("users").document(u.getUid()).set(p,com.google.firebase.firestore.SetOptions.merge()).addOnSuccessListener(x->st.setText("Profile saved.")).addOnFailureListener(e->st.setText("Couldn't save profile. Check Firestore rules."));});body.addView(c);
    body.addView(gap(12));Button out=button("Sign out");out.setBackground(round(Color.WHITE,14));out.setTextColor(green);body.addView(out);out.setOnClickListener(v->{if(auth!=null)auth.signOut();});
  }
  private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(16),dp(16),dp(16));c.setBackground(round(Color.WHITE,18));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(10);c.setLayoutParams(p);return c;}
  private EditText input(String hint,boolean password){EditText e=new EditText(this);e.setSingleLine(!hint.contains("bio"));e.setHint(hint);e.setTextSize(15);e.setPadding(dp(12),dp(10),dp(12),dp(10));e.setBackground(round(bg,12));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(10);e.setLayoutParams(p);if(password)e.setInputType(129);else if(hint.toLowerCase(Locale.ROOT).contains("email"))e.setInputType(33);return e;}
  private Button button(String label){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(15);b.setTextColor(Color.WHITE);b.setBackground(round(teal,14));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(8);b.setLayoutParams(p);return b;}
  private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setGravity(Gravity.START);return t;}
  private View gap(int h){View v=new View(this);v.setLayoutParams(new LinearLayout.LayoutParams(1,dp(h)));return v;}
  private GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
  private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
  private void message(String s){if(status!=null)status.setText(s);else new android.app.AlertDialog.Builder(this).setMessage(s).setPositiveButton("OK",null).show();}
  private String authMessage(Exception e){return e==null?"Something went wrong.":(e.getMessage()==null?"Authentication failed.":e.getMessage());}
}
