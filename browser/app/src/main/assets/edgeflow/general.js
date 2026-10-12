(() => {
  'use strict';

  const S = {
    pressure: 0,
    aggressive: false,
    hydrated: false,
    mutationTimer: 0,
    mediaObserver: null,
    trackedMedia: new WeakSet(),
    resumedMedia: new WeakSet(),
    lastPolicyAt: 0
  };

  const STYLE_ID = 'edgeflow-general-style';

  function addStyle() {
    if (document.getElementById(STYLE_ID)) return;
    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
      html.ef-general-pressure * { scroll-behavior:auto !important; }
      html.ef-general-pressure *,
      html.ef-general-pressure *::before,
      html.ef-general-pressure *::after {
        animation-duration:.001ms !important;
        animation-iteration-count:1 !important;
        transition-duration:.001ms !important;
      }
      html.ef-general-critical [style*="backdrop-filter"],
      html.ef-general-critical [class*="backdrop"] {
        backdrop-filter:none !important;
      }
    `;
    (document.head || document.documentElement).appendChild(style);
  }

  function applyPolicy(policy) {
    if (!policy || typeof policy !== 'object') return;
    S.pressure = Number(policy.pressure) || 0;
    S.aggressive = !!policy.aggressive;
    const root = document.documentElement;
    if (!root) return;
    root.classList.toggle('ef-general-pressure', S.pressure >= 2 || S.aggressive);
    root.classList.toggle('ef-general-critical', S.pressure >= 3);
    if (S.pressure >= 2) optimizeMedia(document);
  }

  function askNative(force = false) {
    if (typeof browser === 'undefined' || !browser.runtime?.sendNativeMessage) return;
    const now = performance.now();
    const min = document.hidden ? 30000 : 9000;
    if (!force && now - S.lastPolicyAt < min) return;
    S.lastPolicyAt = now;
    Promise.resolve(browser.runtime.sendNativeMessage('edgeflow.browser', {
      type: 'page-health',
      hidden: document.hidden,
      nodeCount: document.getElementsByTagName('*').length
    })).then(applyPolicy).catch(() => {});
  }

  function shouldLazy(img) {
    try {
      const r = img.getBoundingClientRect();
      return r.top > innerHeight * 1.25 || r.bottom < -innerHeight * 0.5;
    } catch (_) { return true; }
  }

  function optimizeImages(root) {
    root.querySelectorAll?.('img').forEach(img => {
      if (!img.hasAttribute('decoding')) img.decoding = 'async';
      if (!img.hasAttribute('loading') && shouldLazy(img)) img.loading = 'lazy';
      if (S.pressure >= 2 && !img.hasAttribute('fetchpriority') && shouldLazy(img)) {
        img.setAttribute('fetchpriority', 'low');
      }
    });
  }

  function ensureMediaObserver() {
    if (S.mediaObserver || !('IntersectionObserver' in window)) return;
    S.mediaObserver = new IntersectionObserver(entries => {
      for (const entry of entries) {
        const media = entry.target;
        if (!(media instanceof HTMLMediaElement)) continue;
        if (!entry.isIntersecting && S.pressure >= 2 && !media.paused && (media.muted || media.autoplay)) {
          try {
            media.pause();
            S.resumedMedia.add(media);
          } catch (_) {}
        } else if (entry.isIntersecting && S.resumedMedia.has(media) && document.visibilityState === 'visible') {
          S.resumedMedia.delete(media);
          Promise.resolve(media.play()).catch(() => {});
        }
      }
    }, { rootMargin: '100% 0px 100% 0px', threshold: 0.01 });
  }

  function optimizeMedia(root) {
    optimizeImages(root);
    ensureMediaObserver();
    root.querySelectorAll?.('video, audio').forEach(media => {
      if (S.trackedMedia.has(media)) return;
      S.trackedMedia.add(media);
      if (media.preload === 'auto' && !media.autoplay) media.preload = 'metadata';
      S.mediaObserver?.observe(media);
    });
  }

  function hydrate() {
    if (S.hydrated) return;
    S.hydrated = true;
    addStyle();
    optimizeMedia(document);
    const root = document.body || document.documentElement;
    if (root && 'MutationObserver' in window) {
      const observer = new MutationObserver(records => {
        if (S.mutationTimer) return;
        S.mutationTimer = setTimeout(() => {
          S.mutationTimer = 0;
          for (const record of records) {
            for (const node of record.addedNodes || []) {
              if (node?.nodeType === Node.ELEMENT_NODE) optimizeMedia(node);
            }
          }
        }, 180);
      });
      observer.observe(root, { childList: true, subtree: true });
      setTimeout(() => observer.disconnect(), 15000);
    }
    askNative(true);
  }

  document.addEventListener('visibilitychange', () => {
    if (document.hidden) {
      document.querySelectorAll('video, audio').forEach(media => {
        if (!media.paused && (media.muted || media.autoplay)) {
          try { media.pause(); S.resumedMedia.add(media); } catch (_) {}
        }
      });
    } else {
      askNative(true);
    }
  }, { passive: true });

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', hydrate, { once: true });
  } else hydrate();

  setInterval(() => askNative(false), 10000);
})();
