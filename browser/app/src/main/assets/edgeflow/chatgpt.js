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
    windowTimer: 0,
    loopTimer: 0,
    observer: null,
    observedRoot: null,
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
  const OUTER_TURN_SELECTOR = 'article[data-turn], [data-turn-key]';
  const STOP_SELECTOR = [
    'button[data-testid="stop-button"]',
    'button[aria-label*="Stop"]',
    'button[aria-label*="stop"]',
    'button[aria-label*="إيقاف"]'
  ].join(',');

  function addStyles() {
    if (document.getElementById('edgeflow-core-style')) return;
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
        content-visibility: hidden !important;
        contain: strict !important;
        contain-intrinsic-size: var(--ef-shield-h, 180px) !important;
        overflow-anchor: none !important;
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
    return !document.hidden && !!document.querySelector(STOP_SELECTOR);
  }

  function normalizeTurn(node) {
    if (!node || node.nodeType !== Node.ELEMENT_NODE) return null;
    if (node.matches?.(OUTER_TURN_SELECTOR)) return node;
    const outer = node.closest?.(OUTER_TURN_SELECTOR);
    if (outer) return outer;
    if (node.matches?.('[data-message-author-role]')) return node;
    return node.closest?.('[data-message-author-role]') || null;
  }

  function roleOf(node) {
    if (!node || node.nodeType !== Node.ELEMENT_NODE) return '';
    const explicit = node.getAttribute('data-message-author-role');
    if (explicit) return explicit;
    const turn = node.getAttribute('data-turn');
    if (turn === 'assistant' || turn === 'user') return turn;
    const roleChild = node.querySelector?.('[data-message-author-role]');
    return roleChild?.getAttribute('data-message-author-role') || '';
  }

  function addTurn(candidate) {
    const node = normalizeTurn(candidate);
    if (!node || STATE.turns.includes(node)) return false;
    STATE.turns.push(node);
    return true;
  }

  function scanAdded(node) {
    if (!node || node.nodeType !== Node.ELEMENT_NODE) return false;
    let changed = false;
    if (node.matches?.(TURN_SELECTOR)) changed = addTurn(node) || changed;
    node.querySelectorAll?.(TURN_SELECTOR).forEach(candidate => {
      changed = addTurn(candidate) || changed;
    });
    return changed;
  }

  function compactTurns(sort = false) {
    const before = STATE.turns.length;
    STATE.turns = STATE.turns.filter(node => node && node.isConnected);
    if (sort && STATE.turns.length > 1) {
      STATE.turns.sort((a, b) => {
        if (a === b) return 0;
        const relation = a.compareDocumentPosition(b);
        return relation & Node.DOCUMENT_POSITION_FOLLOWING ? -1 : 1;
      });
    }
    return before !== STATE.turns.length;
  }

  function currentAssistantTurn() {
    compactTurns(false);
    for (let i = STATE.turns.length - 1; i >= 0; i--) {
      if (roleOf(STATE.turns[i]) === 'assistant') return STATE.turns[i];
    }
    return null;
  }

  function applyWindowing() {
    compactTurns(true);
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
    text.textContent = '⚡ EdgeFlow paused live rendering to keep ChatGPT responsive.';
    const button = document.createElement('button');
    button.type = 'button';
    button.textContent = 'Show live response';
    button.addEventListener('click', () => {
      STATE.showLiveForCurrentRun = true;
      removeShield();
      sendTelemetry(true, true);
    });
    badge.append(text, button);
    (document.body || document.documentElement).appendChild(badge);
    STATE.badge = badge;
  }

  function clearShieldNode(node) {
    if (!node) return;
    node.classList.remove('ef-stream-shield');
    node.style.removeProperty('--ef-shield-h');
  }

  function hideBadge() {
    if (STATE.badge) STATE.badge.remove();
    STATE.badge = null;
  }

  function applyShield() {
    if (STATE.showLiveForCurrentRun || document.hidden) return;
    const target = currentAssistantTurn();
    if (!target) return;

    if (STATE.shieldTarget && STATE.shieldTarget !== target) {
      clearShieldNode(STATE.shieldTarget);
    }
    if (STATE.shieldTarget !== target || !target.classList.contains('ef-stream-shield')) {
      const measured = Math.max(100, Math.ceil(target.getBoundingClientRect().height || 0));
      target.style.setProperty('--ef-shield-h', measured + 'px');
    }
    target.classList.remove('ef-cold');
    target.classList.add('ef-hot', 'ef-stream-shield');
    STATE.shieldTarget = target;
    STATE.shielded = true;
    showBadge();
  }

  function removeShield() {
    clearShieldNode(STATE.shieldTarget);
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
    const oldKeep = STATE.coldKeep;
    const oldThreshold = STATE.shieldThreshold;
    const oldPressure = STATE.pressure;
    const oldThermal = STATE.thermal;

    const keep = Number(policy.coldKeep);
    const threshold = Number(policy.shieldThreshold);
    if (Number.isFinite(keep) && keep >= 1 && keep <= 12) STATE.coldKeep = keep;
    if (Number.isFinite(threshold) && threshold >= 1 && threshold <= 10) STATE.shieldThreshold = threshold;
    STATE.pressure = Number(policy.pressure) || 0;
    STATE.thermal = Number(policy.thermal) || 0;

    if (oldPressure !== STATE.pressure || oldThermal !== STATE.thermal) updatePressureClasses();
    if (oldKeep !== STATE.coldKeep) scheduleWindowing();
    if (oldThreshold !== STATE.shieldThreshold && STATE.shielded && STATE.pain < STATE.shieldThreshold) {
      removeShield();
    }
  }

  function sendTelemetry(force = false, generatingHint = null) {
    const now = performance.now();
    const generating = typeof generatingHint === 'boolean' ? generatingHint : isGenerating();
    const minInterval = document.hidden ? 10000 : (generating || STATE.shielded ? 1200 : 5000);
    if (!force && (STATE.telemetryBusy || now - STATE.lastTelemetryAt < minInterval)) return;
    if (typeof browser === 'undefined' || !browser.runtime?.sendNativeMessage) return;

    STATE.telemetryBusy = true;
    STATE.lastTelemetryAt = now;
    const message = {
      type: 'telemetry',
      generating,
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

  function performanceTick(expectedDelay) {
    STATE.loopTimer = 0;
    const now = performance.now();
    const drift = document.hidden ? 0 : Math.max(0, now - STATE.lastTick - expectedDelay);
    STATE.lastTick = now;
    const generating = isGenerating();

    if (document.hidden) {
      if (STATE.shielded) removeShield();
      STATE.pain = Math.max(0, STATE.pain - 1);
    } else if (generating) {
      STATE.generationSeen = true;
      if (drift > 120) STATE.pain = Math.min(10, STATE.pain + 2);
      else if (drift > 55) STATE.pain = Math.min(10, STATE.pain + 0.8);
      else STATE.pain = Math.max(0, STATE.pain - 0.25);

      if (STATE.pain >= STATE.shieldThreshold && !STATE.showLiveForCurrentRun) applyShield();
    } else if (STATE.generationSeen) {
      STATE.generationSeen = false;
      STATE.showLiveForCurrentRun = false;
      STATE.pain = 0;
      removeShield();
      scheduleWindowing();
      sendTelemetry(true, false);
    } else {
      STATE.pain = Math.max(0, STATE.pain - 0.5);
    }

    updatePressureClasses();
    sendTelemetry(false, generating);

    const nextDelay = document.hidden ? 4000 : (generating ? 350 : 900);
    STATE.loopTimer = setTimeout(() => performanceTick(nextDelay), nextDelay);
  }

  function startPerformanceLoop() {
    if (STATE.loopTimer) return;
    STATE.lastTick = performance.now();
    const delay = document.hidden ? 4000 : 500;
    STATE.loopTimer = setTimeout(() => performanceTick(delay), delay);
  }

  function scheduleWindowing() {
    if (STATE.windowTimer) return;
    const run = () => {
      STATE.windowTimer = 0;
      applyWindowing();
    };
    if ('requestIdleCallback' in window) {
      STATE.windowTimer = requestIdleCallback(run, { timeout: 450 });
    } else {
      STATE.windowTimer = setTimeout(run, 80);
    }
  }

  function chooseObserverRoot() {
    return document.querySelector('main') || document.documentElement;
  }

  function startObserver() {
    if (!document.documentElement) return;
    const root = chooseObserverRoot();
    if (STATE.observer && STATE.observedRoot === root && root.isConnected) return;
    if (STATE.observer) STATE.observer.disconnect();

    STATE.observer = null;
    STATE.observedRoot = root;
    STATE.turns = [];
    root.querySelectorAll?.(TURN_SELECTOR).forEach(addTurn);
    compactTurns(true);

    STATE.observer = new MutationObserver(records => {
      let structuralChange = false;
      for (const record of records) {
        if (record.removedNodes?.length) structuralChange = true;
        if (!record.addedNodes?.length) continue;
        for (const node of record.addedNodes) {
          structuralChange = scanAdded(node) || structuralChange;
        }
      }
      if (!structuralChange) return;
      compactTurns(true);
      if (STATE.shielded) applyShield();
      scheduleWindowing();
    });
    STATE.observer.observe(root, { childList: true, subtree: true });
    scheduleWindowing();
  }

  function revalidateObserver() {
    if (!STATE.observedRoot?.isConnected) startObserver();
  }

  addStyles();
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
      startObserver();
      startPerformanceLoop();
    }, { once: true });
  } else {
    startObserver();
    startPerformanceLoop();
  }

  window.addEventListener('pageshow', () => {
    startObserver();
    startPerformanceLoop();
    scheduleWindowing();
    sendTelemetry(true);
  }, { passive: true });

  document.addEventListener('visibilitychange', () => {
    revalidateObserver();
    if (!document.hidden) scheduleWindowing();
    STATE.lastTick = performance.now();
    sendTelemetry(true);
  }, { passive: true });

  setInterval(revalidateObserver, 5000);
})();
