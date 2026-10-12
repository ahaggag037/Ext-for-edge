(() => {
  'use strict';

  if (!window.visualViewport) return;

  const root = document.documentElement;
  let settleTimer = 0;
  let raf = 0;
  let baselineHeight = Math.max(window.innerHeight, visualViewport.height || 0);
  let lastHeight = visualViewport.height || baselineHeight;
  let lastWidth = visualViewport.width || window.innerWidth;

  const style = document.createElement('style');
  style.id = 'edgeflow-mobile-viewport-style';
  style.textContent = `
    html.ef-vv-moving *,
    html.ef-vv-moving *::before,
    html.ef-vv-moving *::after {
      scroll-behavior:auto !important;
      transition-duration:.001ms !important;
    }
    html.ef-vv-moving [style*="backdrop-filter"],
    html.ef-vv-moving [class*="backdrop"] {
      backdrop-filter:none !important;
    }
    html.ef-keyboard-open [data-testid*="sidebar"],
    html.ef-keyboard-open nav[aria-label*="Chat"] {
      will-change:auto !important;
    }
  `;
  (document.head || root).appendChild(style);

  function sample() {
    raf = 0;
    const vv = window.visualViewport;
    if (!vv) return;
    const height = vv.height || window.innerHeight;
    const width = vv.width || window.innerWidth;

    // Re-learn the baseline after rotation / large width changes.
    if (Math.abs(width - lastWidth) > 80) baselineHeight = Math.max(height, window.innerHeight);
    if (height > baselineHeight) baselineHeight = height;

    const keyboardRatio = baselineHeight > 0 ? 1 - (height / baselineHeight) : 0;
    root.classList.toggle('ef-keyboard-open', keyboardRatio > 0.18);
    root.classList.add('ef-vv-moving');

    lastHeight = height;
    lastWidth = width;
    clearTimeout(settleTimer);
    settleTimer = setTimeout(() => {
      root.classList.remove('ef-vv-moving');
      // After keyboard settles, allow ChatGPT/EdgeFlow to recompute naturally on next frame.
      requestAnimationFrame(() => window.dispatchEvent(new CustomEvent('edgeflow-viewport-settled', {
        detail: { height, width, keyboardRatio }
      })));
    }, 180);
  }

  function queueSample() {
    if (raf) return;
    raf = requestAnimationFrame(sample);
  }

  visualViewport.addEventListener('resize', queueSample, { passive: true });
  visualViewport.addEventListener('scroll', queueSample, { passive: true });
  window.addEventListener('orientationchange', () => {
    baselineHeight = 0;
    setTimeout(() => {
      baselineHeight = Math.max(window.innerHeight, visualViewport.height || 0);
      queueSample();
    }, 350);
  }, { passive: true });

  document.addEventListener('focusin', event => {
    const el = event.target;
    if (el && (el.matches?.('textarea,input,[contenteditable="true"]'))) queueSample();
  }, { passive: true });

  queueSample();
})();
