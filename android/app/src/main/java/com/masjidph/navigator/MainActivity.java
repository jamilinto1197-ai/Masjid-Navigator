package com.masjidph.navigator;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.text.InputType;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.text.InputFilter;

import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Query;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final int green = Color.rgb(6, 78, 59);
    private final int teal = Color.rgb(15, 118, 110);
    private final int pale = Color.rgb(226, 247, 235);
    private final int ink = Color.rgb(28, 48, 39);
    private final int muted = Color.rgb(103, 122, 112);
    private final int bg = Color.rgb(245, 249, 246);

    private LinearLayout root, header, body, nav, mosqueResults;
    private TextView headerSubtitle, status, mosqueStatus, prayerStatus, prayerTimes, qiblaStatus, qiblaHeading;
    private EditText emailInput, passwordInput, nameInput, locationInput, bioInput, mosqueSearch;
    private Button mosqueLocateButton;
    private Spinner communitySpinner;
    private TextView communityStatus;
    private LinearLayout communityMessageList;
    private EditText communityMessageInput;
    private ListenerRegistration communityListener;
    private String activeCommunityId = "";
    private final List<MosqueItem> communityMosques = new ArrayList<>();
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private String tab = "home";
    private boolean firebaseReady = false;
    private String firebaseConfigError = "Firebase Android setup is incomplete. Configure the real Android app ID before signing in.";
    private boolean mosqueLoadRequested = false;

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private LocationManager locationManager;
    private Location activeLocation;
    private LocationListener activeLocationListener;
    private LocationResult activeLocationCallback;
    private LocationResult pendingLocationCallback;
    private Runnable locationTimeout;
    private boolean locationRequestCompleted = true;

    private SensorManager sensorManager;
    private Sensor compassSensor;
    private CompassDial compassDial;
    private boolean qiblaBearingReady = false;
    private double qiblaBearing = 0.0;
    private double trueHeading = 0.0;

    private interface LocationResult {
        void onSuccess(Location location);
        void onError(String message);
    }

    private static final class MosqueItem {
        final String documentId;
        final String name;
        final String city;
        final String province;
        final double latitude;
        final double longitude;
        final boolean starter;

        MosqueItem(String name, String city, String province, double latitude, double longitude, boolean starter) {
            this("", name, city, province, latitude, longitude, starter);
        }

        MosqueItem(String documentId, String name, String city, String province, double latitude, double longitude, boolean starter) {
            this.documentId = documentId == null ? "" : documentId;
            this.name = name == null || name.trim().isEmpty() ? "Masjid" : name.trim();
            this.city = city == null ? "" : city.trim();
            this.province = province == null ? "" : province.trim();
            this.latitude = latitude;
            this.longitude = longitude;
            this.starter = starter;
        }

        boolean hasCoordinates() {
            return Double.isFinite(latitude) && Double.isFinite(longitude)
                    && latitude >= -90 && latitude <= 90
                    && longitude >= -180 && longitude <= 180;
        }
    }

    private final List<MosqueItem> mosqueItems = new ArrayList<>();

    private final SensorEventListener compassListener = new SensorEventListener() {
        @Override public void onSensorChanged(SensorEvent event) {
            if (!qiblaBearingReady || compassDial == null) return;
            double magneticHeading;
            if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                float[] rotation = new float[9];
                float[] orientation = new float[3];
                SensorManager.getRotationMatrixFromVector(rotation, event.values);
                SensorManager.getOrientation(rotation, orientation);
                magneticHeading = normalizeDegrees(Math.toDegrees(orientation[0]));
            } else if (event.sensor.getType() == Sensor.TYPE_ORIENTATION) {
                magneticHeading = normalizeDegrees(event.values[0]);
            } else {
                return;
            }

            trueHeading = magneticHeading;
            if (activeLocation != null) {
                GeomagneticField field = new GeomagneticField(
                        (float) activeLocation.getLatitude(),
                        (float) activeLocation.getLongitude(),
                        (float) activeLocation.getAltitude(),
                        System.currentTimeMillis());
                trueHeading = normalizeDegrees(magneticHeading + field.getDeclination());
            }
            double relative = normalizeDegrees(qiblaBearing - trueHeading);
            compassDial.setRelativeBearing((float) relative);
            if (qiblaHeading != null) {
                qiblaHeading.setText(String.format(Locale.getDefault(),
                        "Compass heading: %.0f° true north\nQibla bearing: %.0f° true north",
                        trueHeading, qiblaBearing));
            }
        }
        @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(green);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        mosqueItems.addAll(starterMosques());
        initFirebase();
        showRoot();
        if (auth != null) {
            auth.addAuthStateListener(a -> {
                if (a.getCurrentUser() != null) showApp();
                else showLogin();
            });
        } else {
            showLogin();
        }
    }

    private void initFirebase() {
        try {
            String androidAppId = getString(R.string.firebase_android_app_id).trim();
            if (!androidAppId.matches("^1:885467340998:android:[0-9a-fA-F]+$")) {
                firebaseReady = false;
                firebaseConfigError = "Firebase Android setup is incomplete. Register package com.masjidph.navigator in Firebase and configure its actual mobilesdk_app_id before signing in.";
                return;
            }
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseOptions options = new FirebaseOptions.Builder()
                        .setApiKey("AIzaSyBuNwIsbjxmyBM6pdzR4HVmFjAhViGIsgk")
                        .setApplicationId(androidAppId)
                        .setProjectId("masjid-109f3")
                        .setGcmSenderId("885467340998")
                        .setStorageBucket("masjid-109f3.firebasestorage.app")
                        .build();
                FirebaseApp.initializeApp(this, options);
            }
            auth = FirebaseAuth.getInstance();
            db = FirebaseFirestore.getInstance();
            firebaseReady = true;
            firebaseConfigError = "";
        } catch (Exception e) {
            firebaseReady = false;
            firebaseConfigError = "Firebase could not initialize. Verify the Android app ID and Firebase project configuration.";
        }
    }

    private void showRoot() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);

        header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(16), dp(20), dp(16));
        header.setBackgroundColor(green);
        header.addView(text("🕌  MasjidPH Navigator", 22, Color.WHITE, true));
        headerSubtitle = text("Find your masjid. Connect with your community.", 13, Color.rgb(218, 241, 231), false);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.topMargin = dp(4);
        header.addView(headerSubtitle, hp);
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(16), dp(16), dp(16));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void showLogin() {
        if (root == null) showRoot();
        body.removeAllViews();
        if (nav != null) { root.removeView(nav); nav = null; }
        headerSubtitle.setText("Your Philippine Muslim community");

        LinearLayout hero = card();
        hero.setBackground(round(green, 22));
        hero.addView(text("Assalamu Alaikum", 25, Color.WHITE, true));
        hero.addView(gap(8));
        hero.addView(text("Discover masjids, follow local communities, and stay connected.",
                15, Color.rgb(226, 247, 235), false));
        body.addView(hero);
        body.addView(gap(12));

        LinearLayout form = card();
        form.addView(text("Welcome back", 22, ink, true));
        form.addView(text("Sign in or create your community account.", 14, muted, false));
        emailInput = input("Email address", false);
        form.addView(emailInput);
        passwordInput = input("Password", true);
        form.addView(passwordInput);
        Button sign = button("Sign in");
        form.addView(sign);
        sign.setOnClickListener(v -> authenticate(false));
        Button create = button("Create account");
        create.setBackground(round(pale, 14));
        create.setTextColor(green);
        form.addView(create);
        create.setOnClickListener(v -> authenticate(true));
        Button google = button("Continue with Google");
        google.setBackground(round(Color.WHITE, 14));
        google.setTextColor(green);
        form.addView(google);
        google.setOnClickListener(v -> signInWithGoogle(google));
        Button reset = button("Forgot password?");
        reset.setBackgroundColor(Color.TRANSPARENT);
        reset.setTextColor(teal);
        form.addView(reset);
        reset.setOnClickListener(v -> resetPassword());
        status = text(firebaseReady ? "" : firebaseConfigError, 13, Color.rgb(170, 45, 45), false);
        form.addView(status);
        body.addView(form);
        body.addView(gap(12));
        body.addView(text("By continuing, you agree to follow each masjid community's rules. Be respectful and protect your personal information.",
                12, muted, false));
    }

    private void authenticate(boolean create) {
        if (!firebaseReady || auth == null) { message(firebaseConfigError); return; }
        String email = emailInput.getText().toString().trim();
        String pass = passwordInput.getText().toString();
        if (email.isEmpty() || pass.length() < 6) {
            message("Enter a valid email and a password with at least 6 characters.");
            return;
        }
        status.setText(create ? "Creating your account…" : "Signing in…");
        if (create) {
            auth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener(this, task -> {
                if (task.isSuccessful()) {
                    FirebaseUser user = auth.getCurrentUser();
                    if (user != null) {
                        user.sendEmailVerification();
                        message("Account created. Check your email for the verification link.");
                    }
                } else message(authMessage(task.getException()));
            });
        } else {
            auth.signInWithEmailAndPassword(email, pass).addOnCompleteListener(this,
                    task -> { if (!task.isSuccessful()) message(authMessage(task.getException())); });
        }
    }

    private void resetPassword() {
        String email = emailInput.getText().toString().trim();
        if (email.isEmpty()) { message("Enter your email first."); return; }
        if (auth == null || !firebaseReady) { message(firebaseConfigError); return; }
        auth.sendPasswordResetEmail(email).addOnCompleteListener(this,
                task -> message(task.isSuccessful() ? "Password reset email sent." : authMessage(task.getException())));
    }
    private void signInWithGoogle(Button sourceButton) {
        if (!firebaseReady || auth == null) {
            message(firebaseConfigError);
            return;
        }
        String webClientId = getString(R.string.default_web_client_id).trim();
        if (!webClientId.matches("^[0-9]+-[A-Za-z0-9-]+\\.apps\\.googleusercontent\\.com$")) {
            message("Google sign-in is not configured. Register the Firebase Android app, configure its OAuth web client, and set FIREBASE_WEB_CLIENT_ID in the repository Actions variables.");
            return;
        }
        sourceButton.setEnabled(false);
        if (status != null) status.setText("Opening Google sign-in…");
        try {
            CredentialManager manager = CredentialManager.create(this);
            GetGoogleIdOption googleOption = new GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(webClientId)
                    .setAutoSelectEnabled(false)
                    .build();
            GetCredentialRequest request = new GetCredentialRequest.Builder()
                    .addCredentialOption(googleOption)
                    .build();
            manager.getCredentialAsync(this, request, new CancellationSignal(),
                    command -> mainHandler.post(command),
                    new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                        @Override public void onResult(GetCredentialResponse response) {
                            Credential credential = response.getCredential();
                            if (!(credential instanceof CustomCredential)
                                    || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
                                sourceButton.setEnabled(true);
                                if (status != null) status.setText("Google returned an unsupported credential. Try again or use email sign-in.");
                                return;
                            }
                            try {
                                GoogleIdTokenCredential googleCredential =
                                        GoogleIdTokenCredential.createFrom(((CustomCredential) credential).getData());
                                AuthCredential firebaseCredential =
                                        GoogleAuthProvider.getCredential(googleCredential.getIdToken(), null);
                                auth.signInWithCredential(firebaseCredential).addOnCompleteListener(MainActivity.this, task -> {
                                    sourceButton.setEnabled(true);
                                    if (task.isSuccessful()) {
                                        if (status != null) status.setText("");
                                    } else {
                                        if (status != null) status.setText("Google sign-in failed. Verify the Firebase OAuth client and signing fingerprints.");
                                    }
                                });
                            } catch (Exception e) {
                                sourceButton.setEnabled(true);
                                if (status != null) status.setText("Could not read the Google credential. Update the Google Identity configuration and try again.");
                            }
                        }

                        @Override public void onError(GetCredentialException error) {
                            sourceButton.setEnabled(true);
                            if (status != null) status.setText("Google sign-in was cancelled or unavailable. Check OAuth configuration or use email sign-in.");
                        }
                    });
        } catch (Exception e) {
            sourceButton.setEnabled(true);
            if (status != null) status.setText("Could not start Google sign-in. Verify the app's OAuth configuration.");
        }
    }


    private void showApp() {
        if (root == null) showRoot();
        if (nav != null) root.removeView(nav);
        nav = null;
        headerSubtitle.setText("Your masjid and salah companion");
        renderTab(tab);
        buildNav();
    }

    private void buildNav() {
        nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setBackgroundColor(Color.WHITE);
        nav.setElevation(dp(12));
        String[] labels = {"Home", "Explore", "Community", "Qibla", "Profile"};
        String[] ids = {"home", "explore", "community", "qibla", "profile"};
        String[] icons = {"⌂", "⌕", "◎", "◉", "♙"};
        for (int i = 0; i < labels.length; i++) {
            final String id = ids[i];
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(2), dp(4), dp(2), dp(4));
            item.setBackground(round(tab.equals(id) ? pale : Color.WHITE, 14));
            TextView ic = text(icons[i], 20, green, true);
            ic.setGravity(Gravity.CENTER);
            TextView label = text(labels[i], 10, tab.equals(id) ? green : muted, true);
            label.setGravity(Gravity.CENTER);
            item.addView(ic);
            item.addView(label);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(58), 1);
            p.setMargins(dp(2), dp(4), dp(2), dp(4));
            nav.addView(item, p);
            item.setOnClickListener(v -> { tab = id; renderTab(id); buildNav(); });
        }
        root.addView(nav, new LinearLayout.LayoutParams(-1, dp(66)));
    }

    private void renderTab(String which) {
        if (body == null) return;
        if (!"qibla".equals(which)) stopCompass();
        if (!"community".equals(which)) stopCommunityListener();
        body.removeAllViews();
        tab = which;
        switch (which) {
            case "home": headerSubtitle.setText("Your masjid and salah companion"); home(); break;
            case "explore": headerSubtitle.setText("Discover masjids near you"); explore(); break;
            case "community": headerSubtitle.setText("Conversations rooted in your local masjid"); community(); break;
            case "qibla": headerSubtitle.setText("Find the direction to Makkah"); qibla(); break;
            case "profile": headerSubtitle.setText("Your community identity"); profile(); break;
            default: tab = "home"; home();
        }
    }

    private void home() {
        body.addView(text("Assalamu Alaikum", 25, ink, true));
        body.addView(text("A calmer way to stay connected to your local Muslim community.", 14, muted, false));
        body.addView(gap(14));

        LinearLayout prayerCard = card();
        prayerCard.setBackground(round(green, 20));
        prayerCard.addView(text("TODAY'S PRAYER TIMES", 12, Color.rgb(205, 238, 222), true));
        prayerCard.addView(gap(8));
        prayerStatus = text("Tap below to estimate times for your location.", 13, Color.rgb(226, 247, 235), false);
        prayerCard.addView(prayerStatus);
        prayerTimes = text("Fajr  —     Sunrise  —\nDhuhr —     Asr      —\nMaghrib —     Isha     —",
                16, Color.WHITE, true);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = dp(10);
        prayerCard.addView(prayerTimes, tp);
        Button prayerButton = button("Get prayer times for my location");
        prayerButton.setOnClickListener(v -> loadPrayerTimes());
        prayerCard.addView(prayerButton);
        prayerCard.addView(text("Estimated times use the AlAdhan service. Confirm the timetable with your local masjid.",
                12, Color.rgb(226, 247, 235), false));
        body.addView(prayerCard);

        body.addView(gap(12));
        LinearLayout shortcuts = card();
        shortcuts.addView(text("Your community", 20, ink, true));
        shortcuts.addView(text("Find a masjid, review its listing, and open directions where coordinates are available.", 14, muted, false));
        Button b = button("Explore masjids");
        shortcuts.addView(b);
        b.setOnClickListener(v -> { tab = "explore"; renderTab(tab); buildNav(); });
        body.addView(shortcuts);
    }

    private void loadPrayerTimes() {
        if (prayerStatus != null) prayerStatus.setText("Requesting your location…");
        if (prayerTimes != null) prayerTimes.setText("Fajr —   Sunrise —   Dhuhr —   Asr —   Maghrib —   Isha —");
        findCurrentLocation(new LocationResult() {
            @Override public void onSuccess(Location location) {
                if (prayerStatus != null) prayerStatus.setText("Fetching estimated times for your coordinates…");
                fetchPrayerTimes(location);
            }
            @Override public void onError(String message) {
                if (prayerStatus != null) prayerStatus.setText("Location unavailable: " + message);
            }
        });
    }

    private void fetchPrayerTimes(Location location) {
        ioExecutor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.US);
                dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Manila"));
                String date = dateFormat.format(new Date());
                String lat = String.format(Locale.US, "%.6f", location.getLatitude());
                String lon = String.format(Locale.US, "%.6f", location.getLongitude());
                URL url = new URL("https://api.aladhan.com/v1/timings/" + date
                        + "?latitude=" + lat + "&longitude=" + lon
                        + "&method=3&school=0&timezonestring=Asia%2FManila");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(12000);
                connection.setRequestProperty("Accept", "application/json");
                int responseCode = connection.getResponseCode();
                if (responseCode < 200 || responseCode >= 300) {
                    throw new IllegalStateException("Prayer service returned HTTP " + responseCode);
                }
                StringBuilder response = new StringBuilder();
                try (InputStream input = connection.getInputStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"))) {
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);
                }
                JSONObject json = new JSONObject(response.toString());
                if (json.optInt("code") != 200 || !json.has("data")) {
                    throw new IllegalStateException("Prayer service returned an invalid response");
                }
                JSONObject timings = json.getJSONObject("data").getJSONObject("timings");
                String result = "Fajr      " + cleanTime(timings.optString("Fajr")) + "\n"
                        + "Sunrise   " + cleanTime(timings.optString("Sunrise")) + "\n"
                        + "Dhuhr     " + cleanTime(timings.optString("Dhuhr")) + "\n"
                        + "Asr       " + cleanTime(timings.optString("Asr")) + "\n"
                        + "Maghrib   " + cleanTime(timings.optString("Maghrib")) + "\n"
                        + "Isha      " + cleanTime(timings.optString("Isha"));
                runOnUiThread(() -> {
                    if (prayerTimes != null) prayerTimes.setText(result);
                    if (prayerStatus != null) prayerStatus.setText("Estimated for your current location · Asia/Manila.");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (prayerStatus != null) prayerStatus.setText("Could not fetch prayer times. Check your connection and try again.");
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private String cleanTime(String time) {
        if (time == null || time.isEmpty()) return "—";
        int space = time.indexOf(' ');
        return space > 0 ? time.substring(0, space) : time;
    }

    private void explore() {
        body.addView(text("Find a masjid", 24, ink, true));
        body.addView(text("Starter coordinates are approximate and listings need local verification. Search works on the loaded list without repeatedly querying Firestore.",
                13, muted, false));
        mosqueSearch = input("Search name, city, or province", false);
        body.addView(mosqueSearch);
        mosqueLocateButton = button("Use my location to sort by distance");
        body.addView(mosqueLocateButton);
        mosqueLocateButton.setOnClickListener(v -> {
            mosqueLocateButton.setEnabled(false);
            findCurrentLocation(new LocationResult() {
                @Override public void onSuccess(Location location) {
                    if (mosqueLocateButton != null) mosqueLocateButton.setEnabled(true);
                    renderMosqueResults();
                }
                @Override public void onError(String message) {
                    if (mosqueLocateButton != null) mosqueLocateButton.setEnabled(true);
                    if (mosqueStatus != null) mosqueStatus.setText("Could not get location: " + message);
                }
            });
        });
        mosqueResults = new LinearLayout(this);
        mosqueResults.setOrientation(LinearLayout.VERTICAL);
        body.addView(mosqueResults);
        mosqueStatus = text("Showing starter listings. Verify details locally before travelling.", 12, muted, false);
        renderMosqueResults();
        mosqueSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { renderMosqueResults(); }
            @Override public void afterTextChanged(android.text.Editable e) { }
        });

        if (db != null && !mosqueLoadRequested) {
            mosqueLoadRequested = true;
            mosqueStatus.setText("Loading mosque directory…");
            db.collection("mosques").limit(250).get().addOnSuccessListener(snapshot -> {
                if (!snapshot.isEmpty()) {
                    List<MosqueItem> loaded = new ArrayList<>();
                    for (QueryDocumentSnapshot document : snapshot) {
                        String name = firstNonEmpty(document.getString("name"), document.getString("mosqueName"), "Masjid");
                        String city = firstNonEmpty(document.getString("city"), document.getString("addressCity"), "");
                        String province = firstNonEmpty(document.getString("province"), document.getString("region"), "");
                        Object latRaw = firstPresent(document.get("lat"), document.get("latitude"));
                        Object lonRaw = firstPresent(document.get("lon"), document.get("lng"), document.get("longitude"));
                        double lat = toCoordinate(latRaw);
                        double lon = toCoordinate(lonRaw);
                        loaded.add(new MosqueItem(document.getId(), name, city, province, lat, lon, false));
                    }
                    mosqueItems.clear();
                    mosqueItems.addAll(loaded);
                    if (mosqueStatus != null) mosqueStatus.setText("Loaded " + loaded.size() + " mosque listings from Firestore. Verify details before travel.");
                } else if (mosqueStatus != null) {
                    mosqueStatus.setText("Firestore has no mosque records yet. Showing starter listings that need local verification.");
                }
                renderMosqueResults();
            }).addOnFailureListener(error -> {
                if (mosqueStatus != null) mosqueStatus.setText("Firestore is unavailable. Showing starter listings that need local verification.");
                renderMosqueResults();
            });
        } else if (db == null && mosqueStatus != null) {
            mosqueStatus.setText("Firebase is not configured. Showing local starter listings; cloud listings are unavailable.");
        }
    }

    private void renderMosqueResults() {
        if (mosqueResults == null) return;
        mosqueResults.removeAllViews();
        if (mosqueStatus != null && mosqueStatus.getParent() != null) {
            ((android.view.ViewGroup) mosqueStatus.getParent()).removeView(mosqueStatus);
        }
        mosqueResults.addView(cardWithChild(mosqueStatus == null
                ? text("Mosque directory", 18, ink, true) : mosqueStatus));
        String query = mosqueSearch == null ? "" : mosqueSearch.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<MosqueItem> visible = new ArrayList<>();
        for (MosqueItem item : mosqueItems) {
            String haystack = (item.name + " " + item.city + " " + item.province).toLowerCase(Locale.ROOT);
            if (query.isEmpty() || haystack.contains(query)) visible.add(item);
        }
        if (activeLocation != null) {
            Collections.sort(visible, (a, b) -> Double.compare(distanceKm(activeLocation, a), distanceKm(activeLocation, b)));
        }
        if (visible.isEmpty()) {
            mosqueResults.addView(text("No matching masjids. Try another city or province.", 14, muted, false));
            return;
        }
        for (MosqueItem item : visible) {
            LinearLayout row = card();
            row.addView(text("🕌  " + item.name, 17, ink, true));
            String place = item.city + (item.city.isEmpty() || item.province.isEmpty() ? "" : ", ") + item.province;
            row.addView(text(place.isEmpty() ? "Location details unavailable" : place, 13, muted, false));
            String sourceLabel = item.starter ? "Starter listing · verify coordinates locally" : "Community directory listing · verify before travel";
            if (activeLocation != null && item.hasCoordinates()) {
                sourceLabel += String.format(Locale.getDefault(), " · %.1f km away", distanceKm(activeLocation, item));
            }
            row.addView(text(sourceLabel, 12, muted, false));
            if (item.hasCoordinates()) {
                Button directions = button("Check directions in Maps");
                directions.setOnClickListener(v -> new android.app.AlertDialog.Builder(MainActivity.this)
                        .setTitle("Verify this location")
                        .setMessage("Directory coordinates may be approximate or out of date. Confirm the masjid and destination before starting navigation.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Open Maps", (dialog, which) -> openDirections(item))
                        .show());
                row.addView(directions);
            }
            mosqueResults.addView(row);
        }
    }

    private LinearLayout cardWithChild(View child) {
        LinearLayout c = card();
        if (child.getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) child.getParent()).removeView(child);
        }
        c.addView(child);
        return c;
    }

    private void openDirections(MosqueItem item) {
        try {
            Uri uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination="
                    + item.latitude + "," + item.longitude + "&travelmode=driving");
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception e) {
            message("No app is available to open directions.");
        }
    }

    private List<MosqueItem> starterMosques() {
        List<MosqueItem> list = new ArrayList<>();
        list.add(new MosqueItem("Masjid Dimakuta", "Marawi City", "Lanao del Sur", 8.0005, 124.2928, true));
        list.add(new MosqueItem("Islamic Center / King Faisal Mosque", "Marawi City", "Lanao del Sur", 8.0027, 124.2832, true));
        list.add(new MosqueItem("Masjid in Poblacion", "Tubod", "Lanao del Norte", 8.0555, 123.7907, true));
        list.add(new MosqueItem("Masjid Muslimen", "Poblacion, Magsaysay", "Lanao del Norte", 8.0341, 123.9169, true));
        list.add(new MosqueItem("Grand Mosque of Cotabato", "Cotabato City", "Maguindanao del Norte", 7.2236, 124.2452, true));
        list.add(new MosqueItem("Blue Mosque (Sultan Haji Hassanal Bolkiah Masjid)", "Taguig", "Metro Manila", 14.5176, 121.0493, true));
        list.add(new MosqueItem("Quiapo Mosque (Golden Mosque)", "Manila", "Metro Manila", 14.5995, 120.9850, true));
        list.add(new MosqueItem("Masjid Al-Dahab", "Manila", "Metro Manila", 14.5991, 120.9855, true));
        list.add(new MosqueItem("Masjid in Zamboanga City", "Zamboanga City", "Zamboanga del Sur", 6.9214, 122.0790, true));
        list.add(new MosqueItem("Masjid in Jolo", "Jolo", "Sulu", 6.0522, 121.0022, true));
        list.add(new MosqueItem("Masjid in Isabela City", "Isabela City", "Basilan", 6.7041, 121.9712, true));
        list.add(new MosqueItem("Masjid Sultan Omar Dianalan", "Camague, Iligan City", "Lanao del Norte", 8.21259, 124.23015, true));
        list.add(new MosqueItem("Masjid Musa", "Santiago, Iligan City", "Lanao del Norte", 8.24816, 124.24375, true));
        list.add(new MosqueItem("Halal Masjid", "Bonbonon area, Lanao del Norte", "Lanao del Norte", 8.26655, 124.29043, true));
        list.add(new MosqueItem("Pugaan Masjid", "Pugaan, Iligan City", "Lanao del Norte", 8.24529, 124.31003, true));
        list.add(new MosqueItem("Masjid in Davao City", "Davao City", "Davao del Sur", 7.0731, 125.6128, true));
        return list;
    }

    private String firstNonEmpty(String a, String b, String fallback) {
        if (a != null && !a.trim().isEmpty()) return a;
        if (b != null && !b.trim().isEmpty()) return b;
        return fallback;
    }

    private Object firstPresent(Object... values) {
        for (Object value : values) if (value != null) return value;
        return null;
    }

    private double toCoordinate(Object value) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            try { return Double.parseDouble((String) value); }
            catch (NumberFormatException ignored) { return Double.NaN; }
        }
        return Double.NaN;
    }

    private double distanceKm(Location from, MosqueItem to) {
        if (!to.hasCoordinates()) return Double.POSITIVE_INFINITY;
        float[] results = new float[1];
        Location.distanceBetween(from.getLatitude(), from.getLongitude(), to.latitude, to.longitude, results);
        return results[0] / 1000.0;
    }

    private void community() {
        LinearLayout intro = card();
        intro.setBackground(round(green, 20));
        intro.addView(text("Your masjid, your community", 22, Color.WHITE, true));
        intro.addView(gap(8));
        intro.addView(text("Choose a published masjid to open its live discussion room. Rooms are currently open to signed-in members; keep messages respectful and never share private information.",
                13, Color.rgb(226, 247, 235), false));
        body.addView(intro);

        LinearLayout chatCard = card();
        chatCard.addView(text("Masjid discussion", 19, ink, true));
        communityStatus = text("Loading published masjid rooms…", 13, muted, false);
        chatCard.addView(communityStatus);
        communitySpinner = new Spinner(this);
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(-1, -2);
        spinnerParams.topMargin = dp(8);
        chatCard.addView(communitySpinner, spinnerParams);

        ScrollView messageScroll = new ScrollView(this);
        communityMessageList = new LinearLayout(this);
        communityMessageList.setOrientation(LinearLayout.VERTICAL);
        communityMessageList.addView(text("Choose a masjid to load its messages.", 14, muted, false));
        messageScroll.addView(communityMessageList);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, dp(300));
        scrollParams.topMargin = dp(10);
        chatCard.addView(messageScroll, scrollParams);

        communityMessageInput = input("Write a message (max 1000 characters)", false);
        communityMessageInput.setSingleLine(false);
        communityMessageInput.setMinLines(2);
        communityMessageInput.setMaxLines(4);
        communityMessageInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        communityMessageInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1000)});
        chatCard.addView(communityMessageInput);
        Button send = button("Send message");
        chatCard.addView(send);
        send.setOnClickListener(v -> sendCommunityMessage());
        chatCard.addView(text("Long-press a message to report it, or to delete your own message. Reports are stored for administrator review; an administrator moderation console is not yet included.",
                12, muted, false));
        body.addView(chatCard);

        if (db == null || auth == null || auth.getCurrentUser() == null) {
            communityStatus.setText("Community chat requires Firebase configuration and a signed-in account.");
            communityMessageList.removeAllViews();
            communityMessageList.addView(text("No chat connection.", 14, muted, false));
            return;
        }
        db.collection("mosques").limit(250).get().addOnSuccessListener(snapshot -> {
            List<MosqueItem> rooms = new ArrayList<>();
            for (QueryDocumentSnapshot document : snapshot) {
                String name = firstNonEmpty(document.getString("name"), document.getString("mosqueName"), "Masjid");
                String city = firstNonEmpty(document.getString("city"), document.getString("addressCity"), "");
                String province = firstNonEmpty(document.getString("province"), document.getString("region"), "");
                rooms.add(new MosqueItem(document.getId(), name, city, province, Double.NaN, Double.NaN, false));
            }
            communityMosques.clear();
            communityMosques.addAll(rooms);
            List<String> labels = new ArrayList<>();
            for (MosqueItem mosque : rooms) {
                String place = mosque.city + (mosque.city.isEmpty() || mosque.province.isEmpty() ? "" : ", ") + mosque.province;
                labels.add(place.isEmpty() ? mosque.name : mosque.name + " · " + place);
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_spinner_item, labels);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            communitySpinner.setAdapter(adapter);
            if (rooms.isEmpty()) {
                communityStatus.setText("No mosque documents are published in Firestore yet. Chat rooms appear here once the directory is configured.");
                communityMessageList.removeAllViews();
                communityMessageList.addView(text("No published community rooms.", 14, muted, false));
                return;
            }
            communityStatus.setText("Loaded " + rooms.size() + " community room(s). Select a masjid to read and send messages.");
            communitySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (position >= 0 && position < communityMosques.size()) loadCommunityMessages(communityMosques.get(position));
                }
                @Override public void onNothingSelected(AdapterView<?> parent) {
                    stopCommunityListener();
                }
            });
            loadCommunityMessages(rooms.get(0));
        }).addOnFailureListener(error -> {
            communityStatus.setText("Could not load community rooms. Check Firebase configuration and Firestore read rules.");
            communityMessageList.removeAllViews();
            communityMessageList.addView(text("The directory is unavailable.", 14, muted, false));
        });
    }

    private void loadCommunityMessages(MosqueItem mosque) {
        stopCommunityListener();
        if (db == null || mosque == null || mosque.documentId.isEmpty()) return;
        activeCommunityId = mosque.documentId;
        communityMessageList.removeAllViews();
        communityMessageList.addView(text("Loading recent messages…", 14, muted, false));
        communityListener = db.collection("mosques").document(mosque.documentId)
                .collection("messages").orderBy("createdAt", Query.Direction.DESCENDING).limit(60)
                .addSnapshotListener((snapshot, error) -> {
                    if (!activeCommunityId.equals(mosque.documentId) || communityMessageList == null) return;
                    if (error != null) {
                        communityMessageList.removeAllViews();
                        communityMessageList.addView(text("Messages could not load. Check the deployed Firestore rules.", 14, muted, false));
                        return;
                    }
                    communityMessageList.removeAllViews();
                    if (snapshot == null || snapshot.isEmpty()) {
                        communityMessageList.addView(text("No messages yet. Start a respectful conversation.", 14, muted, false));
                        return;
                    }
                    List<DocumentSnapshot> messages = new ArrayList<>(snapshot.getDocuments());
                    Collections.reverse(messages);
                    for (DocumentSnapshot document : messages) addCommunityMessage(document, mosque.documentId);
                });
    }

    private void addCommunityMessage(DocumentSnapshot document, String mosqueId) {
        String bodyText = document.getString("text");
        String authorName = firstNonEmpty(document.getString("authorName"), "Member", "");
        String authorUid = document.getString("authorUid");
        if (bodyText == null || bodyText.trim().isEmpty()) return;

        LinearLayout messageCard = card();
        boolean mine = auth != null && auth.getCurrentUser() != null
                && auth.getCurrentUser().getUid().equals(authorUid);
        messageCard.setBackground(round(mine ? pale : Color.WHITE, 14));
        messageCard.addView(text(authorName + (mine ? " · You" : ""), 13, green, true));
        messageCard.addView(text(bodyText, 15, ink, false));
        Timestamp timestamp = document.getTimestamp("createdAt");
        if (timestamp != null) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault());
            dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Manila"));
            messageCard.addView(text(dateFormat.format(timestamp.toDate()), 11, muted, false));
        }
        messageCard.setOnLongClickListener(v -> {
            if (mine) {
                // Firestore rules intentionally prohibit reporting one's own message.
                new android.app.AlertDialog.Builder(MainActivity.this)
                        .setTitle("Message options")
                        .setMessage("Delete your message?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete", (dialog, which) -> deleteCommunityMessage(mosqueId, document.getId()))
                        .show();
            } else {
                new android.app.AlertDialog.Builder(MainActivity.this)
                        .setTitle("Report message")
                        .setMessage("Send this message for administrator review?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Report", (dialog, which) -> reportCommunityMessage(mosqueId, document.getId()))
                        .show();
            }
            return true;
        });
        communityMessageList.addView(messageCard);
    }

    private void sendCommunityMessage() {
        FirebaseUser user = auth == null ? null : auth.getCurrentUser();
        if (user == null || db == null) { message("Sign in to use community chat."); return; }
        if (activeCommunityId.isEmpty()) { message("Select a published mosque room first."); return; }
        String bodyText = communityMessageInput == null ? "" : communityMessageInput.getText().toString().trim();
        if (bodyText.isEmpty()) { message("Type a message first."); return; }
        if (bodyText.length() > 1000) { message("Messages must be 1000 characters or fewer."); return; }
        String authorName = user.getDisplayName();
        if (authorName == null || authorName.trim().isEmpty()) {
            String email = user.getEmail();
            authorName = email == null ? "Member" : email.split("@")[0];
        }
        if (authorName.length() > 60) authorName = authorName.substring(0, 60);
        java.util.Map<String, Object> messageData = new java.util.HashMap<>();
        messageData.put("authorUid", user.getUid());
        messageData.put("authorName", authorName);
        messageData.put("text", bodyText);
        messageData.put("createdAt", FieldValue.serverTimestamp());
        String mosqueId = activeCommunityId;
        communityMessageInput.setEnabled(false);
        db.collection("mosques").document(mosqueId).collection("messages").add(messageData)
                .addOnSuccessListener(reference -> {
                    if (communityMessageInput != null) {
                        communityMessageInput.setText("");
                        communityMessageInput.setEnabled(true);
                    }
                })
                .addOnFailureListener(error -> {
                    if (communityMessageInput != null) communityMessageInput.setEnabled(true);
                    if (communityStatus != null) communityStatus.setText("Message was not sent. Check deployed Firestore rules and connection.");
                });
    }

    private void reportCommunityMessage(String mosqueId, String messageId) {
        FirebaseUser user = auth == null ? null : auth.getCurrentUser();
        if (user == null || db == null) return;
        String[] reasons = {"Spam", "Abuse or harassment", "Misleading information", "Other"};
        new android.app.AlertDialog.Builder(this)
                .setTitle("Why are you reporting this message?")
                .setItems(reasons, (dialog, which) -> {
                    java.util.Map<String, Object> report = new java.util.HashMap<>();
                    report.put("reporterUid", user.getUid());
                    report.put("messageId", messageId);
                    report.put("reason", reasons[which]);
                    report.put("createdAt", FieldValue.serverTimestamp());
                    db.collection("mosques").document(mosqueId).collection("reports")
                            .document(user.getUid() + "_" + messageId).set(report)
                            .addOnSuccessListener(reference -> message("Report submitted for administrator review."))
                            .addOnFailureListener(error -> message("Report was not submitted. Check connection and Firestore rules."));
                }).show();
    }

    private void deleteCommunityMessage(String mosqueId, String messageId) {
        FirebaseUser user = auth == null ? null : auth.getCurrentUser();
        if (user == null || db == null) return;
        new android.app.AlertDialog.Builder(this)
                .setTitle("Delete your message?")
                .setMessage("This action cannot be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) ->
                        db.collection("mosques").document(mosqueId).collection("messages").document(messageId).delete()
                                .addOnFailureListener(error -> message("Message was not deleted. Check Firestore rules and connection.")))
                .show();
    }

    private void stopCommunityListener() {
        if (communityListener != null) {
            communityListener.remove();
            communityListener = null;
        }
        activeCommunityId = "";
    }

    private void qibla() {
        LinearLayout c = card();
        c.addView(text("Qibla direction", 24, ink, true));
        c.addView(text("The bearing is calculated on-device from your location to the Kaaba. A phone compass is optional; its accuracy depends on the device sensor and calibration.",
                14, muted, false));
        compassDial = new CompassDial(this);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, dp(240));
        cp.topMargin = dp(8);
        c.addView(compassDial, cp);
        qiblaHeading = text("Compass heading: —\nQibla bearing: —", 14, ink, true);
        c.addView(qiblaHeading);
        qiblaStatus = text("Tap below to calculate the Qibla bearing.", 13, muted, false);
        c.addView(qiblaStatus);
        Button b = button("Calculate Qibla from my location");
        c.addView(b);
        b.setOnClickListener(v -> {
            qiblaStatus.setText("Requesting location…");
            findCurrentLocation(new LocationResult() {
                @Override public void onSuccess(Location location) {
                    qiblaBearing = calculateQiblaBearing(location.getLatitude(), location.getLongitude());
                    qiblaBearingReady = true;
                    compassDial.setRelativeBearing((float) qiblaBearing);
                    qiblaStatus.setText(String.format(Locale.getDefault(),
                            "Qibla is %.1f° clockwise from true north. Hold the phone flat; rotate until the arrow points up.",
                            qiblaBearing));
                    startCompass();
                }
                @Override public void onError(String message) {
                    qiblaStatus.setText("Could not calculate Qibla: " + message);
                }
            });
        });
        c.addView(gap(8));
        c.addView(text("Location is used on-device for the bearing. If enabled, the live direction uses your device's compass sensor and magnetic declination correction.",
                12, muted, false));
        body.addView(c);
        startCompass();
    }

    private double calculateQiblaBearing(double latitude, double longitude) {
        double rad = Math.PI / 180.0;
        double lat1 = latitude * rad;
        double lat2 = 21.4225 * rad;
        double deltaLon = (39.8262 - longitude) * rad;
        double y = Math.sin(deltaLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2)
                - Math.sin(lat1) * Math.cos(lat2) * Math.cos(deltaLon);
        return normalizeDegrees(Math.toDegrees(Math.atan2(y, x)));
    }

    private double normalizeDegrees(double value) {
        return (value % 360.0 + 360.0) % 360.0;
    }

    private void startCompass() {
        if (sensorManager == null || !"qibla".equals(tab) || !qiblaBearingReady) return;
        if (compassSensor == null) {
            compassSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            if (compassSensor == null) compassSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        }
        if (compassSensor == null) {
            if (qiblaStatus != null) qiblaStatus.setText("Qibla bearing is available, but this device has no compatible compass sensor. Use the true-north bearing and an external compass.");
            return;
        }
        sensorManager.registerListener(compassListener, compassSensor, SensorManager.SENSOR_DELAY_UI);
    }

    private void stopCompass() {
        if (sensorManager != null) sensorManager.unregisterListener(compassListener);
    }

    private void findCurrentLocation(LocationResult callback) {
        boolean fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!fine && !coarse) {
            pendingLocationCallback = callback;
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1001);
            return;
        }
        startLocationLookup(callback);
    }

    private void startLocationLookup(LocationResult callback) {
        if (locationManager == null) {
            callback.onError("Location service is unavailable.");
            return;
        }
        activeLocationCallback = callback;
        locationRequestCompleted = false;
        try {
            Location best = null;
            for (String provider : locationManager.getProviders(true)) {
                try {
                    Location candidate = locationManager.getLastKnownLocation(provider);
                    if (candidate != null && (best == null || candidate.getTime() > best.getTime())) best = candidate;
                } catch (SecurityException ignored) { }
            }
            if (best != null && System.currentTimeMillis() - best.getTime() < 3 * 60 * 1000L) {
                finishLocation(best, null);
                return;
            }

            activeLocationListener = new LocationListener() {
                @Override public void onLocationChanged(Location location) { finishLocation(location, null); }
                @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
                @Override public void onProviderEnabled(String provider) { }
                @Override public void onProviderDisabled(String provider) { }
            };
            boolean requested = false;
            for (String provider : locationManager.getProviders(true)) {
                try {
                    locationManager.requestLocationUpdates(provider, 0L, 0f, activeLocationListener, Looper.getMainLooper());
                    requested = true;
                } catch (SecurityException | IllegalArgumentException ignored) { }
            }
            if (!requested) {
                finishLocation(null, "No location provider is enabled. Turn on device location and try again.");
                return;
            }
            locationTimeout = () -> finishLocation(null, "Location request timed out. Move to an area with a clearer signal and try again.");
            mainHandler.postDelayed(locationTimeout, 15000L);
        } catch (SecurityException e) {
            finishLocation(null, "Location permission was not granted.");
        } catch (Exception e) {
            finishLocation(null, "Could not access the device location service.");
        }
    }

    private void finishLocation(Location location, String error) {
        if (locationRequestCompleted) return;
        locationRequestCompleted = true;
        if (locationManager != null && activeLocationListener != null) {
            try { locationManager.removeUpdates(activeLocationListener); } catch (SecurityException ignored) { }
        }
        activeLocationListener = null;
        if (locationTimeout != null) mainHandler.removeCallbacks(locationTimeout);
        locationTimeout = null;
        LocationResult callback = activeLocationCallback;
        activeLocationCallback = null;
        if (callback == null) return;
        if (location != null) {
            activeLocation = location;
            callback.onSuccess(location);
        } else {
            callback.onError(error == null ? "Location is unavailable." : error);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) {
            boolean granted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            LocationResult callback = pendingLocationCallback;
            pendingLocationCallback = null;
            if (granted && callback != null) startLocationLookup(callback);
            else if (callback != null) callback.onError("Location permission was denied.");
        }
    }

    private void profile() {
        FirebaseUser user = auth == null ? null : auth.getCurrentUser();
        LinearLayout c = card();
        c.addView(text("Your profile", 23, ink, true));
        c.addView(text(user == null ? "Signed-in member" : (user.getEmail() == null ? "Signed-in member" : user.getEmail()),
                13, muted, false));

        nameInput = input("Display name", false);
        nameInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(60)});
        locationInput = input("City / province (optional)", false);
        locationInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100)});
        bioInput = input("Short bio (optional)", false);
        bioInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(180)});
        c.addView(nameInput);
        c.addView(locationInput);
        c.addView(bioInput);

        if (user != null && db != null) {
            db.collection("users").document(user.getUid()).get().addOnSuccessListener(snapshot -> {
                if (snapshot.exists() && nameInput != null && locationInput != null && bioInput != null) {
                    nameInput.setText(snapshot.getString("displayName") == null ? "" : snapshot.getString("displayName"));
                    locationInput.setText(snapshot.getString("location") == null ? "" : snapshot.getString("location"));
                    bioInput.setText(snapshot.getString("bio") == null ? "" : snapshot.getString("bio"));
                }
            });
        }

        Button save = button("Save profile");
        c.addView(save);
        TextView profileStatus = text("", 13, muted, false);
        c.addView(profileStatus);
        save.setOnClickListener(v -> {
            if (user == null || db == null) { profileStatus.setText("Sign in to save your profile."); return; }
            String displayName = nameInput.getText().toString().trim();
            String location = locationInput.getText().toString().trim();
            String bio = bioInput.getText().toString().trim();
            if (displayName.isEmpty()) { profileStatus.setText("Enter a display name."); return; }
            if (displayName.length() > 60 || location.length() > 100 || bio.length() > 180) {
                profileStatus.setText("Please shorten the profile fields to fit their limits."); return;
            }
            java.util.Map<String, Object> profile = new java.util.HashMap<>();
            profile.put("displayName", displayName);
            profile.put("location", location);
            profile.put("bio", bio);
            profile.put("updatedAt", System.currentTimeMillis());
            db.collection("users").document(user.getUid()).set(profile,
                    com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener(result -> profileStatus.setText("Profile saved."))
                    .addOnFailureListener(error -> profileStatus.setText("Could not save profile. Check your connection and Firestore rules."));
        });
        body.addView(c);
        body.addView(gap(12));
        Button signOut = button("Sign out");
        signOut.setBackground(round(Color.WHITE, 14));
        signOut.setTextColor(green);
        body.addView(signOut);
        signOut.setOnClickListener(v -> { if (auth != null) auth.signOut(); });
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        c.setBackground(round(Color.WHITE, 18));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(10);
        c.setLayoutParams(p);
        return c;
    }

    private EditText input(String hint, boolean password) {
        EditText edit = new EditText(this);
        edit.setSingleLine(!hint.toLowerCase(Locale.ROOT).contains("bio"));
        edit.setHint(hint);
        edit.setTextSize(15);
        edit.setPadding(dp(12), dp(10), dp(12), dp(10));
        edit.setBackground(round(bg, 12));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(10);
        edit.setLayoutParams(p);
        if (password) edit.setInputType(129);
        else if (hint.toLowerCase(Locale.ROOT).contains("email")) edit.setInputType(33);
        return edit;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(Color.WHITE);
        // Reject taps obscured by another window to reduce overlay/tapjacking risk.
        b.setFilterTouchesWhenObscured(true);
        b.setBackground(round(teal, 14));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(8);
        b.setLayoutParams(p);
        return b;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setGravity(Gravity.START);
        return t;
    }

    private View gap(int height) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(height)));
        return v;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }

    private void message(String message) {
        if (status != null && status.getParent() != null) status.setText(message);
        else new android.app.AlertDialog.Builder(this).setMessage(message).setPositiveButton("OK", null).show();
    }

    private String authMessage(Exception e) {
        if (e == null || e.getMessage() == null) return "Authentication failed. Check your details and try again.";
        String raw = e.getMessage();
        if (raw.contains("email address is badly formatted")) return "Enter a valid email address.";
        if (raw.contains("password is invalid") || raw.contains("no user record") || raw.contains("INVALID_LOGIN_CREDENTIALS")) return "Email or password is incorrect.";
        if (raw.contains("email address is already in use")) return "An account already exists for this email.";
        return "Authentication failed. Check your connection and account settings, then try again.";
    }

    @Override protected void onResume() {
        super.onResume();
        if ("qibla".equals(tab) && qiblaBearingReady) startCompass();
    }

    @Override protected void onPause() {
        stopCompass();
        super.onPause();
    }

    @Override protected void onDestroy() {
        stopCompass();
        stopCommunityListener();
        if (locationManager != null && activeLocationListener != null) {
            try { locationManager.removeUpdates(activeLocationListener); } catch (SecurityException ignored) { }
        }
        mainHandler.removeCallbacksAndMessages(null);
        ioExecutor.shutdownNow();
        super.onDestroy();
    }

    private final class CompassDial extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float relativeBearing = 0f;

        CompassDial(Context context) { super(context); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }

        void setRelativeBearing(float value) {
            relativeBearing = value;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float radius = Math.min(getWidth(), getHeight()) * 0.38f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(green);
            canvas.drawCircle(cx, cy, radius, paint);
            paint.setStrokeWidth(dp(1));
            paint.setColor(Color.rgb(184, 205, 193));
            for (int i = 0; i < 36; i++) {
                canvas.save();
                canvas.rotate(i * 10f, cx, cy);
                float inner = radius - (i % 9 == 0 ? dp(16) : dp(8));
                canvas.drawLine(cx, cy - radius, cx, cy - inner, paint);
                canvas.restore();
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(sp(14));
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setColor(green);
            canvas.drawText("N", cx, cy - radius + dp(30), paint);
            canvas.drawText("E", cx + radius - dp(24), cy + dp(5), paint);
            canvas.drawText("S", cx, cy + radius - dp(12), paint);
            canvas.drawText("W", cx - radius + dp(24), cy + dp(5), paint);

            canvas.save();
            canvas.rotate(relativeBearing, cx, cy);
            Path arrow = new Path();
            arrow.moveTo(cx, cy - radius * 0.70f);
            arrow.lineTo(cx - dp(12), cy + dp(12));
            arrow.lineTo(cx, cy + dp(5));
            arrow.lineTo(cx + dp(12), cy + dp(12));
            arrow.close();
            paint.setColor(teal);
            canvas.drawPath(arrow, paint);
            paint.setColor(green);
            canvas.drawCircle(cx, cy, dp(5), paint);
            canvas.restore();

            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(sp(12));
            paint.setTypeface(Typeface.DEFAULT);
            paint.setColor(muted);
            canvas.drawText("QIBLA", cx, cy + radius + dp(24), paint);
        }

        private float sp(float value) {
            return value * getResources().getDisplayMetrics().scaledDensity;
        }
    }
}
