package io.github.deix97.fahrplan;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Prüft den EIRE-Teil gegen einen Nachbau der Schnittstelle, wie sie die EIRE-Webseite benutzt. */
public class EireTest {
    private static final byte[] PDF = "%PDF-1.4\n% Probe\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

    private MockWebServer server;
    private String basis;
    private final AtomicInteger anmeldungen = new AtomicInteger();
    private final List<String> abrufe = Collections.synchronizedList(new ArrayList<>());
    private volatile String letzterAnmeldeKoerper = "";
    private volatile String gueltigesToken = "";
    private volatile String letzterUserAgent = "";
    private volatile boolean mitCode;
    private volatile boolean firewall;
    private volatile String[] zugang = {"lokfuehrer", "geheim"};

    static String jwt(String kennung, long expSek, boolean passwortAbgelaufen) {
        Base64.Encoder b = Base64.getUrlEncoder().withoutPadding();
        String kopf = b.encodeToString("{\"alg\":\"HS256\"}".getBytes(StandardCharsets.UTF_8));
        String last = b.encodeToString(("{\"sub\":\"" + kennung + "\",\"exp\":" + expSek + ",\"passwordExpired\":" + passwortAbgelaufen
                + ",\"permittedTimetableForecastDays\":0}").getBytes(StandardCharsets.UTF_8));
        return kopf + "." + last + ".unterschrift";
    }

    private static MockResponse antwort(int status, String typ, byte[] inhalt) {
        MockResponse m = new MockResponse().setResponseCode(status);
        if (typ != null) m.setHeader("Content-Type", typ);
        if (inhalt.length > 0) m.setBody(new Buffer().write(inhalt));
        return m;
    }

    private static MockResponse antwort(int status, String typ, String text) {
        return antwort(status, typ, text.getBytes(StandardCharsets.UTF_8));
    }

    @Before
    public void starte() throws IOException {
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest r) {
                String pfad = r.getRequestUrl().encodedPath();
                String frage = r.getRequestUrl().encodedQuery();
                try {
                    if ("/ui-api/authentication/login".equals(pfad)) return anmeldung(r);
                    if (pfad.startsWith("/ui-api/timetables")) return fahrplan(r, pfad, frage);
                } catch (Exception e) {
                    return antwort(500, "text/plain", String.valueOf(e));
                }
                return antwort(404, "text/plain", new byte[0]);
            }
        });
        server.start();
        basis = server.url("/ui-api").toString();
    }

    private MockResponse anmeldung(RecordedRequest r) throws Exception {
        String koerper = r.getBody().readUtf8();
        letzterAnmeldeKoerper = koerper;
        letzterUserAgent = r.getHeader("User-Agent");
        anmeldungen.incrementAndGet();
        if (firewall) return antwort(200, "text/html", "<html>blockiert</html>").setHeader("Suppid", "4711-0815");
        JSONObject b = new JSONObject(koerper);
        if ("gesperrt".equals(b.optString("username"))) return antwort(401, "text/plain", "User locked");
        if (!"lokfuehrer".equals(b.optString("username")) || !"geheim".equals(b.optString("password"))) {
            return antwort(401, "text/plain", "Bad credentials");
        }
        if (mitCode && !b.has("oneTimePassword")) return antwort(417, "text/plain", new byte[0]);
        if (mitCode && !"123456".equals(b.optString("oneTimePassword"))) return antwort(401, "text/plain", "Bad credentials");
        gueltigesToken = jwt("lokfuehrer-" + anmeldungen.get(), System.currentTimeMillis() / 1000 + 3600, false);
        return antwort(200, "application/json", new JSONObject().put("jwt", gueltigesToken).toString());
    }

    private MockResponse fahrplan(RecordedRequest r, String pfad, String frage) throws Exception {
        abrufe.add(r.getPath());
        if (gueltigesToken.isEmpty() || !("Bearer " + gueltigesToken).equals(r.getHeader("Authorization"))) {
            return antwort(401, "text/plain", new byte[0]);
        }
        if ("/ui-api/timetables".equals(pfad)) {
            if ("trainNumber=99999&date=20261113".equals(frage)) return antwort(204, null, new byte[0]);
            if (!"trainNumber=40020&date=20261113".equals(frage)) return antwort(400, "text/plain", new byte[0]);
            JSONArray liste = new JSONArray()
                    .put(new JSONObject().put("id", "abc-1").put("type", "G").put("trainNumber", "40020").put("origin", "Basel Gr/Muttenz")
                            .put("destination", "Venlo").put("travelPathValidityPeriod", "10.11.26 - 14.11.26, Basel Gr/Muttenz - Venlo, Tfz 80 6189 Mbr 65 G")
                            .put("validityStart", "10.11.26").put("validityEnd", "14.11.26"))
                    .put(new JSONObject().put("id", "abc-2").put("type", "A").put("trainNumber", "40020").put("origin", "Basel Gr/Muttenz")
                            .put("destination", "Venlo").put("travelPathValidityPeriod", "10.11.26 - 14.11.26, Basel Gr/Muttenz - Venlo, altern. Tfz 80 6189 Mbr 67 R/P")
                            .put("validityStart", "10.11.26").put("validityEnd", "14.11.26"));
            return antwort(200, "application/json", liste.toString());
        }
        if ("/ui-api/timetables/abc-1".equals(pfad)) return antwort(200, "application/pdf", PDF);
        if ("/ui-api/timetables/kaputt".equals(pfad)) return antwort(200, "text/html", "<html>Wartung</html>");
        return antwort(404, "text/plain", new byte[0]);
    }

    @After
    public void stoppe() throws IOException {
        server.shutdown();
    }

    private Eire eire() {
        return new Eire(basis, "Probe FahrplanApp/1.0", () -> zugang);
    }

    private static JSONObject json(Eire.Antwort a) throws Exception {
        return new JSONObject(a.text());
    }

    @Test
    public void ohneZugangWirdErDerSeiteAbverlangt() throws Exception {
        zugang = null;
        Eire.Antwort a = eire().suche("40020", "2026-11-13", null);
        assertEquals(428, a.status);
        assertEquals("zugang", json(a).getString("art"));
        assertEquals(0, anmeldungen.get());
    }

    @Test
    public void sucheMeldetSichAnUndNutztDasTokenWeiter() throws Exception {
        Eire e = eire();
        Eire.Antwort a = e.suche("40020", "2026-11-13", null);
        assertEquals(200, a.status);
        assertEquals("application/json", a.typ);
        JSONArray liste = new JSONArray(a.text());
        assertEquals(2, liste.length());
        assertEquals("abc-1", liste.getJSONObject(0).getString("id"));
        assertEquals(200, e.suche("40020", "2026-11-13", null).status);
        assertEquals("nur eine Anmeldung für zwei Suchen", 1, anmeldungen.get());
        JSONObject k = new JSONObject(letzterAnmeldeKoerper);
        assertEquals("lokfuehrer", k.getString("username"));
        assertEquals("geheim", k.getString("password"));
        assertFalse("ohne Code fehlt das Feld wie in der Webseite", k.has("oneTimePassword"));
        assertEquals("Probe FahrplanApp/1.0", letzterUserAgent);
        assertEquals("/ui-api/timetables?trainNumber=40020&date=20261113", abrufe.get(0));
    }

    @Test
    public void abgelehntesTokenFuehrtZuGenauEinerNeuenAnmeldung() throws Exception {
        Eire e = eire();
        assertEquals(200, e.suche("40020", "2026-11-13", null).status);
        gueltigesToken = "verfallen";
        Eire.Antwort a = e.suche("40020", "2026-11-13", null);
        assertEquals(200, a.status);
        assertEquals(2, anmeldungen.get());
    }

    @Test
    public void einmalkennwortWirdVerlangtUndMitgeschickt() throws Exception {
        mitCode = true;
        Eire e = eire();
        Eire.Antwort a = e.suche("40020", "2026-11-13", null);
        assertEquals(428, a.status);
        assertEquals("code", json(a).getString("art"));
        Eire.Antwort b = e.suche("40020", "2026-11-13", "123456");
        assertEquals(200, b.status);
        assertEquals("123456", new JSONObject(letzterAnmeldeKoerper).getString("oneTimePassword"));
        Eire.Antwort c = e.suche("40020", "2026-11-13", "999999");
        assertEquals(401, c.status);
    }

    @Test
    public void falschesPasswortUndGesperrtesKonto() throws Exception {
        zugang = new String[]{"lokfuehrer", "falsch"};
        Eire.Antwort a = eire().suche("40020", "2026-11-13", null);
        assertEquals(401, a.status);
        assertEquals("anmeldung", json(a).getString("art"));
        zugang = new String[]{"gesperrt", "egal"};
        Eire.Antwort b = eire().suche("40020", "2026-11-13", null);
        assertEquals("gesperrt", json(b).getString("art"));
    }

    @Test
    public void pdfWirdMitDenParameternDerWebseiteGeholt() throws Exception {
        Eire e = eire();
        Eire.Antwort a = e.pdf("abc-1", "2026-11-13", true);
        assertEquals(200, a.status);
        assertEquals("application/pdf", a.typ);
        assertArrayEquals(PDF, a.inhalt);
        assertTrue(a.kopf.get("X-Dateiname").contains("40020") || a.kopf.get("X-Dateiname").contains("20261113"));
        assertEquals("/ui-api/timetables/abc-1?date=20261113&pages=&twoPagesOnOne=true", abrufe.get(abrufe.size() - 1));
        e.pdf("abc-1", "2026-11-13", false);
        assertEquals("/ui-api/timetables/abc-1?date=20261113&pages=&twoPagesOnOne=false", abrufe.get(abrufe.size() - 1));
    }

    @Test
    public void keinFahrplanGibtLeereListe() throws Exception {
        Eire.Antwort a = eire().suche("99999", "2026-11-13", null);
        assertEquals(200, a.status);
        assertEquals("[]", a.text());
    }

    @Test
    public void etwasAnderesAlsPdfWirdNichtWeitergereicht() throws Exception {
        Eire.Antwort a = eire().pdf("kaputt", "2026-11-13", true);
        assertEquals(502, a.status);
        assertEquals("kein-pdf", json(a).getString("art"));
    }

    @Test
    public void firewallBlockWirdMitSupportIdGemeldet() throws Exception {
        firewall = true;
        Eire.Antwort a = eire().suche("40020", "2026-11-13", null);
        assertEquals(403, a.status);
        JSONObject f = json(a);
        assertEquals("firewall", f.getString("art"));
        assertTrue(f.getString("text").contains("4711-0815"));
    }

    @Test
    public void ungueltigeEingabenGehenGarNichtErstHinaus() throws Exception {
        assertEquals(400, eire().suche("040020", "2026-11-13", null).status);
        assertEquals(400, eire().suche("40x20", "2026-11-13", null).status);
        assertEquals(400, eire().suche("40020", "13.11.2026", null).status);
        assertEquals(0, anmeldungen.get());
        assertTrue(abrufe.isEmpty());
    }

    @Test
    public void nichtErreichbarWirdVerstaendlichGemeldet() throws Exception {
        server.shutdown();
        Eire.Antwort a = eire().suche("40020", "2026-11-13", null);
        assertEquals(502, a.status);
        assertEquals("netz", json(a).getString("art"));
    }

    @Test
    public void ablaufzeitWirdAusDemTokenGelesen() throws Exception {
        JSONObject n = Eire.nutzlast(jwt("x", 1893456000L, true));
        assertEquals(1893456000L, n.getLong("exp"));
        assertTrue(n.getBoolean("passwordExpired"));
        assertEquals(0, Eire.nutzlast("kein.token").length());
    }
}
