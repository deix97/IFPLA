package io.github.deix97.fahrplan;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Hülle für die Fahrplananzeige. Die Seite kommt aus den eingebauten Dateien unter
 * https://appassets.androidplatform.net/ und läuft damit auch ohne Netz.
 * Unter /eire/ beantwortet die App Anfragen der Seite selbst und spricht dafür mit
 * EIRE; das darf eine Webseite im Browser nicht (CORS), die App schon.
 */
public final class MainActivity extends Activity {
    static final String HOST = "appassets.androidplatform.net";
    private static final String START = "https://" + HOST + "/index.html";
    private static final int ANFRAGE_DATEI = 1;
    private static final int ANFRAGE_ORT = 2;

    private FrameLayout wurzel;
    private WebView web;
    private Tresor tresor;
    private Eire eire;
    private Chrome chrome;
    private ValueCallback<Uri[]> dateiRueckruf;
    private GeolocationPermissions.Callback ortRueckruf;
    private String ortHerkunft;
    private View vollbild;
    private WebChromeClient.CustomViewCallback vollbildRueckruf;
    private volatile byte[] geteilt;
    private volatile String geteiltName;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle zustand) {
        super.onCreate(zustand);
        tresor = new Tresor(this);
        eire = new Eire(Eire.STANDARD_BASIS, WebSettings.getDefaultUserAgent(this) + " FahrplanApp/" + version(), tresor);

        wurzel = new FrameLayout(this);
        wurzel.setBackgroundColor(0xFF0C1012);
        web = new WebView(this);
        web.setBackgroundColor(0xFF0C1012);
        wurzel.addView(web, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(wurzel);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setGeolocationEnabled(true);
        s.setAllowFileAccess(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setTextZoom(100);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);

        web.addJavascriptInterface(new Bruecke(), "FahrplanNativ");
        web.setWebViewClient(new Klient());
        chrome = new Chrome();
        web.setWebChromeClient(chrome);

        nimm(getIntent());
        web.loadUrl(START);
        immersiv();
    }

    @Override
    protected void onNewIntent(Intent absicht) {
        super.onNewIntent(absicht);
        setIntent(absicht);
        nimm(absicht);
    }

    @Override
    public void onWindowFocusChanged(boolean fokus) {
        super.onWindowFocusChanged(fokus);
        if (fokus) immersiv();
    }

    /** Zurück schließt offene Blätter; die Anzeige selbst wird nie durch eine Wischgeste beendet. */
    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (vollbild != null) {
            chrome.onHideCustomView();
            return;
        }
        web.evaluateJavascript("document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape'}))", null);
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            wurzel.removeView(web);
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    @SuppressWarnings("deprecation")
    private void immersiv() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private String version() {
        try {
            String v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return v == null ? "?" : v;
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }

    /* ---------- geteilte oder geöffnete PDF ---------- */

    @SuppressWarnings("deprecation")
    private void nimm(Intent absicht) {
        if (absicht == null) return;
        Uri quelle = null;
        if (Intent.ACTION_SEND.equals(absicht.getAction())) {
            quelle = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    ? absicht.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class)
                    : absicht.getParcelableExtra(Intent.EXTRA_STREAM);
        } else if (Intent.ACTION_VIEW.equals(absicht.getAction())) {
            quelle = absicht.getData();
        }
        if (quelle == null) return;
        final Uri u = quelle;
        new Thread(() -> {
            try (InputStream ein = getContentResolver().openInputStream(u)) {
                if (ein == null) return;
                byte[] b = Eire.liesAlles(ein);
                geteiltName = anzeigename(u);
                geteilt = b;
                runOnUiThread(() -> {
                    if (web != null) web.evaluateJavascript("window.FahrplanNativGeteilt&&FahrplanNativGeteilt()", null);
                });
            } catch (IOException | SecurityException ignoriert) {
                /* nicht lesbar: dann bleibt die Anzeige, wie sie ist */
            }
        }, "geteilt").start();
    }

    private String anzeigename(Uri u) {
        try (Cursor c = getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String n = c.getString(0);
                if (n != null && !n.isEmpty()) return n;
            }
        } catch (RuntimeException ignoriert) {
            /* kein Name verfügbar */
        }
        return "geteilt.pdf";
    }

    /* ---------- Rückmeldungen von Dateiauswahl und Berechtigungen ---------- */

    @Override
    protected void onActivityResult(int anfrage, int ergebnis, Intent daten) {
        if (anfrage == ANFRAGE_DATEI && dateiRueckruf != null) {
            Uri[] gewaehlt = null;
            if (ergebnis == RESULT_OK && daten != null) {
                ClipData mehrere = daten.getClipData();
                if (mehrere != null && mehrere.getItemCount() > 0) {
                    gewaehlt = new Uri[mehrere.getItemCount()];
                    for (int i = 0; i < mehrere.getItemCount(); i++) gewaehlt[i] = mehrere.getItemAt(i).getUri();
                } else if (daten.getData() != null) {
                    gewaehlt = new Uri[]{daten.getData()};
                }
            }
            dateiRueckruf.onReceiveValue(gewaehlt);
            dateiRueckruf = null;
            return;
        }
        super.onActivityResult(anfrage, ergebnis, daten);
    }

    @Override
    public void onRequestPermissionsResult(int anfrage, String[] rechte, int[] ergebnisse) {
        if (anfrage == ANFRAGE_ORT && ortRueckruf != null) {
            boolean erlaubt = false;
            for (int e : ergebnisse) erlaubt |= e == PackageManager.PERMISSION_GRANTED;
            ortRueckruf.invoke(ortHerkunft, erlaubt, false);
            ortRueckruf = null;
            ortHerkunft = null;
            return;
        }
        super.onRequestPermissionsResult(anfrage, rechte, ergebnisse);
    }

    /* ---------- Antworten für die Seite ---------- */

    private static WebResourceResponse antwort(int status, String typ, byte[] inhalt, Map<String, String> zusatz) {
        Map<String, String> kopf = new HashMap<>();
        kopf.put("Cache-Control", "no-store");
        if (zusatz != null) kopf.putAll(zusatz);
        String grund = status == 200 ? "OK" : status == 204 ? "No Content" : "Status " + status;
        String zeichen = typ.startsWith("text/") || typ.contains("json") || typ.contains("javascript") ? "utf-8" : null;
        String reinTyp = typ.contains(";") ? typ.substring(0, typ.indexOf(';')) : typ;
        return new WebResourceResponse(reinTyp, zeichen, status, grund, kopf, new ByteArrayInputStream(inhalt));
    }

    private static WebResourceResponse antwort(Eire.Antwort a) {
        return antwort(a.status, a.typ, a.inhalt, a.kopf);
    }

    private static String typVon(String pfad) {
        String p = pfad.toLowerCase(Locale.ROOT);
        if (p.endsWith(".html")) return "text/html";
        if (p.endsWith(".js") || p.endsWith(".mjs")) return "text/javascript";
        if (p.endsWith(".css")) return "text/css";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".svg")) return "image/svg+xml";
        if (p.endsWith(".json") || p.endsWith(".webmanifest")) return "application/json";
        return "application/octet-stream";
    }

    private WebResourceResponse datei(String pfad) {
        if ("/".equals(pfad)) pfad = "/index.html";
        if (pfad.contains("..") || pfad.contains("//")) return antwort(404, "text/plain", new byte[0], null);
        try {
            InputStream ein = getAssets().open("web" + pfad);
            String typ = typVon(pfad);
            String zeichen = typ.startsWith("text/") || typ.equals("application/json") ? "utf-8" : null;
            Map<String, String> kopf = new HashMap<>();
            kopf.put("Cache-Control", "no-cache");
            return new WebResourceResponse(typ, zeichen, 200, "OK", kopf, ein);
        } catch (IOException e) {
            return antwort(404, "text/plain", "nicht da".getBytes(StandardCharsets.UTF_8), null);
        }
    }

    private WebResourceResponse eireAntwort(String was, Uri u) {
        switch (was) {
            case "suche":
                return antwort(eire.suche(u.getQueryParameter("zug"), u.getQueryParameter("datum"), u.getQueryParameter("code")));
            case "pdf":
                return antwort(eire.pdf(u.getQueryParameter("id"), u.getQueryParameter("datum"), !"0".equals(u.getQueryParameter("zweispaltig"))));
            default:
                return antwort(Eire.Antwort.fehler(404, "unbekannt", "Unbekannter Aufruf."));
        }
    }

    private WebResourceResponse geteiltAntwort() {
        byte[] b = geteilt;
        String name = geteiltName;
        geteilt = null;
        geteiltName = null;
        if (b == null) return antwort(204, "application/pdf", new byte[0], null);
        Map<String, String> kopf = new HashMap<>();
        try {
            kopf.put("X-Dateiname", URLEncoder.encode(name == null ? "geteilt.pdf" : name, "UTF-8").replace("+", "%20"));
        } catch (java.io.UnsupportedEncodingException e) {
            kopf.put("X-Dateiname", "geteilt.pdf");
        }
        return antwort(200, "application/pdf", b, kopf);
    }

    private final class Klient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView ansicht, WebResourceRequest anfrage) {
            Uri u = anfrage.getUrl();
            if (!"https".equals(u.getScheme()) || !HOST.equals(u.getHost())) return null;
            String pfad = u.getPath() == null ? "/" : u.getPath();
            if (!"GET".equalsIgnoreCase(anfrage.getMethod())) {
                return antwort(405, "text/plain", new byte[0], null);
            }
            if (pfad.startsWith("/eire/")) return eireAntwort(pfad.substring(6), u);
            if ("/geteilt".equals(pfad)) return geteiltAntwort();
            return datei(pfad);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView ansicht, WebResourceRequest anfrage) {
            Uri u = anfrage.getUrl();
            if ("https".equals(u.getScheme()) && HOST.equals(u.getHost())) return false;
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, u));
            } catch (ActivityNotFoundException ignoriert) {
                /* kein Browser da */
            }
            return true;
        }
    }

    private final class Chrome extends WebChromeClient {
        @Override
        public boolean onShowFileChooser(WebView ansicht, ValueCallback<Uri[]> rueckruf, FileChooserParams wahl) {
            if (dateiRueckruf != null) dateiRueckruf.onReceiveValue(null);
            dateiRueckruf = rueckruf;
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/pdf");
            if (wahl.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            try {
                startActivityForResult(i, ANFRAGE_DATEI);
                return true;
            } catch (ActivityNotFoundException e) {
                dateiRueckruf = null;
                return false;
            }
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(String herkunft, GeolocationPermissions.Callback rueckruf) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                rueckruf.invoke(herkunft, true, false);
                return;
            }
            ortRueckruf = rueckruf;
            ortHerkunft = herkunft;
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, ANFRAGE_ORT);
        }

        @Override
        public void onShowCustomView(View ansicht, CustomViewCallback rueckruf) {
            if (vollbild != null) {
                rueckruf.onCustomViewHidden();
                return;
            }
            vollbild = ansicht;
            vollbildRueckruf = rueckruf;
            wurzel.addView(ansicht, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            web.setVisibility(View.GONE);
            immersiv();
        }

        @Override
        public void onHideCustomView() {
            if (vollbild == null) return;
            wurzel.removeView(vollbild);
            vollbild = null;
            web.setVisibility(View.VISIBLE);
            if (vollbildRueckruf != null) vollbildRueckruf.onCustomViewHidden();
            vollbildRueckruf = null;
            immersiv();
        }
    }

    /** Für die Seite als window.FahrplanNativ; nur kleine, schnelle Aufrufe. */
    private final class Bruecke {
        @JavascriptInterface
        public String zugang() {
            JSONObject o = new JSONObject();
            try {
                String b = tresor.benutzer();
                o.put("gesetzt", b != null);
                o.put("benutzer", b == null ? "" : b);
            } catch (JSONException e) {
                return "{}";
            }
            return o.toString();
        }

        @JavascriptInterface
        public String setzeZugang(String benutzer, String passwort) {
            if (benutzer == null || benutzer.trim().isEmpty() || passwort == null || passwort.isEmpty()) return "leer";
            try {
                tresor.lege(benutzer.trim(), passwort);
                eire.vergissToken();
                return "ok";
            } catch (Exception e) {
                return "fehler";
            }
        }

        @JavascriptInterface
        public void vergissZugang() {
            tresor.loesche();
            eire.vergissToken();
        }

        @JavascriptInterface
        public void wach(boolean an) {
            runOnUiThread(() -> {
                if (an) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }

        @JavascriptInterface
        public String version() {
            return MainActivity.this.version();
        }
    }
}
