# Fahrplan ohne Dateiauswahl

Die Anzeige konnte den Blattfahrplan bisher nur über „PDF auswählen“ aufnehmen. Jetzt gibt es eine
Android-App, die ihn selbst aus EIRE holt, dazu Wege ohne Dateiauswahl im Browser und Betrieb
ohne Internet.

## Android-App: nur Zugnummer und Datum

Die App enthält die Anzeige und meldet sich selbst bei EIRE an. Das darf eine Webseite im Browser
nicht (CORS), eine App schon.

**Installieren**, einmalig auf dem Tablet:

1. `https://github.com/deix97/IFPLA/releases/latest/download/fahrplan.apk` in Chrome öffnen.
2. Die heruntergeladene `fahrplan.apk` öffnen. Android fragt einmal, ob Chrome Apps installieren
   darf — erlauben, dann *Installieren*. Warnt Play Protect vor einer unbekannten App, über
   *Details* → *Trotzdem installieren* weiter; die App kommt nicht aus dem Play Store.
   Nötig ist mindestens Android 8.
3. „Fahrplan“ starten, unter **Zugang zu EIRE** Benutzername und Passwort eintragen, *Speichern*.

**Bei jedem Dienst:** Zugnummer eingeben (Datum steht auf heute), *Fahrpläne suchen*, den
passenden Fahrplan antippen. Die App zeigt die Liste wie EIRE, getrennt nach Gesamt- und
Einzelblattfahrplänen. **Welcher gilt, wählst du selbst** — die App nimmt nie von sich aus einen,
denn Varianten wie *Mbr 65 G* und *altern. Mbr 67 R/P* hängen von Lok und Bremsstellung ab.
Meldet der Leser keine unsicheren Stellen, springt die App gleich zur Anzeige, sonst bleibt der
Bericht zum Vergleich mit der PDF offen. Verlangt EIRE ein Einmalkennwort, erscheint ein Feld dafür.

**Zugangsdaten:** Sie liegen verschlüsselt mit einem Schlüssel aus dem Android-Schlüsselspeicher
und gehen nur an `eire.noncd.db.de`. Sie werden weder gesichert noch auf ein neues Gerät
übertragen. *Zugang löschen* entfernt sie samt Schlüssel.

**Updates:** Jede Änderung baut GitHub neu (Ablauf `.github/workflows/android-app.yml`); die neue
`fahrplan.apk` einfach über die alte installieren, Zugang und Einstellungen bleiben. Der
Signaturschlüssel liegt offen im Repo (`android/fahrplan.keystore`), damit jeder Bau dieselbe
Unterschrift trägt. Deshalb: **Updates nur von der Release-Seite dieses Repos installieren.**

Außerdem in der App: Teilen und „Öffnen mit“ für PDF, Dateiauswahl für Fahrplan und La, Ortung,
Bildschirm wach. Die App läuft immer im Vollbild; die Zurück-Geste schließt nur Blätter, nie die Anzeige.
Die Schrift folgt der Schriftgröße in den Einstellungen des Tablets, von 85 bis 130 %.

**PDF teilen, z. B. aus DiLoc:** Die Anzeige erkennt an Seite 1, was ankommt: Tages-La (Deckblatt
„La-Bereich …“), Fahrplananordnung (Fplo), Ersatzfahrplan (Efpl-Heft) oder Blattfahrplan. Findet der
Fahrplan-Leser keine Tabelle, versucht sie es noch als La. Mehrere PDF auf einmal gehen auch; es gilt
immer nur ein Fahrplan. Dasselbe gilt für „PDF auswählen“ und „Öffnen mit“, auch im Browser.

**Tages-La:** Gelesen werden alle Einträge mit Ort, Geschwindigkeit, Geltung und Gründen.
- **Mehrere Zeitpunkte:** „In Kraft ab“ und „Außer Kraft ab“ können mehrere Zeitpunkte nennen, z. B. „15.11. 22:20 und 16.11. 22:20“. Jedes Zeitfenster zählt für sich, ein Fenster ohne Ende bleibt offen.
- **Jahre vor 2000:** Zweistellige Jahre wie „01.01.90“ gehören ins 20. Jahrhundert.
- **Mehrere km-Bereiche:** Nennt eine Angabe mehrere km-Bereiche, wird der Gesamtbereich eingeordnet.

**Andere La-Unterlagen:** Sonder-La, Ergänzungen zur Tages-La, La der HGK und Bedarfsermittlungen werden erkannt und mit einem Hinweis abgewiesen. **Sonder-La werden nicht gelesen.** Ihre La-Einträge und Angaben zum Ersatzfahrplan (Änderungsheft) fehlen deshalb in der Anzeige, auch in Fahrplänen aus Fplo und Efpl-Heft. Welche Sonder-La gelten, steht auf dem Deckblatt der Tages-La, und die Anzeige zeigt diese Hinweise.

## Fahrplananordnung (Fplo) mit Ersatzfahrplan

Eine Fplo nennt je Zug die Betriebsstellen mit Ankunft und Abfahrt und verweist mit „Bfpl 3995 S.400
Sp. 2d“ ins Ersatzfahrplan-Heft. Aus beidem baut die Anzeige den Fahrplan: Strecke, km, Signale und
Geschwindigkeit der genannten Spalte (2a–2d, je eine Zugklasse) aus dem Heft, die Zeiten aus der Fplo.

1. **Einmal:** die Ersatzfahrplan-Hefte (z. B. 3993–3996) aus DiLoc an die Fahrplananzeige teilen.
   Sie bleiben auf dem Gerät, bis du sie entfernst; neue Ausgaben einfach dazuteilen.
2. **Nur wenn ein Dienst eine Fplo braucht** (Umleitung, Baustelle): die aktuelle Fplo aus DiLoc
   teilen. Sie gilt **24 Stunden** ab dem Teilen und verschwindet dann von selbst – Fplo ändern sich
   oft täglich, so bleibt keine veraltete liegen. Ein Neustart der App mitten im Dienst schadet nicht.
3. Im Blatt *Fahrplan laden* unter *Fahrplananordnung (Fplo) mit Ersatzfahrplan* Datum wählen
   (steht auf heute), ggf. Zugnummer eingeben, den Zug antippen.

Unter *Gespeichert: …* steht, bis wann eine Fplo da ist; alles lässt sich dort auch einzeln entfernen.

Der Heftleser verarbeitet auch fehlende Kopfmarken „1“, getrennte Ziffern und Seitenzahlen sowie
verschobene oder verkleinerte PDF-Nutzflächen. Er unterscheidet Seitenverweise und Fußnoten von
Fahrplandaten. Beim Datum gilt nur eine Ausgabe, deren Gültigkeitsbeginn bekannt und bereits
erreicht ist. Ein früher ohne Datum gespeichertes Heft wird aus seinen vorhandenen PDF-Bytes
nachgelesen und berichtigt. Fehlen Betriebsstellen oder Zeiten, wird kein unvollständiger Fahrplan
übernommen.

**Andere Formate:** Die Buchfahrplan-Hefte 6353/6354 und die HGK-Unterlagen sind keine Efpl-Hefte
mit den Spalten 2a–2d. Sie werden mit einem konkreten Hinweis erkannt; ihr Inhalt wird nicht als
ein einzelner Blattfahrplan zusammengelesen. Für diese Formate vorerst die Original-PDF bzw.
einen einzelnen Blattfahrplan aus EIRE verwenden.

Gibt es zu einem Zug mehrere Fassungen (z. B. 6.3 und 6.4), steht dabei, an welchem Tag er fährt;
die Anzeige baut immer die zum gewählten Tag. Fehlt ein Heft, sagt sie welches.

Wie gelesen wird:
* Eine Heftseite trägt oft mehrere Tabellen. Endet die eigene mit „Fortsetzung Seite N“, geht es auf
  Seite N beim passenden „Forts. …“-Kopf weiter; die Tabellen dazwischen gehören zu anderen Wegen.
* Fplo und Heft kürzen Betriebsstellen verschieden ab („K-Ehrenf Pbf“ / „Bft K-Ehrenfeld Pbf“). Die
  Zuordnung sucht die beste Übereinstimmung in der richtigen Reihenfolge.
* Führt die Fplo über das Ende einer Strecke hinaus, ohne neuen Heftverweis (z. B. Münster → Rheine),
  sucht die Anzeige im Heft die anschließende Tabelle in Fahrtrichtung, notfalls in den anderen Heften,
  die die Fplo für den Zug nennt.
* Jeder Heftabschnitt beginnt in der Anzeige mit einer Zeile „– Heft 3995 ab S. 400, Spalte 2d –“.
* Betriebsstellen der Fplo, die im Heft nicht zu finden sind, stehen im Bericht unter *Mit der PDF
  vergleichen*; ihre Zeit fehlt dann in der Anzeige. **Maßgebend bleiben Fplo und Heft.**

**Wie sie mit EIRE spricht** — genau wie die EIRE-Webseite selbst:
`POST /ui-api/authentication/login` (Benutzername, Passwort, bei Bedarf Einmalkennwort) liefert
ein Token, dann `GET /ui-api/timetables?trainNumber=…&date=JJJJMMTT` für die Liste und
`GET /ui-api/timetables/{id}?date=…&pages=&twoPagesOnOne=true|false` für die PDF, jeweils mit
`Authorization: Bearer`. Ändert DB InfraGO diese Schnittstelle, muss die App nachziehen.

## Im Browser, ohne App

Aus einer Webseite heraus lässt sich EIRE nicht abrufen (siehe oben). Im Browser bleibt deshalb:
**in EIRE anmelden, PDF herunterladen, einmal antippen — den Rest macht die Anzeige.**

| Weg | Gerät | Handgriff je Dienst |
| --- | --- | --- |
| **Teilen** | Android, Anzeige installiert | PDF in EIRE teilen → Fahrplananzeige |
| **Öffnen mit** | Rechner, Anzeige installiert | PDF doppelklicken bzw. „Öffnen mit“ |
| **Download-Ordner** | Chrome/Edge am Rechner | nur herunterladen, die Anzeige nimmt sie selbst |

### Ein Dienst im Browser auf dem Tablet

1. In Chrome `https://eire.noncd.db.de` öffnen und anmelden (nicht im Inkognito-Fenster, sonst
   merkt Chrome weder Anmeldung noch Passwort).
2. Zugnummer und Datum eingeben → *Fahrpläne anzeigen* → beim passenden Fahrplan auf das
   Download-Symbol tippen.
3. Die heruntergeladene **Datei** teilen: Downloads öffnen (oder in der Meldung von Chrome auf
   *Öffnen*), PDF lange drücken bzw. Teilen-Symbol → **Fahrplan**.

Die Anzeige öffnet sich und liest den Fahrplan. Teilt man stattdessen die geöffnete Seite, kommt
nur ein Link an; die Anzeige sagt das dann und nennt den richtigen Weg.

## Browser-Fassung als App installieren

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

## Ohne Internet

pdf.js 3.11.174 liegt unter `lib/` (Mozilla, Apache-2.0, Lizenz daneben) und wird von dort
geladen; cdnjs ist nur noch Rückfall. Schriften kommen nicht aus dem Netz: die Anzeige nutzt die
Systemschrift des Geräts, im Fahrplan deren schmale Form. `sw.js` legt Anzeige, Symbole und pdf.js
beim ersten Aufruf über https ab. Danach startet und liest die Anzeige ohne Netz. Eine andere
Herkunft für pdf.js lässt sich mit `?pdfjs=…` setzen; sie wird gemerkt.

## Was wo liegt

| Datei | Inhalt |
| --- | --- |
| `index.html` | die Anzeige, alles in einer Datei |
| `manifest.webmanifest` | Name, Symbole, Teilen-Ziel, Datei-Öffnen |
| `sw.js` | Ablage für den Betrieb ohne Netz, Annahme geteilter PDF |
| `symbol/` | Symbole der installierten Anzeige |
| `lib/` | pdf.js 3.11.174 samt Lizenz |
| `android/` | Android-App: Hülle um die Anzeige und Zugriff auf EIRE |
| `.github/workflows/android-app.yml` | baut die App und legt sie als Release ab |
| `tests/` | Regressionstests der Leser (Efpl-Heft, Tages-La) und lokale Bestandsprüfung |

## Leser prüfen

Mit Node.js, ohne zusätzliche Pakete: `node --test tests/`. Die Tests laden denselben Leser wie die Anzeige und verwenden künstliche Daten:
- **`efpl.test.cjs`:** Seitenzahlen, Spalten, Ziffern, Fußnoten, Gültigkeit und unvollständige Fplo.
- **`la.test.cjs`:** Zeitfenster und Jahre in „In Kraft“/„Außer Kraft“, km-Bereiche, Zeichenerklärung und die Erkennung von Sonder-La, Ergänzungen, HGK-La und Bedarfsermittlungen. Dazu kommt eine künstliche Tabellenseite, die durchgehend gelesen wird.

Den eigenen PDF-Bestand vollständig prüfen:
`node tests/efpl-bestand.cjs "C:\Pfad\zu\Efpl-Hefte" "C:\Pfad\außerhalb-des-Repos\Ergebnis.json"`.
Das Skript verwendet das mitgelieferte pdf.js, liest jede PDF-Seite, prüft die vier Spalten,
Seitenzugriff und Streckenindex und baut je Heft vier Testfahrpläne. Andere Formate werden
als solche geprüft. Die Zeiten dieser Testfahrpläne sind künstlich; die Prüfung ersetzt keinen
fachlichen Abgleich jeder Fahrplanzeile mit dem Original. Dienstliche PDF und daraus erzeugte
Ergebnisse gehören nicht ins öffentliche Repo.

Eine geteilte PDF liegt bis zum Start kurz in der Ablage des Dienstarbeiters und wird dort anschließend
gelöscht. Ersatzfahrplan-Hefte bleiben in der Geräteablage, Fplo höchstens 24 Stunden. Die Original-PDF
eines angezeigten Blattfahrplans bleibt im Speicher des Tabs; die gelesenen Fahrplandaten werden für
den Offline-Neustart gespeichert.
