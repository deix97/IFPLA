'use strict';
// Lokale Vollprüfung. Die dienstlichen PDF und das Ergebnis bleiben außerhalb des Repos.
// node tests/efpl-bestand.cjs <PDF-Ordner> [Ergebnis.json]
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { BF, html } = require('./leser.cjs');
const pdfjs = require('../lib/pdf.min.js');
pdfjs.GlobalWorkerOptions.workerSrc = path.join(__dirname, '..', 'lib', 'pdf.worker.min.js');
const base = process.argv[2] && path.resolve(process.argv[2]);
if (!base) throw Error('PDF-Ordner angeben.');
const files = dir => fs.readdirSync(dir, { withFileTypes: true }).flatMap(d => d.isDirectory() ? (d.name === '_Alt' ? [] : files(path.join(dir, d.name))) : /\.pdf$/i.test(d.name) ? [path.join(dir, d.name)] : []);
const cols = ['2a', '2b', '2c', '2d'];

// Unabhängige Referenz aus den geometrisch zusammengehörenden Fußzeilenstücken.
function footer(pg) {
  const labels = pg.items.filter(i => /^Seite(?:\s*\d+)?$/.test(i.s.trim())).sort((a, b) => b.y - a.y);
  for (const label of labels) {
    const line = pg.items.filter(i => Math.abs(i.y - label.y) < 2).sort((a, b) => a.x - b.x);
    const left = line.filter(i => i.x < label.x).map(i => i.s).join('').replace(/\s/g, '').replace(/^Efpl/, '').replace(/Fpl\d{4}$/, '');
    const right = line.filter(i => i.x >= label.x).map(i => i.s).join('').replace(/\s/g, '');
    if (/^\d{4}$/.test(left) && /^Seite\d+$/.test(right)) return { nr: +right.slice(5), heft: left, y: label.y };
  }
  return null;
}
function fail(r, problem, detail) {
  r.errors[problem] = (r.errors[problem] || 0) + 1;
  const a = r.examples[problem] ||= [];
  if (a.length < 5) a.push(detail);
}
function namesIn(block) {
  return block.zeilen.filter(e => e.lines.length && !e.info && !e.nurKm && !e.nurV && !BF.RE_KEINNAME.test(BF.eintragName(e))).map(BF.eintragName);
}
function train(nr, sample, column) {
  return { zug: '99999', gattung: 'TEST', abschnitt: '1.1', laufweg: sample.names.join(' - '), tageText: '', tfz: '', last: '', laenge: '', vmax: '', mbr: [], hefte: [nr], zeilen: sample.names.map((name, i) => ({ name, an: '', ab: '12.' + String(i * 5).padStart(2, '0'), halt: '', bem: '', ref: i ? null : { heft: nr, seite: sample.nr, spalte: column } })) };
}
(async () => {
  const results = [];
  for (const file of files(base)) {
    const doc = await pdfjs.getDocument({ data: new Uint8Array(fs.readFileSync(file)), verbosity: 0 }).promise;
    try {
      const h = BF.heftZugriff(pdfjs, doc), head = await h.kopf();
      const first = await BF.leseText(pdfjs, await doc.getPage(1));
      const text = first.items.map(i => i.s).join(' '), hint = BF.pdfHinweis(text);
      const efpl = /Ersatzfahrplan\s+\d{4}/.test(text);
      const r = { file: path.relative(base, file), pages: doc.numPages, format: efpl ? 'Efpl' : hint ? 'Anderes Format mit Hinweis' : 'Unbekannt', head, tables: 0, columns: 0, builds: 0, errors: {}, examples: {} };
      let sample = null;
      if (efpl && (!head.nr || !head.gueltigAb)) fail(r, 'Heftkopf', head);
      if (!efpl && !hint) fail(r, 'FormatNichtErkannt', {});
      if (!efpl && hint && !BF.parse([first]).fehler?.includes('noch nicht unterstützt')) fail(r, 'FormatWirdFalschGelesen', {});
      for (let p = 1; p <= doc.numPages; p++) {
        const pg = p === 1 ? first : await BF.leseText(pdfjs, await doc.getPage(p));
        if (!efpl) continue;
        const ref = footer(pg);
        const marks = ['2a', '2b', '2c', '2d', '3a', '3b', '4a'].filter(s => pg.items.some(i => i.s.trim() === s)).length;
        const b = BF.ersatzBlattseite(pg, '2a', p);
        const lastHeader = pg.items.find(i => i.s.trim() === 'ab km');
        const hasBody = lastHeader && ref && pg.items.some(i => i.y > lastHeader.y + 4 && i.y < ref.y - 4);
        if (ref && hasBody && marks >= 5 && !b) fail(r, 'TabelleNichtErkannt', { pdf: p, printed: ref.nr });
        if (!b) continue;
        r.tables++;
        if (!ref || b.nr !== ref.nr || b.heft !== head.nr) fail(r, 'Seitenzuordnung', { pdf: p, reference: ref, read: b.nr, heft: b.heft });
        const got = await h.seite(b.nr);
        if (!got || got.pdfSeite !== p) fail(r, 'Seitenzugriff', { pdf: p, printed: b.nr, got: got?.pdfSeite });
        for (const c of cols) {
          const B = BF.ersatzBlattseite(pg, c, p), z = BF.zeilenAus(B);
          B.zeilen = z.zeilen; B.noten = z.fuss;
          const blocks = BF.leseBloecke([B], () => {}), rows = blocks.flatMap(b => b.zeilen);
          r.columns++;
          if (!rows.length) fail(r, 'LeereSpalte', { pdf: p, column: c });
          const invalid = rows.filter(e => e.v && (!Number.isFinite(e.v.v) || e.v.v < 5 || e.v.v > 300));
          if (invalid.length) fail(r, 'GeschwindigkeitUnplausibel', { pdf: p, column: c, values: invalid.map(e => e.v.v) });
          if (c === '2a' && !sample) {
            const names = blocks.map(namesIn).sort((a, b) => b.length - a.length)[0];
            if (names?.length >= 2) sample = { pdf: p, nr: b.nr, names: names.slice(0, 3) };
          }
        }
      }
      if (efpl) {
        const index = await h.streckenIndex();
        if (index.length !== r.tables) fail(r, 'Streckenindex', { tables: r.tables, index: index.length });
        if (!sample) fail(r, 'KeinAufbaubeispiel', {});
        else for (const c of cols) {
          const result = await BF.bauePlanAusFplo({ name: 'Lokaler Strukturtest' }, train(head.nr, sample, c), 20261117, async nr => nr === head.nr ? h : null);
          r.builds++;
          if (result.fehler || result.zeiten !== sample.names.length) fail(r, 'Fahrplanaufbau', { pdf: sample.pdf, column: c, error: result.fehler, expectedTimes: sample.names.length, actualTimes: result.zeiten });
        }
      }
      results.push(r);
      console.log(`${Object.keys(r.errors).length ? 'FEHLER' : 'OK'} ${r.file}: ${r.pages} Seiten, ${r.tables} Tabellen, ${r.builds} Aufbauten${Object.keys(r.errors).length ? ' ' + JSON.stringify(r.errors) : ''}`);
    } finally { await doc.destroy(); }
  }
  const totals = { files: results.length, pages: results.reduce((n, r) => n + r.pages, 0), efpl: results.filter(r => r.format === 'Efpl').length, otherFormats: results.filter(r => r.format === 'Anderes Format mit Hinweis').length, tables: results.reduce((n, r) => n + r.tables, 0), columns: results.reduce((n, r) => n + r.columns, 0), builds: results.reduce((n, r) => n + r.builds, 0), failedFiles: results.filter(r => Object.keys(r.errors).length).length };
  const report = { sourceSha256: crypto.createHash('sha256').update(html).digest('hex'), note: 'Vollständiger Seitenscan und synthetische Aufbauten; kein fachlicher Soll-Ist-Abgleich jeder Fahrplanzeile. Andere Formate werden klar gemeldet und nicht als Efpl oder einzelner Blattfahrplan gelesen.', totals, files: results };
  if (process.argv[3]) fs.writeFileSync(path.resolve(process.argv[3]), JSON.stringify(report, null, 2));
  console.log('GESAMT', JSON.stringify(totals));
  process.exitCode = totals.failedFiles ? 1 : 0;
})().catch(e => { console.error(e); process.exitCode = 1; });
