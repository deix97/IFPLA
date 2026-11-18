'use strict';
// Regressionstests des Tages-La-Lesers mit künstlichen Daten. Es werden keine dienstlichen PDF mitgeliefert.
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { TL } = require('./leser.cjs');

// Werte aus dem Kontext des Lesers als gewöhnliche Werte vergleichen
const eq = (a, b) => assert.deepEqual(JSON.parse(JSON.stringify(a)), b);
const ms = (j, m, t, h = 0, mi = 0) => new Date(j, m - 1, t, h, mi).getTime();
const item = (s, x, y, h = 10, w = 8) => ({ s, x, y, w, h });

// Ein Eintrag in der Form, die leseTabellen an bereite übergibt
function eintrag({ ort = ['12,3 - 12,8'], v = [60], sp6 = [], sp7 = [] } = {}) {
  return { nr: '1', neu: false, seite: 3, seiten: [3], y: [100, 140], bst: ['Alpha', '-', 'Beta'], sp6, sp7,
    teile: [{ ort, ortSym: [], v, bes: '', sym: [], fp: [], geltung: [], gruende: [], grundSym: [] }] };
}
function bereite(E, jahr = 2026) {
  TL.bereite([{ nr: '5', ri: 'a', name: 'Alpha - Beta', eintraege: [E], seite: 3 }], () => {}, jahr);
  return E;
}
const ZWEI_NAECHTE = { sp6: ['15.11.', '26', '22:20', 'und', '16.11.', '26', '22:20'], sp7: ['16.11.', '26', '04:30', '17.11.', '26', '04:30'] };

test('zweistellige Jahre vor 2000 bleiben im 20. Jahrhundert', () => {
  const jahr = (zeilen) => new Date(TL.datumZeit(zeilen, 2026).ms).getFullYear();
  assert.equal(jahr(['01.01.', '90']), 1990);
  assert.equal(jahr(['23.05.', '84']), 1984);
  assert.equal(jahr(['24.11.', '99']), 1999);
  assert.equal(jahr(['16.11.', '26']), 2026);
  assert.equal(jahr(['01.01.', '46']), 2046);
  assert.equal(jahr(['01.01.', '47']), 1947);
  assert.equal(jahr(['16.11.2026']), 2026);
  assert.equal(TL.datumZeit(['01.01.', '90'], 2026).text, '01.01.90');
});

test('ein Eintrag seit 1990 gilt heute und ist nicht „später“', () => {
  const E = bereite(eintrag({ sp6: ['01.01.', '90'] }));
  assert.equal(TL.fensterAm(E, ms(2026, 11, 16, 12)).st, 'jetzt');
});

test('mehrere Zeitpunkte in „In Kraft“ und „Außer Kraft“ werden alle gelesen', () => {
  const E = bereite(eintrag(ZWEI_NAECHTE));
  assert.equal(E.inKraft.text, '15.11.26 22:20 und 16.11.26 22:20');
  assert.equal(E.ausserKraft.text, '16.11.26 04:30 und 17.11.26 04:30');
  assert.equal(E.fenster.length, 2);
  assert.equal(E.fenster[1].von.text, '16.11.26 22:20');
  assert.equal(E.fenster[1].bis.text, '17.11.26 04:30');
});

test('jedes Zeitfenster zählt einzeln: die zweite Nacht ist nicht „außer Kraft“', () => {
  const E = bereite(eintrag(ZWEI_NAECHTE));
  assert.equal(TL.fensterAm(E, ms(2026, 11, 16, 3)).st, 'jetzt');
  const tag = TL.fensterAm(E, ms(2026, 11, 16, 12));
  assert.equal(tag.st, 'vor');
  assert.equal(tag.f.von.text, '16.11.26 22:20');
  const nacht = TL.fensterAm(E, ms(2026, 11, 16, 23));
  assert.equal(nacht.st, 'jetzt');
  assert.equal(nacht.f.bis.text, '17.11.26 04:30');
  assert.equal(TL.fensterAm(E, ms(2026, 11, 17, 5)).st, 'vorbei');
  eq(TL.fensterAm(E, ms(2026, 11, 16, 3)).weitere.map((f) => f.von.text), ['16.11.26 22:20']);
});

test('ein zweites Fenster ohne Ende bleibt offen', () => {
  const E = bereite(eintrag({ sp6: ['16.11.', '26', '23:55', 'und', '17.11.', '26', '23:55'], sp7: ['17.11.', '26', '05:00'] }));
  assert.equal(E.fenster[0].bis.text, '17.11.26 05:00');
  assert.equal(E.fenster[1].bis, null);
  assert.equal(TL.fensterAm(E, ms(2026, 11, 17, 12)).st, 'vor');
  assert.equal(TL.fensterAm(E, ms(2026, 11, 18, 3)).st, 'jetzt');
});

test('Zeitfenster: ohne Angaben gültig, nur mit Ende bis dahin, ein Ende vor dem Beginn kürzt nichts', () => {
  assert.equal(TL.fensterAm(bereite(eintrag()), ms(2026, 11, 16)).st, 'jetzt');
  const nurEnde = bereite(eintrag({ sp7: ['17.11.', '26', '05:00'] }));
  assert.equal(TL.fensterAm(nurEnde, ms(2026, 11, 16, 12)).st, 'jetzt');
  assert.equal(TL.fensterAm(nurEnde, ms(2026, 11, 17, 6)).st, 'vorbei');
  const frueh = bereite(eintrag({ sp6: ['16.11.', '26', '22:00'], sp7: ['15.11.', '26', '05:00'] }));
  assert.equal(TL.fensterAm(frueh, ms(2026, 11, 17, 12)).st, 'jetzt');
});

test('ein länger durchgehendes Ende verlängert das letzte Fenster', () => {
  const E = bereite(eintrag({ sp6: ['13.11.', '26', '00:01'], sp7: ['13.11.', '26', '05:00', '14.11.', '26', '05:00'] }));
  assert.equal(E.fenster.length, 1);
  assert.equal(E.fenster[0].bis.text, '14.11.26 05:00');
});

test('mehrere km-Bereiche einer Angabe ergeben den Gesamtbereich', () => {
  const T = bereite(eintrag({ ort: ['1,7 - 2,0', '-0,05 - 1,7'] })).teile[0];
  eq([T.von, T.bis, T.vonText, T.bisText], [-0.05, 2, '-0,05', '2,0']);
  eq(T.rest, []);
  const ab = bereite(eintrag({ ort: ['45,5 - 40,8', '44,0 - 43,0'] })).teile[0];
  eq([ab.von, ab.bis], [45.5, 40.8]);
  // Die Richtung gibt der erste Bereich vor, auch wenn der zweite aufsteigend gedruckt ist
  const gegen = bereite(eintrag({ ort: ['2,0 - 1,7', '-0,05 - 1,7'] })).teile[0];
  eq([gegen.von, gegen.bis, gegen.vonText, gegen.bisText], [2, -0.05, '2,0', '-0,05']);
  // Ohne Richtung im ersten Bereich bleibt alles wie gedruckt: keine erfundene Fahrtrichtung
  const punkt = bereite(eintrag({ ort: ['49,4 - 49,4', '49,376-49,398'] })).teile[0];
  eq([punkt.von, punkt.bis], [49.4, 49.4]);
  eq(punkt.rest, ['49,376-49,398']);
});

test('km-Bereich über zwei Zeilen, auch mit „km“ am Anfang', () => {
  const T = bereite(eintrag({ ort: ['14,7 - 14,8', 'km 14,767 -', 'km 14,795'] })).teile[0];
  eq([T.von, T.bis], [14.7, 14.8]);
  assert.match(T.ortText, /km 14,767–14,795/);
  const F = bereite(eintrag({ ort: ['km 7,5', '- 9,8'] })).teile[0];
  eq([F.von, F.bis], [7.5, 9.8]);
});

test('Pfeil der Zeichenerklärung nennt nur die Richtung, auch für Halteplätze', () => {
  const karte = {};
  const pg = { items: [item('3', 120, 100), item('Signal um angegebene Entfernung entgegen der Fahrtrichtung versetzt', 160, 100, 10, 300)],
    bilder: [{ fp: 'pfeil', x0: 100, x1: 118, y0: 92, y1: 104 }] };
  TL.leseLegende(pg, karte);
  assert.equal(karte.pfeil.text, 'entgegen der Fahrtrichtung versetzt');
});

test('Sonder-La, Ergänzung, HGK-La und Bedarfsermittlung werden benannt, die Tages-La nicht', () => {
  const tagesLa = 'DB Intern Tages-La Zusammenstellung der vorübergehenden Langsamfahrstellen und anderen Besonderheiten La-Bereich Süd Bitte beachten Sie folgende Sonderdrucke zur La: 19: IBN ESTW Musterstadt Montag, 16. November 2026 Daten vom 15. November 2026';
  assert.equal(TL.hinweis(tagesLa), '');
  assert.match(TL.hinweis('Seite 1 von 20 Sonder - La Bereich Süd Inbetriebnahme ESTW Musterstadt für La - Strecke 5'), /Sonder-La/);
  assert.match(TL.hinweis('Berichtigung Nr. 1 zu m Sonder d ruck zu r La, Bereich Nord'), /Sonder-La/);
  assert.match(TL.hinweis('La-Bereich Nord Sonderdruck zur La Inbetriebnahme Gültig ab 01.04.2026'), /Sonder-La/);
  assert.match(TL.hinweis('Ergänzung zur aktuellen Tages-La Nord Folgende Sonder-La sind zusätzlich gültig'), /Ergänzung zur Tages-La/);
  assert.match(TL.hinweis('La Übersicht KW 45 Zusammenstellung der vorübergehenden Langsamfahrstellen und anderen Besonderheiten im Bereich der HGK'), /HGK/);
  assert.match(TL.hinweis('DB Intern Bedarfsermittlung Sonderdruck zur La Süd La-Strecke 2'), /Bedarfsermittlung/);
  assert.equal(TL.hinweis('Weisung zum Regelwerk ' + 'x '.repeat(600) + 'Nutzung der Sonder-La'), '');
});

test('eine Sonder-La mit „La-Bereich“ auf dem Deckblatt wird nicht als Tages-La gelesen', () => {
  const seite = { w: 595, h: 842, pdfSeite: 1, linien: [], bilder: [], items: [item('La-Bereich Nord', 60, 80), item('Sonderdruck zur La', 60, 120), item('Gültig ab 01.04.2026', 60, 160)] };
  assert.match(TL.parse([seite]).fehler, /Sonder-La/);
});

// Durchgehend: Deckblatt und eine Tabellenseite wie in der echten Tages-La, gelesen von parse()
function tagesLa() {
  const deckblatt = { w: 595, h: 842, pdfSeite: 1, linien: [], bilder: [], items: [
    item('La-Bereich Süd', 60, 100), item('Montag, 16. November 2026', 60, 140),
    item('Daten vom 15. November 2026, 21:00 Uhr bis 17. November 2026, 24:00 Uhr', 60, 160)] };
  const voll = (y) => ({ x0: 30, x1: 560, y });
  const items = [item('1', 33, 60), item('8', 460, 60), item('5a', 10, 90, 14), item('Alpha - Beta', 60, 90, 14)];
  // Nr. 7: zwei Nächte, 120 km/h
  items.push(item('7', 20, 115), item('Alpha', 55, 115), item('-', 55, 125), item('Beta', 55, 135),
    item('19,4 - 35,6', 150, 115), item('120', 260, 125, 18), item('Schutz-Lfst', 450, 115));
  ['15.11.', '26', '22:20', 'und', '16.11.', '26', '22:20'].forEach((s, k) => items.push(item(s, 380, 115 + 10 * k)));
  ['16.11.', '26', '04:30', '17.11.', '26', '04:30'].forEach((s, k) => items.push(item(s, 412, 115 + 10 * (k < 3 ? k : k + 1))));
  // Nr. 8: seit 1990 in Kraft
  items.push(item('8', 20, 205), item('Gamma', 55, 205), item('166,6', 150, 205), item('01.01.', 380, 205), item('90', 380, 215),
    item('Wechsel der betrieblichen Regeln', 450, 205));
  const tabelle = { w: 595, h: 842, pdfSeite: 2, bilder: [], items, linien: [voll(70), voll(100), voll(190), voll(230)] };
  return [deckblatt, tabelle];
}

test('durchgehend: zwei Zeitfenster und ein Datum von 1990 aus der Tabellenseite', () => {
  const r = TL.parse(tagesLa());
  assert.equal(r.fehler, undefined);
  assert.equal(r.eintraege, 2);
  const [E7, E8] = r.bloecke[0].eintraege;
  eq([E7.nr, E7.teile[0].v, E7.teile[0].von, E7.teile[0].bis], ['7', [120], 19.4, 35.6]);
  assert.equal(E7.fenster.length, 2);
  assert.equal(TL.fensterAm(E7, ms(2026, 11, 16, 23)).st, 'jetzt');
  assert.equal(new Date(E8.inKraft.ms).getFullYear(), 1990);
  assert.equal(TL.fensterAm(E8, ms(2026, 11, 16, 12)).st, 'jetzt');
});
