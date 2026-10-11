(() => {
  'use strict';

  // ChatGPT has its own specialized optimizer. Running both engines on the same
  // page would duplicate DOM observers and native-policy polling on the hottest
  // workload we care about, so the global engine deliberately gets out of the way.
  const host = location.hostname.toLowerCase();
  if (host === 'chatgpt.com' || host.endsWith('.chatgpt.com')
      || host === 'chat.openai.com' || host.endsWith('.openai.com')) return;

  const STATE = {
    pressure: 0,
    aggressive: false,
    dataSaver: false,
    powerSave: false,
    observer: null,
    observerActive: false,
    mediaObserver: null,
    policyTimer: 0,
    relaxTimer: 0,
    lastPolicyAt: 0,
    touched: new WeakSet()
  };

  function ensureStyle() {
    if (document.getElementById('edgeflow-global-style')) return;
    const style = document.createElement('style');
    style.id = 'edgeflow-global-style';
    style.textContent = `
      html.ef-global-watch { scroll-behavior: auto !important; }
      html.ef-global-high *,
      html.ef-global-high *::before,
      html.ef-global-high *::after {
        animation-duration: 0.001ms !important;
        animation-iteration-count: 1 !important;
        transition-duration: 0.001ms !important;
      }
      html.ef-global-critical [style*="backdrop-filter"],
      html.ef-global-critical [class*="backdrop"] {
        backdrop-filter: none !important;
      }
    `;
    (document.head || document.documentElement).appendChild(style);
  }

  function applyClasses() {
    const root = document.documentElement;
    if (!root) return;
    root.classList.toggle('ef-global-watch', STATE.pressure >= 1 || STATE.powerSave);
    root.classList.toggle('ef-global-high', STATE.pressure >= 2);
    root.classList.toggle('ef-global-critical', STATE.pressure >= 3);
  }

  function tuneImage(img) {
    if (!(img instanceof HTMLImageElement) || STATE.touched.has(img)) return;
    STATE.touched.add(img);
    try {
      img.decoding = 'async';
      if (!img.loading) img.loading = 'lazy';
      if ('fetchPriority' in img && !img.fetchPriority) img.fetchPriority = 'auto';
    } catch (_) {}
  }

  function tuneFrame(frame) {
    if (!(frame instanceof HTMLIFrameElement) || STATE.touched.has(frame)) return;
    STATE.touched.add(frame);
    try {
      if (!frame.loading) frame.loading = 'lazy';
    } catch (_) {}
  }

  function retuneMedia(media) {
    try {
      if ((STATE.dataSaver || STATE.pressure >= 3) && media.paused) media.preload = 'none';
      else if ((STATE.dataSaver || STATE.pressure >= 1) && media.preload === 'auto') media.preload = 'metadata';
    } catch (_) {}
  }

  function tuneMedia(media) {
    if (!(media instanceof HTMLMediaElement) || STATE.touched.has(media)) return;
    STATE.touched.add(media);
    retuneMedia(media);
    STATE.mediaObserver?.observe(media);
  }

  function tuneNode(node) {
    if (!node || node.nodeType !== Node.ELEMENT_NODE) return;
    const el = node;
    if (el.matches?.('img')) tuneImage(el);
    else if (el.matches?.('iframe')) tuneFrame(el);
    else if (el.matches?.('video,audio')) tuneMedia(el);
    el.querySelectorAll?.('img').forEach(tuneImage);
    el.querySelectorAll?.('iframe').forEach(tuneFrame);
    el.querySelectorAll?.('video,audio').forEach(tuneMedia);
  }

  function retuneKnownMedia() {
    document.querySelectorAll('video,audio').forEach(retuneMedia);
  }

  function ensureMediaObserver() {
    if (STATE.mediaObserver || !('IntersectionObserver' in window)) return;
    STATE.mediaObserver = new IntersectionObserver(entries => {
      if (!STATE.dataSaver && STATE.pressure < 2) return;
      for (const entry of entries) {
        const media = entry.target;
        if (!(media instanceof HTMLMediaElement)) continue;
        // Never stop audible playback. Only suppress muted, controls-free autoplay.
        if (!entry.isIntersecting && media.muted && !media.paused && !media.controls) {
          try { media.pause(); } catch (_) {}
        }
      }
    }, { rootMargin: '200% 0px 200% 0px', threshold: 0.01 });
  }

  function startDomObserver(burstMs = 18000) {
    if (document.hidden || STATE.observerActive) return;
    if (!STATE.observer) {
      STATE.observer = new MutationObserver(records => {
        for (const record of records) {
          for (const node of record.addedNodes || []) tuneNode(node);
        }
      });
    }
    STATE.observer.observe(document.documentElement, { childList: true, subtree: true });
    STATE.observerActive = true;

    clearTimeout(STATE.relaxTimer);
    STATE.relaxTimer = setTimeout(() => {
      // Once hydration settles, a healthy page should not pay a permanent observer
      // tax. Keep observing only while pressure/data-saver policy makes it worthwhile.
      if (!STATE.dataSaver && STATE.pressure === 0 && !STATE.powerSave) stopDomObserver();
    }, burstMs);
  }

  function stopDomObserver() {
    if (!STATE.observerActive || !STATE.observer) return;
    STATE.observer.disconnect();
    STATE.observerActive = false;
  }

  function reconcileObserverPolicy() {
    if (document.hidden) {
      stopDomObserver();
      return;
    }
    if (STATE.dataSaver || STATE.pressure >= 1 || STATE.powerSave) {
      startDomObserver(30000);
    }
  }

  function applyPolicy(policy) {
    if (!policy || typeof policy !== 'object') return;
    const next = Number(policy.pressure);
    STATE.pressure = Number.isFinite(next) ? Math.max(0, Math.min(3, next)) : 0;
    STATE.aggressive = !!policy.aggressive;
    STATE.dataSaver = !!policy.dataSaver;
    STATE.powerSave = !!policy.powerSave;
    applyClasses();
    retuneKnownMedia();
    reconcileObserverPolicy();
  }

  async function refreshPolicy(force = false) {
    const now = performance.now();
    if (!force && now - STATE.lastPolicyAt < 4000) return;
    STATE.lastPolicyAt = now;
    if (typeof browser === 'undefined' || !browser.runtime?.sendNativeMessage) return;
    try {
      const policy = await browser.runtime.sendNativeMessage('edgeflow.browser', {
        type: 'page_policy',
        hidden: document.hidden,
        url: location.href
      });
      applyPolicy(policy);
    } catch (_) {}
  }

  function schedulePolicyLoop() {
    clearTimeout(STATE.policyTimer);
    const delay = document.hidden ? 30000 : 8000;
    STATE.policyTimer = setTimeout(async () => {
      await refreshPolicy();
      schedulePolicyLoop();
    }, delay);
  }

  function start() {
    ensureStyle();
    ensureMediaObserver();
    tuneNode(document.documentElement);
    startDomObserver();
    refreshPolicy(true);
    schedulePolicyLoop();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', start, { once: true });
  } else {
    start();
  }

  document.addEventListener('visibilitychange', () => {
    if (document.hidden) stopDomObserver();
    else {
      tuneNode(document.documentElement);
      startDomObserver(10000);
    }
    refreshPolicy(true);
    schedulePolicyLoop();
  }, { passive: true });

  window.addEventListener('pageshow', () => {
    tuneNode(document.documentElement);
    startDomObserver(10000);
    refreshPolicy(true);
  }, { passive: true });
})();
