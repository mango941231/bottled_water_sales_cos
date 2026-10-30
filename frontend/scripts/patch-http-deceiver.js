#!/usr/bin/env node
/**
 * Patch `http-deceiver` so it works on Node.js >= 12.
 *
 * `http-deceiver@1.2.7` (pulled in transitively by `spdy`, which
 * `webpack-dev-server@2.x` requires at module load time) obtains the native
 * HTTP parser through `process.binding('http_parser')`. That binding was
 * removed from Node.js, so requiring the module throws:
 *
 *     Error: No such module: http_parser
 *
 * The internal `_http_common` module still exports the very same
 * `HTTPParser` constructor (including the `kOn*` slot constants) and the
 * list of supported HTTP methods, so we swap the binding lookup for it.
 *
 * The patch is applied in-place inside node_modules and is idempotent: it is
 * a no-op when the file is already patched or when the environment does not
 * need the fix.
 */

'use strict';

const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const NODE_MODULES = path.join(ROOT, 'node_modules');

const ORIGINAL = `  HTTPParser = process.binding('http_parser').HTTPParser;
  methods = HTTPParser.methods;

  // v6
  if (!methods)
    methods = process.binding('http_parser').methods;
`;

const PATCHED = `  // Node.js >= 12 removed the \`http_parser\` process binding.
  // The internal \`_http_common\` module still exposes the very same
  // \`HTTPParser\` constructor (including the \`kOn*\` slot constants) and
  // the list of supported HTTP methods, so use it when available.
  try {
    var httpCommon = require('_http_common');
    HTTPParser = httpCommon.HTTPParser;
    methods = HTTPParser.methods;

    // v6
    if (!methods)
      methods = httpCommon.methods;
  } catch (e) {
    var httpParserBinding = process.binding('http_parser');
    HTTPParser = httpParserBinding.HTTPParser;
    methods = HTTPParser.methods;

    // v6
    if (!methods)
      methods = httpParserBinding.methods;
  }
`;

/** Collect every `http-deceiver` package directory installed under node_modules. */
function findDeceiverDirs(dir, found) {
  let entries;
  try {
    entries = fs.readdirSync(dir, { withFileTypes: true });
  } catch (e) {
    return found;
  }

  for (const entry of entries) {
    if (!entry.isDirectory() && !entry.isSymbolicLink()) continue;
    const full = path.join(dir, entry.name);

    if (entry.name === 'http-deceiver') {
      found.push(full);
      // Also look for nested copies (npm may install several versions).
      findDeceiverDirs(path.join(full, 'node_modules'), found);
      continue;
    }

    if (entry.name === '.bin' || entry.name === '.cache') continue;

    findDeceiverDirs(path.join(full, 'node_modules'), found);
  }

  return found;
}

function patchFile(file) {
  let source;
  try {
    source = fs.readFileSync(file, 'utf8');
  } catch (e) {
    return false;
  }

  if (source.indexOf(PATCHED) !== -1) {
    console.log('[patch-http-deceiver] already patched: ' + file);
    return false;
  }

  if (source.indexOf(ORIGINAL) === -1) {
    console.log('[patch-http-deceiver] nothing to patch: ' + file);
    return false;
  }

  fs.writeFileSync(file, source.replace(ORIGINAL, PATCHED), 'utf8');
  console.log('[patch-http-deceiver] patched: ' + file);
  return true;
}

if (!fs.existsSync(NODE_MODULES)) {
  console.log('[patch-http-deceiver] node_modules not found, skipping');
  process.exit(0);
}

const dirs = findDeceiverDirs(NODE_MODULES, []);
let patched = 0;

for (const dir of dirs) {
  const file = path.join(dir, 'lib', 'deceiver.js');
  if (patchFile(file)) patched += 1;
}

if (patched === 0) {
  console.log('[patch-http-deceiver] no changes required');
} else {
  console.log('[patch-http-deceiver] done (' + patched + ' file(s) patched)');
}
