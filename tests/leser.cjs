'use strict';
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

// Tests laden dieselbe Quelle wie die App. Es werden keine dienstlichen PDF mitgeliefert.
const html = fs.readFileSync(path.join(__dirname, '..', 'index.html'), 'utf8');
const script = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)]
  .find(m => /root\.Blattfahrplan\s*=\s*api/.test(m[1]))[1];
const world = { module: { exports: {} }, console, Uint8Array, setTimeout, clearTimeout };
vm.createContext(world);
vm.runInContext(script.replace('var api = {', 'var api = { leseBloecke, RE_KEINNAME, RE_INFO,'), world);

// Tages-La-Leser, zusätzlich mit inneren Schritten für gezielte Tests
const laScript = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)]
  .find(m => /root\.TagesLa\s*=\s*api/.test(m[1]))[1];
const laWorld = { module: { exports: {} }, console, Uint8Array, setTimeout, clearTimeout };
vm.createContext(laWorld);
vm.runInContext(laScript.replace('var api = {', 'var api = { datumZeit, fenster, bereite, leseLegende,'), laWorld);
module.exports = { BF: world.module.exports, TL: laWorld.module.exports, html };
