const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { spawnSync } = require('node:child_process');

const root = path.resolve(__dirname, '..');
const htmlPath = path.join(root, 'www', 'index.html');
const html = fs.readFileSync(htmlPath, 'utf8');
const sw = fs.readFileSync(path.join(root, 'www', 'sw.js'), 'utf8');

function requireMatch(regex, description, input = html) {
  if (!regex.test(input)) throw new Error('Missing web security requirement: ' + description);
}
function forbidMatch(regex, description, input = html) {
  if (regex.test(input)) throw new Error('Disallowed web pattern found: ' + description);
}

requireMatch(/<html\b[^>]*>/i, 'complete HTML document start');
requireMatch(/<\/html>/i, 'complete HTML document end');
requireMatch(/leaflet@1\.9\.4\/dist\/leaflet\.css[^>]*integrity="sha256-p4NxAoJBhIIN\+hmNHrzRCf9tD\/miZyoHS5obTRR9BMY="[^>]*crossorigin="anonymous"/, 'Leaflet CSS SRI');
requireMatch(/leaflet@1\.9\.4\/dist\/leaflet\.js[^>]*integrity="sha256-20nQCchB9co0qIjJZRGuk2\/Z9VM\+kNiyxNV1lvTlZBo="[^>]*crossorigin="anonymous"/, 'Leaflet JavaScript SRI');
requireMatch(/id="authVerificationActions"/i, 'email verification actions markup');
requireMatch(/sendEmailVerification/, 'Firebase email verification support');
requireMatch(/getIdToken\([^)]*,\s*true\)/, 'refresh Firebase ID token after verification');
requireMatch(/timeZone:\s*PRAYER_TIME_ZONE/, 'explicit Manila timezone use');
requireMatch(/PRAYER_TIME_ZONE\s*=\s*['"]Asia\/Manila['"]/, 'Asia/Manila timezone constant');
requireMatch(/noopener,noreferrer/, 'noopener/noreferrer for external map window');
requireMatch(/escapeHtml/, 'escaping of directory values inserted into HTML');
requireMatch(/request\.mode\s*===\s*['"]navigate['"]/, 'navigation-only offline fallback', sw);
requireMatch(/url\.origin\s*!==\s*self\.location\.origin/, 'same-origin service worker filtering', sw);
requireMatch(/masjidph-v2/, 'service-worker cache version bump', sw);
forbidMatch(/\beval\s*\(|new\s+Function\s*\(|document\.write\s*\(/, 'dynamic code execution');

const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'masjidph-pwa-check-'));
try {
  const scriptPattern = /<script\b([^>]*)>([\s\S]*?)<\/script>/gi;
  let match;
  let index = 0;
  while ((match = scriptPattern.exec(html)) !== null) {
    const attrs = match[1] || '';
    const source = match[2] || '';
    if (/\bsrc\s*=/.test(attrs) || !source.trim()) continue;
    const isModule = /\btype\s*=\s*["']module["']/i.test(attrs);
    const file = path.join(tempDir, 'inline-' + (++index) + (isModule ? '.mjs' : '.js'));
    fs.writeFileSync(file, source);
    const result = spawnSync(process.execPath, ['--check', file], {encoding: 'utf8'});
    if (result.status !== 0) {
      throw new Error('Inline script syntax check failed for script ' + index + ': ' + (result.stderr || result.stdout));
    }
  }
} finally {
  fs.rmSync(tempDir, {recursive: true, force: true});
}

console.log('Static web/PWA security checks passed.');
