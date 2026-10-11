(() => {
  'use strict';

  const STATE = {
    coldKeep: 6,
    shieldThreshold: 5,
    pressure: 0,
    thermal: 0,
    pain: 0,
    shielded: false,
    shieldTarget: null,
    showLiveForCurrentRun: false,
    timer: 0,
    observer: null,
    badge: null,
    turns: [],
    lastTick: performance.now(),
    generationSeen: false,
    lastTelemetryAt: 0,
    telemetryBusy: false
  };

  const TURN_SELECTOR = [
    '[data-message-author-role]',
    'article[data-turn]',
    '[data-turn-key]'
  ].join(',');

  function addStyles() {
    const style = document.createElement('style');
    style.id = 'edgeflow-core-style';
    style.textContent = `
      html.edgeflow-core * { scroll-behavior: auto !important; }
      html.edgeflow-core .ef-cold {
        content-visibility: auto !important;
        contain-intrinsic-size: auto 720px !important;
        contain: layout paint style !important;
      }
      html.edgeflow-core .ef-hot { contain: style paint !important; }
      html.edgeflow-core.ef-pressure-high *,
      html.edgeflow-core.ef-pressure-high *::before,
      html.edgeflow-core.ef-pressure-high *::after {
        animation-duration: 0.001ms !important;
        animation-iteration-count: 1 !important;
        transition-duration: 0.001ms !important;
      }
      html.edgeflow-core.ef-pressure-critical [style*="backdrop-filter"],
      html.edgeflow-core.ef-pressure-critical [class*="backdrop"] {
        backdrop-filter: none !important;
      }
      html.edgeflow-core .ef-stream-shield {
        contain: strict !important;
        min-height: 140px !important;
      }
      html.edgeflow-core .ef-stream-shield > * { visibility: hidden !important; }
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
        max-width: 220px;
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

  function roleOf(node) {
    if (!node || node.nodeType !== 1) return '';
    const explicit = node.getAttribute('data-message-author-role');
    if (explicit) return explicit;
    const turn = node.getAttribute('data-turn');
    if (turn) return turn;
    const roleChild = node.querySelector?.('[data-message-author-role]');
    return roleChild?.getAttribute('data-message-author-role') || '';
  }

  function addTurn(node) {
    if (!node || node.nodeType !== 1 || !node.matches?.(TURN_SELECTOR)) return;
    if (!STATE.turns.includes(node)) STATE.turns.push(node);
  }

  function scanAdded(node) {
    if (!node || node.nodeType !== 1) return;
    addTurn(node);
    node.querySelectorAll?.(TURN_SELECTOR).forEach(addTurn);
  }

  function compactTurns() {
    if (STATE.turns.length < 2) return;
    STATE.turns = STATE.turns.filter(node => node && node.isConnected);
  }

  function currentAssistantTurn() {
    compactTurns();
    for (let i = STATE.turns.length - 1; i >= 0; i--) {
      if (roleOf(STATE.turns[i]) === 'assistant') return STATE.turns[i];
    }
    return null;
  }

  function applyWindowing() {
    compactTurns();
    const list = STATE.turns;
    if (!list.length) return;
    const cutoff = Math.max(0, list.length - Math.max(1, STATE.coldKeep));
    for (let i = 0; i < list.length; i++) {
      const node = list[i];
      if (node === STATE.shieldTarget) continue;
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
    text.textContent = '⚡ EdgeFlow is buffering live paint to keep ChatGPT responsive.';
    const button = document.createElement('button');
    button.type = 'button';
    button.textContent = 'Show live response';
    button.addEventListener('click', () => {
      STATE.showLiveForCurrentRun = true;
      removeShield();
      sendTelemetry(true);
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
    if (STATE.showLiveForCurrentRun) return;
    const target = currentAssistantTurn();
    if (!target) return;
    if (STATE.shieldTarget && STATE.shieldTarget !== target) {
      STATE.shieldTarget.classList.remove('ef-stream-shield');
    }
    target.classList.remove('ef-cold');
    target.classList.add('ef-hot', 'ef-stream-shield');
    STATE.shieldTarget = target;
    STATE.shielded = true;
    showBadge();
  }

  function removeShield() {
    if (STATE.shieldTarget) STATE.shieldTarget.classList.remove('ef-stream-shield');
    document.querySelectorAll('.ef-stream-shield').forEach(node => node.classList.remove('ef-stream-shield'));
    STATE.shieldTarget = null;
    STATE.shielded = false;
    hideBadge();
  }

  function updatePressureClasses() {
    const root = document.documentElement;
    root.classList.toggle('ef-pressure-high', STATE.pressure >= 2 || STATE.pain >= 4);
    root.classList.toggle('ef-pressure-critical', STATE.pressure >= 3);
  }

  function applyNativePolicy(policy) {
    if (!policy || typeof policy !== 'object') return;
    const keep = Number(policy.coldKeep);
    const threshold = Number(policy.shieldThreshold);
    if (Number.isFinite(keep) && keep >= 1 && keep <= 12) STATE.coldKeep = keep;
    if (Number.isFinite(threshold) && threshold >= 1 && threshold <= 10) STATE.shieldThreshold = threshold;
    STATE.pressure = Number(policy.pressure) || 0;
    STATE.thermal = Number(policy.thermal) || 0;
    updatePressureClasses();
    scheduleWindowing();
  }

  function sendTelemetry(force = false) {
    const now = performance.now();
    if (!force && (STATE.telemetryBusy || now - STATE.lastTelemetryAt < 1200)) return;
    if (typeof browser === 'undefined' || !browser.runtime?.sendNativeMessage) return;
    STATE.telemetryBusy = true;
    STATE.lastTelemetryAt = now;
    const message = {
      type: 'telemetry',
      generating: isGenerating(),
      shielded: STATE.shielded,
      pain: Math.round(STATE.pain),
      turns: STATE.turns.length,
      hidden: document.hidden
    };
    Promise.resolve(browser.runtime.sendNativeMessage('edgeflow.browser', message))
      .then(applyNativePolicy)
      .catch(() => {})
      .finally(() => { STATE.telemetryBusy = false; });
  }

  function performanceTick() {
    const now = performance.now();
    const drift = now - STATE.lastTick - 500;
    STATE.lastTick = now;
    const generating = isGenerating();

    if (generating) {
      STATE.generationSeen = true;
      if (drift > 120) STATE.pain = Math.min(10, STATE.pain + 2);
      else if (drift > 55) STATE.pain = Math.min(10, STATE.pain + 0.8);
      else STATE.pain = Math.max(0, STATE.pain - 0.25);

      if (STATE.pain >= STATE.shieldThreshold && !STATE.showLiveForCurrentRun) applyShield();
      if (STATE.shielded) applyShield();
      scheduleWindowing();
    } else if (STATE.generationSeen) {
      STATE.generationSeen = false;
      STATE.showLiveForCurrentRun = false;
      STATE.pain = 0;
      removeShield();
      scheduleWindowing();
      sendTelemetry(true);
    } else {
      STATE.pain = Math.max(0, STATE.pain - 0.5);
    }

    updatePressureClasses();
    sendTelemetry(false);
  }

  function scheduleWindowing() {
    if (STATE.timer) return;
    const run = () => {
      STATE.timer = 0;
      applyWindowing();
    };
    if ('requestIdleCallback' in window) {
      STATE.timer = requestIdleCallback(run, { timeout: 450 });
    } else {
      STATE.timer = setTimeout(run, 80);
    }
  }

  function startObserver() {
    if (STATE.observer || !document.documentElement) return;
    const root = document.querySelector('main') || document.documentElement;
    root.querySelectorAll?.(TURN_SELECTOR).forEach(addTurn);
    STATE.observer = new MutationObserver(records => {
      let touched = false;
      for (const record of records) {
        if (!record.addedNodes?.length && !record.removedNodes?.length) continue;
        touched = true;
        record.addedNodes?.forEach(scanAdded);
      }
      if (touched) {
        compactTurns();
        if (STATE.shielded) applyShield();
        scheduleWindowing();
      }
    });
    STATE.observer.observe(root, { childList: true, subtree: true });
    scheduleWindowing();
  }

  addStyles();
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', startObserver, { once: true });
  } else {
    startObserver();
  }
  setInterval(performanceTick, 500);
  window.addEventListener('pageshow', () => { startObserver(); scheduleWindowing(); sendTelemetry(true); }, { passive: true });
  document.addEventListener('visibilitychange', () => {
    if (!document.hidden) { startObserver(); scheduleWindowing(); }
    sendTelemetry(true);
  }, { passive: true });
})();
