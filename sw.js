/* Dienstarbeiter der Fahrplananzeige.
   Zwei Aufgaben:
   1. Die Anzeige selbst (und pdf.js, wenn es daneben liegt) offline bereithalten,
      damit sie im Führerraum ohne Netz startet und PDF lesen kann.
   2. Eine oder mehrere PDF entgegennehmen, die von außen in die Anzeige geteilt
      werden (share_target im Manifest). Sie werden kurz abgelegt, die Seite holt
      sie sich beim Start und löscht sie sofort wieder.
   Fahrplan-PDF werden nie dauerhaft zwischengespeichert. */

var FASSUNG = 'fahrplan-schale-4';
var UEBERGABE = 'fahrplan-uebergabe';
var SCHALE = ['./', './index.html', './manifest.webmanifest', './symbol/192.png', './symbol/512.png',
              './lib/pdf.min.js', './lib/pdf.worker.min.js'];

self.addEventListener('install', function (ev) {
  ev.waitUntil(caches.open(FASSUNG).then(function (c) {
    /* einzeln, damit eine fehlende Datei (z. B. kein eigenes pdf.js) nichts verhindert */
    return Promise.all(SCHALE.map(function (pfad) {
      return c.add(new Request(pfad, { cache: 'reload' })).catch(function () {});
    }));
  }).then(function () { return self.skipWaiting(); }));
});

self.addEventListener('activate', function (ev) {
  ev.waitUntil(caches.keys().then(function (namen) {
    return Promise.all(namen.map(function (n) {
      if (n !== FASSUNG && n !== UEBERGABE) return caches.delete(n);
      if (n === UEBERGABE) return caches.delete(n); /* Reste einer Übergabe nie liegen lassen */
    }));
  }).then(function () { return self.clients.claim(); }));
});

function istSchale(url) {
  var p = url.pathname;
  return p === new URL('./', self.registration.scope).pathname || /\/(index\.html|manifest\.webmanifest)$/.test(p) ||
    /\/(lib|symbol)\/[\w.-]+$/.test(p);
}

self.addEventListener('fetch', function (ev) {
  var anfrage = ev.request, url;
  try { url = new URL(anfrage.url); } catch (e) { return; }

  /* geteilte PDF entgegennehmen */
  if (anfrage.method === 'POST' && /\/geteilt$/.test(url.pathname)) {
    ev.respondWith((function () {
      return anfrage.formData().then(function (form) {
        var dateien = form.getAll('datei').concat(form.getAll('file')).filter(function (d) { return d && d.size; });
        if (!dateien.length) return null;
        return caches.open(UEBERGABE).then(function (c) {
          return Promise.all(dateien.map(function (datei, i) {
            return c.put(new Request(new URL('uebergabe/' + i, self.registration.scope).href),
              new Response(datei, { headers: {
                'content-type': 'application/pdf',
                'x-datei-name': encodeURIComponent(datei.name || 'geteilt.pdf')
              } }));
          }));
        });
      }).catch(function () { return null; }).then(function () {
        return Response.redirect(new URL('./?geteilt=1', self.registration.scope).href, 303);
      });
    })());
    return;
  }

  if (anfrage.method !== 'GET' || url.origin !== self.location.origin) return;

  /* Tagesdaten nie aus dem Cache und nie hinein */
  if (/\/uebergabe(\/\d+)?$/.test(url.pathname)) return;

  if (anfrage.mode === 'navigate') {
    /* aus dem Cache anzeigen, im Hintergrund erneuern: startet auch ohne Netz */
    ev.respondWith(caches.open(FASSUNG).then(function (c) {
      return c.match(new URL('./', self.registration.scope).href).then(function (treffer) {
        var netz = fetch(anfrage).then(function (antwort) {
          if (antwort && antwort.ok) c.put(new URL('./', self.registration.scope).href, antwort.clone());
          return antwort;
        }).catch(function () { return null; });
        return treffer || netz.then(function (a) { return a || new Response('Keine Verbindung und nichts abgelegt.', { status: 503, headers: { 'content-type': 'text/plain; charset=utf-8' } }); });
      });
    }));
    return;
  }

  if (istSchale(url)) {
    ev.respondWith(caches.open(FASSUNG).then(function (c) {
      return c.match(anfrage).then(function (treffer) {
        if (treffer) return treffer;
        return fetch(anfrage).then(function (antwort) {
          if (antwort && antwort.ok) c.put(anfrage, antwort.clone());
          return antwort;
        });
      });
    }));
  }
});
