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

## Anzeige installieren

Nötig für Teilen und „Öffnen mit“: die Anzeige über **https** aufrufen (GitHub Pages reicht), dann
im Browsermenü *Zum Startbildschirm hinzufügen* bzw. *Installieren*. Dafür liegen
`manifest.webmanifest`, `sw.js` und `symbol/` neben der `index.html`; aus einer lokalen Datei
(`file://`) geht beides nicht.

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
| `?pdfjs=/lib/` | pdf.js aus eigener Ablage statt von cdnjs |

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
Weitere Schalter: `--port`, `--app`, `--lib`, `--cors`.

## Ohne Internet

Zwei Dinge holte die Anzeige bisher aus dem Netz: sich selbst und pdf.js von cdnjs. Im Führerraum
ist beides oft nicht erreichbar.

* `sw.js` legt Anzeige, Symbole und — falls vorhanden — pdf.js ab, sobald die Anzeige einmal über
  https (oder localhost) geladen wurde. Danach startet sie ohne Netz.
* pdf.js dazulegen:

```bash
npm pack pdfjs-dist@3.11.174 && tar -xzf pdfjs-dist-3.11.174.tgz
mkdir -p lib && cp package/build/pdf.min.js package/build/pdf.worker.min.js lib/
```

  Einmal mit `?pdfjs=/lib/` öffnen; die Herkunft wird gemerkt, bei Fehlschlag gilt wieder cdnjs.

## Was wo liegt

| Datei | Inhalt |
| --- | --- |
| `index.html` | die Anzeige, alles in einer Datei |
| `manifest.webmanifest` | Name, Symbole, Teilen-Ziel, Datei-Öffnen |
| `sw.js` | Ablage für den Betrieb ohne Netz, Annahme geteilter PDF |
| `symbol/` | Symbole der installierten Anzeige |
| `werkzeug/quelle.mjs` | kleiner Server für PDF-Ordner und Verzeichnis |

Fahrplan-PDF werden nirgends dauerhaft abgelegt: eine geteilte PDF liegt bis zum Start der Anzeige
kurz in der Ablage des Dienstarbeiters und wird dann gelöscht, alles Weitere bleibt im Speicher des
Tabs.
