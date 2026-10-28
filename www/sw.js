const CACHE='masjidph-v2';
const FILES=['./','./index.html','./manifest.json'];

self.addEventListener('install', event => {
  event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(FILES)));
  self.skipWaiting();
});

self.addEventListener('activate', event => {
  event.waitUntil(caches.keys().then(keys => Promise.all(
    keys.filter(key => key.startsWith('masjidph-') && key !== CACHE)
      .map(key => caches.delete(key))
  )));
  self.clients.claim();
});

self.addEventListener('fetch', event => {
  const request = event.request;
  if (request.method !== 'GET') return;
  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;

  if (request.mode === 'navigate') {
    event.respondWith(fetch(request)
      .then(response => response.ok ? response : caches.match('./index.html'))
      .catch(() => caches.match('./index.html')));
    return;
  }
  event.respondWith(caches.match(request).then(response => response || fetch(request)));
});
