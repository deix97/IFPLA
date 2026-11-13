/* Quelle für die Automatik der Fahrplananzeige.
   Liefert die App und einen Ordner mit PDF zusammen aus einer Herkunft (same origin)
   und daraus das Verzeichnis /fahrplan.json, das die Anzeige abruft.

   Aufruf:  node werkzeug/quelle.mjs --ordner ./pdf [--port 8723] [--app ./index.html]
                 [--lib ./lib] [--cors]
   Dann im Browser http://127.0.0.1:8723/?quelle=/fahrplan.json&auto=1

   Liegen in --lib die Dateien pdf.min.js und pdf.worker.min.js (npm pack pdfjs-dist@3.11.174,
   aus package/build/), liefert die Quelle sie unter /lib/ aus; mit &pdfjs=/lib/ in der Adresse
   liest die Anzeige PDF dann ohne Internet.

   Einteilung der PDF im Ordner, nach Name:
     beginnt mit "la"  -> Tages-La; je La-Bereich im Namen (Nord, Ost, Mitte, Suedost, Sued)
                          gilt die jüngste Datei, ohne erkennbaren Bereich alle vom jüngsten Tag
     sonst             -> Blattfahrplan; es gilt die jüngste Datei
   Wer es anders braucht, legt neben den Ordner eine eigene fahrplan.json und zeigt mit
   --verzeichnis darauf; dann wird nur sie ausgeliefert. */

import { createServer } from 'node:http';
import { readFile, readdir, stat } from 'node:fs/promises';
import { createReadStream } from 'node:fs';
import { resolve, join, basename, extname } from 'node:path';
import { fileURLToPath } from 'node:url';

const arg = (name, fallback) => {
  const i = process.argv.indexOf('--' + name);
  return i >= 0 && process.argv[i + 1] && !process.argv[i + 1].startsWith('--') ? process.argv[i + 1] : fallback;
};
const hat = (name) => process.argv.includes('--' + name);

const hier = resolve(fileURLToPath(import.meta.url), '..');
const ordner = resolve(arg('ordner', join(hier, '..', 'pdf')));
const appDatei = resolve(arg('app', join(hier, '..', 'index.html')));
const eigenesVerzeichnis = arg('verzeichnis', '');
const libOrdner = resolve(arg('lib', join(hier, '..', 'lib')));
const appOrdner = resolve(appDatei, '..');
const port = Number(arg('port', '8723'));
const cors = hat('cors');

const BEREICHE = ['Nord', 'Ost', 'Mitte', 'Suedost', 'Sued'];
const bereichVon = (name) => {
  const n = name.toLowerCase().replace(/ü/g, 'ue').replace(/ö/g, 'oe').replace(/ä/g, 'ae');
  if (/(^|[\W_])s(ue|u)dost([\W_]|$)/.test(n)) return 'Suedost';
  for (const b of BEREICHE) {
    if (b === 'Suedost') continue;
    if (new RegExp('(^|[\\W_])' + b.toLowerCase() + '([\\W_]|$)').test(n)) return b;
  }
  return '';
};

async function verzeichnis() {
  if (eigenesVerzeichnis) return JSON.parse(await readFile(resolve(eigenesVerzeichnis), 'utf8'));
  let namen = [];
  try { namen = await readdir(ordner); } catch { throw new Error('Ordner nicht lesbar: ' + ordner); }
  const pdfs = [];
  for (const n of namen) {
    if (extname(n).toLowerCase() !== '.pdf') continue;
    try {
      const st = await stat(join(ordner, n));
      if (st.isFile() && st.size > 0) pdfs.push({ name: n, ms: st.mtimeMs });
    } catch {}
  }
  pdfs.sort((a, b) => b.ms - a.ms);
  const laAlle = pdfs.filter((d) => /^la/i.test(d.name));
  const plaene = pdfs.filter((d) => !/^la/i.test(d.name));
  const la = [];
  const gesehen = new Set();
  let ohneBereichTag = null;
  for (const d of laAlle) {
    const b = bereichVon(d.name);
    if (b) { if (!gesehen.has(b)) { gesehen.add(b); la.push(d); } continue; }
    const tag = new Date(d.ms).toISOString().slice(0, 10);
    if (ohneBereichTag === null) ohneBereichTag = tag;
    if (tag === ohneBereichTag) la.push(d);
  }
  const plan = plaene[0] || null;
  const benutzt = [plan, ...la].filter(Boolean);
  return {
    stand: benutzt.length ? new Date(Math.max(...benutzt.map((d) => d.ms))).toISOString() : '',
    plan: plan ? 'pdf/' + encodeURIComponent(plan.name) : '',
    la: la.map((d) => 'pdf/' + encodeURIComponent(d.name)),
    ordner
  };
}

const kopf = (typ) => {
  const h = { 'content-type': typ, 'cache-control': 'no-store' };
  if (cors) h['access-control-allow-origin'] = '*';
  return h;
};

const server = createServer(async (anfrage, antwort) => {
  let pfad;
  try { pfad = decodeURIComponent(new URL(anfrage.url, 'http://127.0.0.1').pathname); } catch { pfad = '/'; }
  try {
    if (pfad === '/' || pfad === '/index.html') {
      antwort.writeHead(200, kopf('text/html; charset=utf-8'));
      antwort.end(await readFile(appDatei));
      return;
    }
    if (pfad === '/fahrplan.json') {
      const v = await verzeichnis();
      console.log(new Date().toLocaleTimeString('de-DE') + '  Verzeichnis: ' + (v.plan ? basename(decodeURIComponent(v.plan)) : 'kein Fahrplan') + (v.la.length ? ', La ' + v.la.length : ''));
      antwort.writeHead(200, kopf('application/json; charset=utf-8'));
      antwort.end(JSON.stringify(v, null, 1));
      return;
    }
    if (pfad === '/manifest.webmanifest' || pfad === '/sw.js' || /^\/symbol\/[\w.-]+\.(png|svg)$/.test(pfad)) {
      const datei = join(appOrdner, pfad.slice(1));
      const st = await stat(datei);
      const typ = pfad.endsWith('.json') || pfad.endsWith('.webmanifest') ? 'application/manifest+json; charset=utf-8'
        : pfad.endsWith('.js') ? 'text/javascript; charset=utf-8'
        : pfad.endsWith('.svg') ? 'image/svg+xml' : 'image/png';
      /* der Dienstarbeiter muss ueber die ganze Herkunft gehen duerfen */
      const zusatz = pfad === '/sw.js' ? { 'service-worker-allowed': '/' } : {};
      antwort.writeHead(200, { ...kopf(typ), ...zusatz, 'content-length': String(st.size) });
      createReadStream(datei).pipe(antwort);
      return;
    }
    if (pfad.startsWith('/lib/')) {
      const name = basename(pfad.slice(5));
      if (!/^[\w.-]+\.js$/.test(name)) { antwort.writeHead(404, kopf('text/plain')); antwort.end('nicht da'); return; }
      const datei = join(libOrdner, name);
      const st = await stat(datei);
      antwort.writeHead(200, { ...kopf('text/javascript; charset=utf-8'), 'content-length': String(st.size) });
      createReadStream(datei).pipe(antwort);
      return;
    }
    if (pfad.startsWith('/pdf/')) {
      const name = basename(pfad.slice(5));
      if (!name || extname(name).toLowerCase() !== '.pdf') { antwort.writeHead(404, kopf('text/plain')); antwort.end('nicht da'); return; }
      const datei = join(ordner, name);
      const st = await stat(datei);
      antwort.writeHead(200, { ...kopf('application/pdf'), 'content-length': String(st.size) });
      createReadStream(datei).pipe(antwort);
      return;
    }
    antwort.writeHead(404, kopf('text/plain; charset=utf-8'));
    antwort.end('nicht da');
  } catch (e) {
    antwort.writeHead(500, kopf('text/plain; charset=utf-8'));
    antwort.end('Fehler: ' + (e && e.message ? e.message : e));
  }
});

let eigenesPdfJs = false;
try { await stat(join(libOrdner, 'pdf.min.js')); await stat(join(libOrdner, 'pdf.worker.min.js')); eigenesPdfJs = true; } catch {}

server.listen(port, '127.0.0.1', () => {
  console.log('Quelle läuft.  Ordner: ' + ordner);
  console.log('pdf.js:         ' + (eigenesPdfJs ? 'eigene Ablage ' + libOrdner : 'cdnjs, also mit Internet; sonst --lib füllen'));
  console.log('Anzeige:        http://127.0.0.1:' + port + '/?quelle=/fahrplan.json&auto=1' + (eigenesPdfJs ? '&pdfjs=/lib/' : ''));
  console.log('Verzeichnis:    http://127.0.0.1:' + port + '/fahrplan.json');
});
