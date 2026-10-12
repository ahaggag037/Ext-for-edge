(() => {
  'use strict';

  const S = {
    pressure: 0,
    pain: 0,
    observer: null,
    timer: 0,
    loop: 0,
    lastTick: performance.now(),
    candidates: []
  };

  const ITEM_SELECTOR = [
    'article',
    '[role="article"]',
    '[data-message-author-role]',
    '[data-testid*="message"]',
    '[class*="message"]'
  ].join(',');

  function addStyle() {
    const style = document.createElement('style');
    style.textContent = `
      html.ef-ai-web * { scroll-behavior:auto !important; }
      html.ef-ai-web .ef-ai-cold {
        content-visibility:auto !important;
        contain-intrinsic-size:auto 640px !important;
        contain:layout paint style !important;
      }
      html.ef-ai-web.ef-ai-pressure *,
      html.ef-ai-web.ef-ai-pressure *::before,
      html.ef-ai-web.ef-ai-pressure *::after {
        animation-duration:.001ms !important;
        animation-iteration-count:1 !important;
        transition-duration:.001ms !important;
      }
    `;
    (document.head || document.documentElement).appendChild(style);
    document.documentElement.classList.add('ef-ai-web');
  }

  function normalize() {
    const root = document.querySelector('main') || document.body || document.documentElement;
    if (!root) return;
    const found = Array.from(root.querySelectorAll(ITEM_SELECTOR))
      .filter(node => node && node.isConnected && node.getBoundingClientRect().height > 48);

    // De-duplicate nested matches. We want coarse conversation units, not every inner wrapper.
    const coarse = [];
    for (const node of found) {
      if (coarse.some(parent => parent.contains(node))) continue;
      while (coarse.length && node.contains(coarse[coarse.length - 1])) coarse.pop();
      coarse.push(node);
    }
    S.candidates = coarse;

    const keep = S.pressure >= 3 ? 2 : S.pressure >= 2 ? 3 : S.pressure >= 1 ? 5 : 7;
    const cutoff = Math.max(0, coarse.length - keep);
    for (let i = 0; i < coarse.length; i++) {
      coarse[i].classList.toggle('ef-ai-cold', i < cutoff);
    }
  }

  function schedule() {
    if (S.timer) return;
    const run = () => { S.timer = 0; normalize(); };
    if ('requestIdleCallback' in window) S.timer = requestIdleCallback(run, { timeout: 500 });
    else S.timer = setTimeout(run, 120);
  }

  function askNative() {
    if (typeof browser === 'undefined' || !browser.runtime?.sendNativeMessage) return;
    Promise.resolve(browser.runtime.sendNativeMessage('edgeflow.browser', {
      type: 'ai-health',
      hidden: document.hidden,
      pain: Math.round(S.pain),
      units: S.candidates.length
    })).then(policy => {
      if (!policy || typeof policy !== 'object') return;
      const old = S.pressure;
      S.pressure = Number(policy.pressure) || 0;
      document.documentElement.classList.toggle('ef-ai-pressure', S.pressure >= 2 || S.pain >= 4);
      if (old !== S.pressure) schedule();
    }).catch(() => {});
  }

  function tick(expected) {
    S.loop = 0;
    const now = performance.now();
    const drift = document.hidden ? 0 : Math.max(0, now - S.lastTick - expected);
    S.lastTick = now;
    if (!document.hidden) {
      if (drift > 140) S.pain = Math.min(10, S.pain + 2);
      else if (drift > 65) S.pain = Math.min(10, S.pain + .75);
      else S.pain = Math.max(0, S.pain - .25);
      document.documentElement.classList.toggle('ef-ai-pressure', S.pressure >= 2 || S.pain >= 4);
    }
    askNative();
    const next = document.hidden ? 5000 : 1200;
    S.loop = setTimeout(() => tick(next), next);
  }

  function start() {
    addStyle();
    schedule();
    const root = document.querySelector('main') || document.body || document.documentElement;
    if (root && 'MutationObserver' in window) {
      S.observer = new MutationObserver(records => {
        let structural = false;
        for (const record of records) {
          if (record.addedNodes?.length || record.removedNodes?.length) { structural = true; break; }
        }
        if (structural) schedule();
      });
      S.observer.observe(root, { childList: true, subtree: true });
    }
    S.lastTick = performance.now();
    S.loop = setTimeout(() => tick(700), 700);
    askNative();
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start, { once: true });
  else start();

  window.addEventListener('pageshow', schedule, { passive: true });
  document.addEventListener('visibilitychange', () => {
    S.lastTick = performance.now();
    if (!document.hidden) { schedule(); askNative(); }
  }, { passive: true });
})();
