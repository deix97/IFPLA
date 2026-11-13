# Fahrplan ohne Dateiauswahl

Die Anzeige konnte den Blattfahrplan bisher nur über „PDF auswählen“ aufnehmen. Dazu kommen jetzt
vier Wege, die ohne Dateiauswahl auskommen, dazu Betrieb ohne Internet.

## Was mit EIRE geht und was nicht

**Nicht möglich:** `https://eire.noncd.db.de` direkt aus der Anzeige heraus abrufen. Das ist eine
fremde Herkunft mit Anmeldung über Cookie; der Browser lässt ein Skript einer anderen Seite dort
weder anmelden noch lesen (CORS). Das liegt am Browser, nicht an der Anzeige — kein Umbau ändert
etwas daran, und eine Abkürzung über einen fremden Vermittler hieße, die Zugangsdaten dorthin zu
geben. Auf dem Anzeigegerät allein bleibt deshalb: **in EIRE anmelden, PDF öffnen, einmal
antippen — den Rest macht die Anzeige.**

| Weg | Gerät | Handgriff je Dienst |
| --- | --- | --- |
| **Teilen** | Android, Anzeige installiert | PDF in EIRE teilen → Fahrplananzeige |
| **Öffnen mit** | Rechner, Anzeige installiert | PDF doppelklicken bzw. „Öffnen mit“ |
| **Download-Ordner** | Chrome/Edge am Rechner | nur herunterladen, die Anzeige nimmt sie selbst |
| **Quelle (JSON/URL)** | überall | keiner, wenn ein Helfer die PDF bereitlegt |

Soll gar nichts mehr angetippt werden, braucht es einen zweiten Rechner, der sich bei EIRE anmeldet
und die PDF in einen Ordner legt (`werkzeug/quelle.mjs` liefert sie dann an die Anzeige). Der
Anmeldeteil ist nicht gebaut.

## Ein Dienst auf dem Tablet

1. In Chrome `https://eire.noncd.db.de` öffnen und anmelden (nicht im Inkognito-Fenster, sonst
   merkt Chrome weder Anmeldung noch Passwort).
2. Zugnummer und Datum eingeben → *Fahrpläne anzeigen* → beim passenden Fahrplan auf das
   Download-Symbol tippen.
3. Die heruntergeladene **Datei** teilen: Downloads öffnen (oder in der Meldung von Chrome auf
   *Öffnen*), PDF lange drücken bzw. Teilen-Symbol → **Fahrplan**.

Die Anzeige öffnet sich und liest den Fahrplan. Teilt man stattdessen die geöffnete Seite, kommt
nur ein Link an; die Anzeige sagt das dann und nennt den richtigen Weg.

## Auf dem Android-Tablet installieren

1. In Chrome `https://deix97.github.io/IFPLA/` öffnen.
2. Oben im Blatt *Fahrplan laden* auf **Als App installieren** tippen, bestätigen.

Fertig: „Fahrplan“ liegt auf dem Startbildschirm, startet ohne Netz und steht im Teilen-Menü.
Zeigt die Anzeige den Knopf nicht, ist sie schon installiert — oder es geht über das Browsermenü
⋮ → *App installieren*.

Am Rechner geht es genauso (Chrome/Edge, Knopf oder Install-Symbol in der Adressleiste). Auf
iPhone/iPad gibt es nur *Teilen → Zum Home-Bildschirm*, ohne Teilen-Ziel und ohne Datei-Öffnen.

Voraussetzung ist **https**, wie bei GitHub Pages; aus einer lokalen Datei (`file://`) lässt sich
nichts installieren. GitHub Pages liefert den Zweig aus, der unter *Settings → Pages* eingestellt
ist — Änderungen erscheinen dort erst, wenn sie auf diesem Zweig liegen.

## Download-Ordner beobachten

Blatt **Fahrplan laden → Vom Gerät übernehmen → Ordner wählen**, dort den Download-Ordner angeben.
Die Anzeige nimmt beim Start und jedes Mal, wenn sie wieder vorn ist, die **jüngste PDF**
daraus. Dateinamen, die mit `la` beginnen, bleiben liegen. Gelesen wird nur; der Ordner bleibt
gewählt, bis *Ordner vergessen* gedrückt wird. Chrome und Edge am Rechner können das, Safari und
Chrome auf Android nicht — dort der Weg über Teilen.

## Quelle: Verzeichnis oder PDF

Unter **Automatisch holen** steht eine Quelle: entweder direkt eine PDF oder ein JSON-Verzeichnis.

```json
{
  "stand": "2026-11-13T03:40:00Z",
  "plan": "pdf/RE4711_2026-11-13.pdf",
  "la": ["pdf/La-Nord_2026-11-13.pdf"]
}
```

Relative Pfade gelten gegen die Quelle. Ändert sich `stand` nicht, wird nichts neu geladen.
`Beim Start holen` fragt die Quelle beim Öffnen der Anzeige ab, sonst holt `Jetzt holen`.

Über die Adresse geht es ohne Eintippen — praktisch für einen Kiosk-Browser:

| Teil | Wirkung |
| --- | --- |
| `?quelle=…` | Quelle setzen und merken |
| `?auto=1` | beim Start holen (`?auto=0` aus) |
| `?plan=…` | einmalig diese Fahrplan-PDF holen |
| `?la=…,…` | einmalig diese La-PDF holen |
| `?pdfjs=…` | pdf.js aus anderer Ablage als `lib/` |

Aus einem Skript oder Kiosk-Rahmen:

```js
Automatik.hole();                      // Quelle abfragen
Automatik.plan('pdf/zug.pdf');         // eine Fahrplan-PDF
Automatik.la(['pdf/la-nord.pdf']);     // La-PDF
Automatik.bytes('zug.pdf', bytes);     // schon geholte Bytes (ArrayBuffer/Uint8Array)
Automatik.verzeichnis({ plan: '…' });  // Verzeichnis von Hand
Automatik.stand();                     // was gerade geladen ist
```

## Quelle auf dem eigenen Rechner

`werkzeug/quelle.mjs` liefert Anzeige und PDF-Ordner aus einer Herkunft aus und baut das
Verzeichnis selbst. Nur Node, keine Pakete:

```bash
node werkzeug/quelle.mjs --ordner ./pdf
```

Dann `http://127.0.0.1:8723/?quelle=/fahrplan.json&auto=1` öffnen. Neue PDF in den Ordner legen,
die Anzeige holt sie beim nächsten Öffnen oder mit `Jetzt holen`. Einteilung nach Dateiname: beginnt er mit `la`, gilt die
Datei als Tages-La (je Bereich — Nord, Ost, Mitte, Südost, Süd — die jüngste), sonst als
Blattfahrplan (die jüngste). Eigenes Verzeichnis: `--verzeichnis ./fahrplan.json`.
Weitere Schalter: `--port`, `--app`, `--lib` (sonst `lib/` im Projekt), `--cors`.

## Ohne Internet

pdf.js 3.11.174 liegt unter `lib/` (Mozilla, Apache-2.0, Lizenz daneben) und wird von dort
geladen; cdnjs ist nur noch Rückfall. `sw.js` legt Anzeige, Symbole und pdf.js beim ersten Aufruf
über https ab. Danach startet und liest die Anzeige ohne Netz. Eine andere Herkunft für pdf.js
lässt sich mit `?pdfjs=…` setzen; sie wird gemerkt.

## Was wo liegt

| Datei | Inhalt |
| --- | --- |
| `index.html` | die Anzeige, alles in einer Datei |
| `manifest.webmanifest` | Name, Symbole, Teilen-Ziel, Datei-Öffnen |
| `sw.js` | Ablage für den Betrieb ohne Netz, Annahme geteilter PDF |
| `symbol/` | Symbole der installierten Anzeige |
| `lib/` | pdf.js 3.11.174 samt Lizenz |
| `werkzeug/quelle.mjs` | kleiner Server für PDF-Ordner und Verzeichnis |

Fahrplan-PDF werden nirgends dauerhaft abgelegt: eine geteilte PDF liegt bis zum Start der Anzeige
kurz in der Ablage des Dienstarbeiters und wird dann gelöscht, alles Weitere bleibt im Speicher des
Tabs.
