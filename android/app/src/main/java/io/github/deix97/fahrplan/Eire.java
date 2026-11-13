package io.github.deix97.fahrplan;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spricht mit der EBuLa-Rückfallebene (EIRE) so, wie es deren eigene Webseite tut:
 * POST /ui-api/authentication/login liefert ein JWT, danach gehen
 * GET /ui-api/timetables (Suche) und GET /ui-api/timetables/{id} (PDF) mit
 * "Authorization: Bearer". Die Antworten werden für die Anzeige aufbereitet:
 * Erfolg reicht EIRE unverändert durch, Fehler kommen als {"art","text"}.
 */
final class Eire {
    static final String STANDARD_BASIS = "https://eire.noncd.db.de/ui-api";
    private static final int GRENZE = 40 * 1024 * 1024;

    /** Woher Benutzername und Passwort kommen; null, solange keine hinterlegt sind. */
    interface Zugangsquelle {
        String[] lies();
    }

    /** Antwort an die Seite. */
    static final class Antwort {
        final int status;
        final String typ;
        final byte[] inhalt;
        final Map<String, String> kopf = new LinkedHashMap<>();

        Antwort(int status, String typ, byte[] inhalt) {
            this.status = status;
            this.typ = typ;
            this.inhalt = inhalt;
        }

        static Antwort fehler(int status, String art, String text) {
            JSONObject o = new JSONObject();
            try {
                o.put("art", art);
                o.put("text", text);
            } catch (JSONException e) {
                throw new IllegalStateException(e);
            }
            return new Antwort(status, "application/json", o.toString().getBytes(StandardCharsets.UTF_8));
        }

        String text() {
            return new String(inhalt, StandardCharsets.UTF_8);
        }
    }

    /** Rohantwort von EIRE. */
    static final class Http {
        final int status;
        final byte[] inhalt;
        final String suppid;

        Http(int status, byte[] inhalt, String suppid) {
            this.status = status;
            this.inhalt = inhalt;
            this.suppid = suppid;
        }

        String text() {
            return new String(inhalt, StandardCharsets.UTF_8);
        }
    }

    private final String basis;
    private final String kennung;
    private final Zugangsquelle zugang;
    private String token;
    private long tokenBisMs;

    Eire(String basis, String kennung, Zugangsquelle zugang) {
        this.basis = basis;
        this.kennung = kennung;
        this.zugang = zugang;
    }

    /** Nach einem neuen Zugang: altes Token verwerfen. */
    synchronized void vergissToken() {
        token = null;
        tokenBisMs = 0;
    }

    synchronized Antwort suche(String zug, String datum, String code) {
        if (zug == null || !zug.matches("0|[1-9][0-9]{0,11}")) {
            return Antwort.fehler(400, "eingabe", "Zugnummer: nur Ziffern, ohne führende Null.");
        }
        if (datum == null || !datum.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return Antwort.fehler(400, "eingabe", "Das Datum fehlt.");
        }
        String pfad = "/timetables?trainNumber=" + kodiere(zug) + "&date=" + datum.replace("-", "");
        try {
            Object r = mitToken(code, pfad);
            if (r instanceof Antwort) return (Antwort) r;
            Http a = (Http) r;
            if (a.status == 204 || (a.status == 200 && a.inhalt.length == 0)) {
                return new Antwort(200, "application/json", "[]".getBytes(StandardCharsets.UTF_8));
            }
            if (a.status == 200) return new Antwort(200, "application/json", a.inhalt);
            return serverFehler(a.status);
        } catch (IOException e) {
            return Antwort.fehler(502, "netz", "EIRE ist nicht erreichbar. Netz prüfen und erneut versuchen.");
        }
    }

    synchronized Antwort pdf(String id, String datum, boolean zweispaltig) {
        if (id == null || id.isEmpty() || id.length() > 200) {
            return Antwort.fehler(400, "eingabe", "Kein Fahrplan gewählt.");
        }
        if (datum == null || !datum.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return Antwort.fehler(400, "eingabe", "Das Datum fehlt.");
        }
        String d = datum.replace("-", "");
        String pfad = "/timetables/" + kodiere(id) + "?date=" + d + "&pages=&twoPagesOnOne=" + zweispaltig;
        try {
            Object r = mitToken(null, pfad);
            if (r instanceof Antwort) return (Antwort) r;
            Http a = (Http) r;
            if (a.status != 200) return serverFehler(a.status);
            if (a.inhalt.length < 5 || a.inhalt[0] != '%' || a.inhalt[1] != 'P' || a.inhalt[2] != 'D' || a.inhalt[3] != 'F') {
                return Antwort.fehler(502, "kein-pdf", "EIRE hat statt einer PDF etwas anderes geschickt.");
            }
            Antwort pdf = new Antwort(200, "application/pdf", a.inhalt);
            pdf.kopf.put("X-Dateiname", kodiere("EIRE_" + d + "_" + id + ".pdf"));
            return pdf;
        } catch (IOException e) {
            return Antwort.fehler(502, "netz", "EIRE ist nicht erreichbar. Netz prüfen und erneut versuchen.");
        }
    }

    /**
     * Holt mit gültigem Token; meldet sich bei Bedarf an. Lehnt EIRE das Token ab,
     * wird einmal neu angemeldet. Rückgabe: Http bei Erfolg, Antwort bei Fehler.
     */
    private Object mitToken(String code, String pfad) throws IOException {
        boolean frisch = false;
        if (code != null && !code.isEmpty()) {
            token = null;
        }
        if (token == null || System.currentTimeMillis() > tokenBisMs - 30_000L) {
            Antwort f = anmelden(code);
            if (f != null) return f;
            frisch = true;
        }
        Http a = hole("GET", pfad, null, token);
        if (a.suppid != null) return firewall(a.suppid);
        if (a.status == 401 && !frisch) {
            vergissToken();
            Antwort f = anmelden(null);
            if (f != null) return f;
            a = hole("GET", pfad, null, token);
            if (a.suppid != null) return firewall(a.suppid);
        }
        return a;
    }

    /** Anmeldung wie in der Webseite; null bei Erfolg, sonst die Antwort für die Seite. */
    private Antwort anmelden(String code) throws IOException {
        String[] z = zugang.lies();
        if (z == null || z[0] == null || z[0].isEmpty() || z[1] == null || z[1].isEmpty()) {
            return Antwort.fehler(428, "zugang", "Zuerst Benutzername und Passwort für EIRE eintragen.");
        }
        JSONObject koerper = new JSONObject();
        try {
            koerper.put("username", z[0]);
            koerper.put("password", z[1]);
            /* ohne Code lässt die Webseite das Feld weg */
            if (code != null && !code.isEmpty()) koerper.put("oneTimePassword", code);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        Http a = hole("POST", "/authentication/login", koerper.toString(), null);
        if (a.suppid != null) return firewall(a.suppid);
        switch (a.status) {
            case 200: {
                JSONObject o;
                try {
                    o = new JSONObject(a.text());
                } catch (JSONException e) {
                    return Antwort.fehler(502, "server", "EIRE hat unverständlich geantwortet.");
                }
                if (o.has("qrCodeImageBase64")) {
                    return Antwort.fehler(409, "mfa-einrichten",
                            "EIRE verlangt, die Zwei-Faktor-Anmeldung einzurichten. Das einmal im Browser in EIRE erledigen, dann hier erneut suchen.");
                }
                String jwt = o.optString("jwt", "");
                if (jwt.isEmpty()) return Antwort.fehler(502, "server", "EIRE hat kein Token geschickt.");
                JSONObject nutzlast = nutzlast(jwt);
                if (nutzlast.optBoolean("passwordExpired", false)) {
                    return Antwort.fehler(423, "passwort-abgelaufen",
                            "Das Passwort ist abgelaufen. In EIRE im Browser ein neues vergeben und es hier eintragen.");
                }
                long exp = nutzlast.optLong("exp", 0L);
                token = jwt;
                tokenBisMs = exp > 0 ? exp * 1000L : System.currentTimeMillis() + 10 * 60_000L;
                return null;
            }
            case 401:
                if (a.text().contains("User locked")) {
                    return Antwort.fehler(401, "gesperrt", "Das Nutzerkonto ist gesperrt. Bitte an den IRE-Administrator wenden.");
                }
                return Antwort.fehler(401, "anmeldung", "Falsches Passwort oder Benutzer nicht vorhanden.");
            case 417:
                return Antwort.fehler(428, "code", "EIRE verlangt das Einmalkennwort aus der Authentifizierungs-App.");
            case 423:
                return Antwort.fehler(423, "passwort-abgelaufen",
                        "Das Passwort ist abgelaufen. In EIRE im Browser ein neues vergeben und es hier eintragen.");
            case 429:
                return Antwort.fehler(429, "zu-viele", "Max. Loginversuche erreicht. Etwas warten und erneut versuchen.");
            default:
                return serverFehler(a.status);
        }
    }

    private static Antwort firewall(String suppid) {
        return Antwort.fehler(403, "firewall", "Die Firewall von EIRE hat die Anfrage blockiert (Support-ID " + suppid
                + "). Bei Bedarf an ebula@deutschebahn.com wenden.");
    }

    /** Texte wie in der Webseite von EIRE. */
    private static Antwort serverFehler(int status) {
        String text;
        switch (status) {
            case 400:
            case 422:
                text = "Die Daten konnten aufgrund einer fehlerhaften Anfrage nicht bereitgestellt werden.";
                break;
            case 401:
            case 403:
                text = "Du bist nicht berechtigt, diese Daten abzurufen. Für andere Tage als heute lässt EIRE das oft nicht zu.";
                break;
            case 404:
                text = "Diesen Fahrplan gibt es bei EIRE nicht (mehr).";
                break;
            case 429:
                text = "Zu viele Anfragen. Einen Moment warten und erneut versuchen.";
                break;
            case 502:
                text = "Die Daten konnten aufgrund eines Datenbankfehlers nicht bereitgestellt werden.";
                break;
            case 503:
                text = "Der EBuLa-IRE-Server ist nicht erreichbar.";
                break;
            default:
                text = "Im EBuLa-IRE-Server ist ein Fehler aufgetreten (" + status + ").";
        }
        return Antwort.fehler(status >= 400 && status <= 599 ? status : 502, "server", text);
    }

    private Http hole(String methode, String pfad, String json, String bearer) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(basis + pfad).openConnection();
        try {
            c.setRequestMethod(methode);
            c.setConnectTimeout(15_000);
            c.setReadTimeout(45_000);
            c.setInstanceFollowRedirects(false);
            c.setUseCaches(false);
            c.setRequestProperty("Accept", "application/json, text/plain, */*");
            c.setRequestProperty("Accept-Language", "de-DE,de;q=0.9");
            if (kennung != null) c.setRequestProperty("User-Agent", kennung);
            if (bearer != null) c.setRequestProperty("Authorization", "Bearer " + bearer);
            if (json != null) {
                byte[] b = json.getBytes(StandardCharsets.UTF_8);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                /* kein Streaming-Modus: sonst verwirft HttpURLConnection bei 401 den Antworttext,
                   und "User locked" ließe sich nicht von falschem Passwort unterscheiden */
                try (OutputStream aus = c.getOutputStream()) {
                    aus.write(b);
                }
            }
            int status = c.getResponseCode();
            InputStream ein = status >= 400 ? c.getErrorStream() : c.getInputStream();
            byte[] inhalt = ein == null ? new byte[0] : liesAlles(ein);
            return new Http(status, inhalt, c.getHeaderField("Suppid"));
        } finally {
            c.disconnect();
        }
    }

    static byte[] liesAlles(InputStream ein) throws IOException {
        try (InputStream e = ein) {
            ByteArrayOutputStream puffer = new ByteArrayOutputStream();
            byte[] stueck = new byte[16 * 1024];
            int n;
            while ((n = e.read(stueck)) > 0) {
                puffer.write(stueck, 0, n);
                if (puffer.size() > GRENZE) throw new IOException("Antwort zu groß");
            }
            return puffer.toByteArray();
        }
    }

    /** Nutzlast eines JWT, ohne Prüfung der Signatur: nur für Ablaufzeit und Hinweise. */
    static JSONObject nutzlast(String jwt) {
        try {
            String[] teile = jwt.split("\\.");
            if (teile.length < 2) return new JSONObject();
            byte[] roh = Base64.getUrlDecoder().decode(teile[1]);
            return new JSONObject(new String(roh, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException | JSONException e) {
            return new JSONObject();
        }
    }

    private static String kodiere(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
