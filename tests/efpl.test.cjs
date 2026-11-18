'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const { BF, html } = require('./leser.cjs');

const item = (s, x, y, w = 8, h = 10) => ({ s, x, y, w, h });
function table({ nr = 1, one = true, splitFooter = false } = {}) {
  const centers = { '1': 37, '2a': 64, '2b': 94, '2c': 124, '2d': 154, '3a': 232, '3b': 303, '4a': 330 };
  const items = Object.entries(centers).filter(([s]) => one || s !== '1').map(([s, x]) => item(s, x - 4, 100));
  items.push(item('Strecke 123456 Alpha - Gamma', 23, 60, 200), item('ab km', 27, 140));
  for (const x of [64, 94, 124, 154]) items.push(item('80', x - 5, 160, 10));
  ['Alpha', 'Beta', 'Gamma'].forEach((s, j) => {
    const y = 180 + j * 40;
    items.push(item(s, 176, y, 40), item(j + ',0', 298, y), item(String(j * 5), 328, y));
  });
  items.push(item('4000', 23, 560, 20));
  if (splitFooter) items.push(item('Seite', 350, 560, 20), item(String(nr), 380, 560, 15));
  else items.push(item('Seite ' + nr, 350, 560, 40));
  return { w: 420, h: 595, items };
}
const util = { Util: { transform: (a, b) => b } };
function fakeDoc(pages) {
  return {
    numPages: pages.length,
    getPage: async n => {
      const pg = pages[n - 1];
      if (!pg) throw Error('Seite fehlt');
      return {
        getViewport: () => ({ width: pg.w, height: pg.h, transform: [1, 0, 0, 1, 0, 0] }),
        getTextContent: async () => ({ items: pg.items.map(i => ({ str: i.s, width: i.w, height: i.h, transform: [1, 0, 0, 1, i.x, i.y] })) })
      };
    }
  };
}

test('fehlende Kopfmarke 1 schließt keine Fahrplantabelle aus', () => {
  for (const c of ['2a', '2b', '2c', '2d']) {
    const b = BF.ersatzBlattseite(table({ one: false }), c, 2);
    assert.ok(b);
    assert.equal(b.nr, 1);
    assert.ok(b.items.some(i => i.s === 'Gamma'));
  }
  assert.equal(BF.ersatzBlattseite(table(), '2x', 2), null);
});

test('Fußzeile zählt, nicht ein Seitenverweis im Fahrweg', () => {
  const pg = table({ nr: 230, splitFooter: true });
  pg.items.push(item('Seite 236', 230, 290, 40));
  const b = BF.ersatzBlattseite(pg, '2a', 231);
  assert.equal(b.nr, 230);
  assert.equal(b.heft, '4000');
  assert.ok(b.items.some(i => i.s === 'Gamma'));
  assert.ok(b.items.every(i => i.s !== 'Seite' && i.s !== '4000'));
});

test('einzeln gespeicherte Ziffern einer Fußzeile werden zusammengefügt', () => {
  const pg = table({ nr: 230, splitFooter: true });
  pg.items.pop();
  pg.items.push(item('2', 380, 560, 5), item('3', 385, 560, 5), item('0', 390, 560, 5));
  assert.equal(BF.ersatzBlattseite(pg, '2a', 231).nr, 230);
});

test('erklärender Musterkopf ohne Seitenfuß ist keine Fahrplanseite', () => {
  const pg = table(); pg.items = pg.items.filter(i => i.y < 300);
  assert.equal(BF.ersatzBlattseite(pg, '2a', 2), null);
});

test('leerer Musterkopf bleibt auch mit regulärem Seitenfuß außerhalb des Streckenindex', () => {
  const pg = table(); pg.items = pg.items.filter(i => i.y <= 140 || i.y === 560);
  assert.equal(BF.ersatzBlattseite(pg, '2a', 2), null);
});

test('Seitenfuß auf verschobener oder verkleinerter Nutzfläche', () => {
  const pg = table({ nr: 5 });
  pg.w = 595; pg.h = 842;
  pg.items = pg.items.map(i => ({ ...i, x: i.x + 175 }));
  assert.equal(BF.ersatzBlattseite(pg, '2a', 6).nr, 5);
});

test('Jahresangabe zwischen Heftnummer und Seitenzahl in der Fußzeile', () => {
  const pg = table({ nr: 10 });
  pg.items.push(item('Fpl 2026', 50, 560, 40));
  assert.equal(BF.ersatzBlattseite(pg, '2a', 11).nr, 10);
});

test('Heftnummer mit vorangestelltem Efpl in der Fußzeile', () => {
  const pg = table({ nr: 21 });
  pg.items = pg.items.map(i => i.s === '4000' ? { ...i, s: 'Efpl 4000', w: 40 } : i);
  assert.equal(BF.ersatzBlattseite(pg, '2a', 22).nr, 21);
});

test('Seitenzahl im Fußnotentext wird nicht zur Geschwindigkeit', () => {
  const pg = table({ nr: 300 });
  pg.items.push(item('1', 23, 303.5, 3, 5.5), item(') Fortsetzung Regelweg S.', 26, 307, 89, 9), item('303', 118, 307, 14, 9));
  const b = BF.ersatzBlattseite(pg, '2c', 301), z = BF.zeilenAus(b);
  assert.match(z.fuss['1'], /Regelweg S\. 303/);
  assert.ok(z.zeilen.every(z => z.c2.every(v => v.v !== 303)));
});

test('4 und 0 werden vor der Spaltenprojektion als 40 gelesen', () => {
  const pg = table();
  pg.items.push(item('4', 150, 220, 5), item('0', 155, 220, 5));
  const original = JSON.stringify(pg);
  const b = BF.ersatzBlattseite(pg, '2d', 2);
  const z = BF.zeilenAus(b).zeilen.find(z => Math.abs(z.y - 220) < 2);
  assert.equal(z.c2.length, 1);
  assert.equal(z.c2[0].v, 40);
  const other = BF.ersatzBlattseite(pg, '2c', 2);
  assert.ok(!other.items.some(i => i.x === 70 && i.y === 220));
  assert.equal(JSON.stringify(pg), original, 'erneutes Lesen darf das Original nicht verändern');
});

test('benachbarte Geschwindigkeitsspalten und Zeilen werden nicht verbunden', () => {
  const pg = table();
  pg.items.push(item('4', 150, 220, 5), item('0', 155, 225, 5), item('30', 119, 220, 10));
  assert.equal(BF.ersatzBlattseite(pg, '2d', 2).items.filter(i => i.x === 70 && i.y >= 220).map(i => i.s).join(','), '4,0');
  assert.equal(BF.ersatzBlattseite(pg, '2c', 2).items.filter(i => i.x === 70 && i.y === 220)[0].s, '30');
});

test('PDF-Zeichenkodierung G¸ltig wird im Gültigkeitsbeginn berücksichtigt', async () => {
  const pg = { w: 420, h: 595, items: [item('Ersatzfahrplan 4000', 30, 200), item('G¸ltig ab 14. Dezember 2025', 30, 500)] };
  const head = await BF.heftZugriff(util, fakeDoc([pg])).kopf();
  assert.equal(head.nr, '4000');
  assert.equal(head.gueltigAb, 20251214);
});

test('Seitenzugriff nutzt echte Fußzeilen auch bei großem Versatz', async () => {
  const blank = { w: 420, h: 595, items: [] };
  const pages = Array.from({ length: 31 }, () => blank);
  pages[2] = { w: 420, h: 595, items: [item('Seite 1', 176, 200)] };
  pages[30] = table({ nr: 1, splitFooter: true });
  const h = BF.heftZugriff(util, fakeDoc(pages));
  assert.equal((await h.seite(1)).pdfSeite, 31);
  assert.equal(await h.seite(99), null);
  assert.equal(await h.seite(-1), null);
});

function train(names) {
  return { zug: '99999', gattung: 'TEST', abschnitt: '1.1', laufweg: names.join(' - '), tageText: '', tfz: '', last: '', laenge: '', vmax: '', mbr: [], hefte: ['4000'], zeilen: names.map((name, i) => ({ name, an: '', ab: '12.' + String(i * 5).padStart(2, '0'), halt: '', bem: '', ref: i ? null : { heft: '4000', seite: 1, spalte: '2a' } })) };
}
test('vollständiger Aufbau übernimmt jede Fplo-Zeit', async () => {
  const h = BF.heftZugriff(util, fakeDoc([table()]));
  const r = await BF.bauePlanAusFplo({ name: 'Test' }, train(['Alpha', 'Beta', 'Gamma']), 20261117, async () => h);
  assert.equal(r.fehler, undefined);
  assert.equal(r.zeiten, 3);
});

test('fehlende Betriebsstelle liefert keinen scheinbar vollständigen Fahrplan', async () => {
  const h = BF.heftZugriff(util, fakeDoc([table()]));
  const r = await BF.bauePlanAusFplo({ name: 'Test' }, train(['Alpha', 'Nichtvorhanden', 'Gamma']), 20261117, async () => h);
  assert.match(r.fehler, /unvollständig.*Nichtvorhanden/);
  assert.equal(r.regel, undefined);
});

test('fehlendes Heft in einem späteren Abschnitt verhindert einen Teilfahrplan', async () => {
  const h = BF.heftZugriff(util, fakeDoc([table()]));
  const z = train(['Alpha', 'Beta', 'Gamma']);
  z.zeilen[2].ref = { heft: '4999', seite: 1, spalte: '2a' };
  const r = await BF.bauePlanAusFplo({ name: 'Test' }, z, 20261117, async nr => nr === '4000' ? h : null);
  assert.match(r.fehler, /4999/);
  assert.equal(r.regel, undefined);
});

test('Fplo-Zeilen vor dem ersten Heftverweis werden nicht still ausgelassen', async () => {
  const z = train(['Alpha', 'Beta', 'Gamma']);
  z.zeilen[1].ref = z.zeilen[0].ref; z.zeilen[0].ref = null;
  const r = await BF.bauePlanAusFplo({ name: 'Test' }, z, 20261117, async () => null);
  assert.match(r.fehler, /ersten 1 Betriebsstellen/);
  assert.equal(r.regel, undefined);
});

test('nicht unterstützte Buchfahrplan- und HGK-Formate erhalten einen konkreten Hinweis', () => {
  for (const t of ['Buchfahrplan 6353 SBB Cargo Grenzverkehr', 'Geschwindigkeitsheft HGK', 'Häfen und Güterverkehr Fahrzeitenheft', 'DGS 99999 GeH HGK Seite 42,37']) {
    assert.match(BF.pdfHinweis(t), /noch nicht unterstützt/);
    assert.match(BF.parse([{ items: [item(t, 30, 200)] }]).fehler, /noch nicht unterstützt/);
  }
  assert.equal(BF.pdfHinweis('Ersatzfahrplan 4000'), '');
  assert.equal(BF.pdfHinweis('Blattfahrplan 99999 1 2 3a 3b 4 5'), '');
});

test('Auswahl der Heftausgabe berücksichtigt Datum und unbekannte Gültigkeit', () => {
  const src = html.slice(html.indexOf('function heftFuer('), html.indexOf('function heftOeffnen('));
  const world = { dokMeta: [
    { art: 'heft', nr: '4000', gueltigAb: 20260614 },
    { art: 'heft', nr: '4000', gueltigAb: 20251214 },
    { art: 'heft', nr: '4000', gueltigAb: null }
  ] };
  vm.createContext(world); vm.runInContext(src, world);
  assert.equal(world.heftFuer('4000', 20260101).gueltigAb, 20251214);
  assert.equal(world.heftFuer('4000', 20261117).gueltigAb, 20260614);
  assert.equal(world.heftFuer('4000', 20250101), null);
});
