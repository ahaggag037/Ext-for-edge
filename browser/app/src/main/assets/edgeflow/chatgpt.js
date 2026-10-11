(() => {
  'use strict';

  const STATE = {
    coldKeep: 5,
    pain: 0,
    shielded: false,
    showLiveForCurrentRun: false,
    timer: 0,
    observer: null,
    badge: null,
    lastTick: performance.now(),
    generationSeen: false
  };

  const TURN_SELECTOR = [
    '[data-message-author-role]',
    'article[data-turn]',
    '[data-turn-key]'
  ].join(',');

  const ASSISTANT_SELECTOR = [
    '[data-message-author-role="assistant"]',
    'article[data-turn="assistant"]',
    '[data-turn-key][data-message-author-role="assistant"]'
  ].join(',');

  function addStyles() {
    const style = document.createElement('style');
    style.id = 'edgeflow-core-style';
    style.textContent = `
      html.edgeflow-core * {
        scroll-behavior: auto !important;
      }
      html.edgeflow-core .ef-cold {
        content-visibility: auto !important;
        contain-intrinsic-size: auto 720px !important;
        contain: layout paint style !important;
      }
      html.edgeflow-core .ef-hot {
        contain: style paint !important;
      }
      html.edgeflow-core.ef-pressure *,
      html.edgeflow-core.ef-pressure *::before,
      html.edgeflow-core.ef-pressure *::after {
        animation-duration: 0.001ms !important;
        animation-iteration-count: 1 !important;
        transition-duration: 0.001ms !important;
        caret-color: auto;
      }
      html.edgeflow-core .ef-stream-shield {
        contain: strict !important;
        min-height: 140px !important;
      }
      html.edgeflow-core .ef-stream-shield > * {
        visibility: hidden !important;
      }
      #edgeflow-shield-badge {
        position: fixed;
        right: 10px;
        bottom: 86px;
        z-index: 2147483647;
        font: 12px/1.35 system-ui, sans-serif;
        color: #fff;
        background: rgba(10, 14, 20, .90);
        border: 1px solid rgba(255,255,255,.18);
        border-radius: 12px;
        padding: 8px 10px;
        box-shadow: 0 8px 24px rgba(0,0,0,.25);
        max-width: 210px;
      }
      #edgeflow-shield-badge button {
        margin-top: 6px;
        border: 1px solid rgba(255,255,255,.24);
        background: rgba(255,255,255,.10);
        color: #fff;
        border-radius: 9px;
        padding: 5px 8px;
        font: inherit;
      }
    `;
    (document.head || document.documentElement).appendChild(style);
    document.documentElement.classList.add('edgeflow-core');
  }

  function isGenerating() {
    return !!document.querySelector([
      'button[data-testid="stop-button"]',
      'button[aria-label*="Stop"]',
      'button[aria-label*="stop"]',
      'button[aria-label*="إيقاف"]'
    ].join(','));
  }

  function turns() {
    const root = document.querySelector('main') || document.body;
    if (!root) return [];
    return Array.from(root.querySelectorAll(TURN_SELECTOR));
  }

  function assistantTurns() {
    const root = document.querySelector('main') || document.body;
    if (!root) return [];
    return Array.from(root.querySelectorAll(ASSISTANT_SELECTOR));
  }

  function currentAssistantTurn() {
    const list = assistantTurns();
    return list.length ? list[list.length - 1] : null;
  }

  function applyWindowing() {
    const list = turns();
    if (!list.length) return;
    const cutoff = Math.max(0, list.length - STATE.coldKeep);
    for (let i = 0; i < list.length; i++) {
      const node = list[i];
      if (i < cutoff) {
        node.classList.add('ef-cold');
        node.classList.remove('ef-hot');
      } else {
        node.classList.remove('ef-cold');
        node.classList.add('ef-hot');
      }
    }
  }

  function showBadge() {
    if (STATE.badge && STATE.badge.isConnected) return;
    const badge = document.createElement('div');
    badge.id = 'edgeflow-shield-badge';
    const text = document.createElement('div');
    text.textContent = '⚡ EdgeFlow is reducing live rendering to keep ChatGPT responsive.';
    const button = document.createElement('button');
    button.type = 'button';
    button.textContent = 'Show live response';
    button.addEventListener('click', () => {
      STATE.showLiveForCurrentRun = true;
      removeShield();
    });
    badge.append(text, button);
    (document.body || document.documentElement).appendChild(badge);
    STATE.badge = badge;
  }

  function hideBadge() {
    if (STATE.badge) STATE.badge.remove();
    STATE.badge = null;
  }

  function applyShield() {
    if (STATE.shielded || STATE.showLiveForCurrentRun) return;
    const target = currentAssistantTurn();
    if (!target) return;
    target.classList.add('ef-stream-shield');
    STATE.shielded = true;
    showBadge();
  }

  function removeShield() {
    document.querySelectorAll('.ef-stream-shield').forEach(n => n.classList.remove('ef-stream-shield'));
    STATE.shielded = false;
    hideBadge();
  }

  function updatePressure() {
    if (STATE.pain >= 3) document.documentElement.classList.add('ef-pressure');
    else document.documentElement.classList.remove('ef-pressure');
  }

  function performanceTick() {
    const now = performance.now();
    const drift = now - STATE.lastTick - 500;
    STATE.lastTick = now;

    const generating = isGenerating();
    if (generating) {
      STATE.generationSeen = true;
      if (drift > 120) STATE.pain = Math.min(10, STATE.pain + 2);
      else STATE.pain = Math.max(0, STATE.pain - 0.25);

      if (STATE.pain >= 4 && !STATE.showLiveForCurrentRun) applyShield();
      applyWindowing();
    } else {
      if (STATE.generationSeen) {
        STATE.generationSeen = false;
        STATE.showLiveForCurrentRun = false;
        STATE.pain = 0;
        removeShield();
        applyWindowing();
      } else {
        STATE.pain = Math.max(0, STATE.pain - 0.5);
      }
    }
    updatePressure();
  }

  function scheduleWindowing() {
    if (STATE.timer) return;
    const run = () => {
      STATE.timer = 0;
      applyWindowing();
    };
    if ('requestIdleCallback' in window) {
      STATE.timer = requestIdleCallback(run, { timeout: 600 });
    } else {
      STATE.timer = setTimeout(run, 120);
    }
  }

  function startObserver() {
    if (STATE.observer || !document.documentElement) return;
    STATE.observer = new MutationObserver(records => {
      let relevant = false;
      for (const record of records) {
        if (record.addedNodes && record.addedNodes.length) {
          relevant = true;
          break;
        }
      }
      if (relevant) scheduleWindowing();
    });
    STATE.observer.observe(document.documentElement, { childList: true, subtree: true });
  }

  addStyles();
  startObserver();
  setInterval(performanceTick, 500);
  window.addEventListener('pageshow', scheduleWindowing, { passive: true });
  document.addEventListener('visibilitychange', () => {
    if (!document.hidden) scheduleWindowing();
  }, { passive: true });
})();
