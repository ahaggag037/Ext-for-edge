(() => {
  'use strict';

  let lastTitle = '';
  let lastUrl = '';
  let timer = 0;

  function send() {
    timer = 0;
    if (typeof browser === 'undefined' || !browser.runtime?.sendNativeMessage) return;
    const title = (document.title || '').trim();
    const url = location.href;
    if (!url.startsWith('http://') && !url.startsWith('https://')) return;
    if (title === lastTitle && url === lastUrl) return;
    lastTitle = title;
    lastUrl = url;
    Promise.resolve(browser.runtime.sendNativeMessage('edgeflow.browser', {
      type: 'page-meta',
      title,
      url
    })).catch(() => {});
  }

  function schedule(delay = 120) {
    clearTimeout(timer);
    timer = setTimeout(send, delay);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => schedule(50), { once: true });
  } else schedule(50);

  window.addEventListener('pageshow', () => schedule(50), { passive: true });
  window.addEventListener('popstate', () => schedule(80), { passive: true });

  const title = document.querySelector('title');
  if (title && 'MutationObserver' in window) {
    new MutationObserver(() => schedule(100)).observe(title, { childList: true, subtree: true, characterData: true });
  }

  // SPAs often update title and URL after navigation; low-frequency check avoids history monkey-patching.
  setInterval(() => {
    if (document.hidden) return;
    if (document.title !== lastTitle || location.href !== lastUrl) send();
  }, 4000);
})();
