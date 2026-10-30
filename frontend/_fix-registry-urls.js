// One-off: rewrite retired taobao/npmmirror resolved URLs to registry.npmjs.org
const fs = require('fs');
const path = require('path');

const RETIRED = /^(?:https?:)?\/\/(?:registry\.npm\.taobao\.org|registry\.npmmirror\.com)\//;

function rewriteUrl(rawUrl) {
  if (!RETIRED.test(rawUrl)) return null;
  const [urlPart, hash] = rawUrl.split('#');
  const m = urlPart.match(/^(?:https?:)?\/\/[^/]+\/(.+)$/);
  if (!m) return null;
  const rest = m[1];
  const idx = rest.indexOf('/download/');
  if (idx === -1) return null;
  const pkg = rest.slice(0, idx);            // may be @scope/name
  const file = rest.slice(idx + '/download/'.length);
  // file is "<pkg>-<version>.tgz" where pkg includes scope
  if (!file.startsWith(pkg + '-') || !file.endsWith('.tgz')) return null;
  const version = file.slice(pkg.length + 1, -'.tgz'.length);
  const base = pkg.split('/').pop();
  const newUrl = `https://registry.npmjs.org/${pkg}/-/${base}-${version}.tgz`;
  return hash ? `${newUrl}#${hash}` : newUrl;
}

function processFile(file) {
  const p = path.resolve(file);
  let text = fs.readFileSync(p, 'utf8');
  let changed = 0;
  let failed = 0;
  const out = text.replace(/https?:\/\/(?:registry\.npm\.taobao\.org|registry\.npmmirror\.com)\/[^\s"']+/g, (u) => {
    const nu = rewriteUrl(u);
    if (nu) { changed++; return nu; }
    failed++;
    console.log('  UNMATCHED: ' + u);
    return u;
  });
  fs.writeFileSync(p, out);
  console.log(`${file}: rewrote ${changed}, unmatched ${failed}`);
}

for (const f of process.argv.slice(2)) {
  if (fs.existsSync(f)) processFile(f);
  else console.log(`${f}: not found`);
}
