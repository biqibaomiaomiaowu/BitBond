import test from 'node:test';
import assert from 'node:assert/strict';
import {
  createPartnerStatusAutoRefresh,
  shouldRefreshPartnerStatus,
} from '../src/lib/partnerStatusAutoRefresh.mjs';

test('shouldRefreshPartnerStatus requires ready paired visible state', () => {
  assert.equal(
    shouldRefreshPartnerStatus({
      bridgeReady: true,
      paired: true,
      visibilityState: 'visible',
    }),
    true,
  );
  assert.equal(
    shouldRefreshPartnerStatus({
      bridgeReady: false,
      paired: true,
      visibilityState: 'visible',
    }),
    false,
  );
  assert.equal(
    shouldRefreshPartnerStatus({
      bridgeReady: true,
      paired: false,
      visibilityState: 'visible',
    }),
    false,
  );
  assert.equal(
    shouldRefreshPartnerStatus({
      bridgeReady: true,
      paired: true,
      visibilityState: 'hidden',
    }),
    false,
  );
});

test('auto refresh starts immediately, repeats, and stops when no longer paired', () => {
  const fakeWindow = createFakeWindow();
  const fakeDocument = createFakeDocument('visible');
  const refreshCalls = [];
  let paired = true;
  const autoRefresh = createPartnerStatusAutoRefresh({
    windowObject: fakeWindow,
    documentObject: fakeDocument,
    getBridgeReady: () => true,
    getPaired: () => paired,
    refresh: () => {
      refreshCalls.push('refresh');
    },
    intervalMs: 15_000,
  });

  autoRefresh.attach();

  assert.equal(refreshCalls.length, 1);
  assert.equal(fakeWindow.intervalCount(), 1);

  fakeWindow.runOnlyInterval();
  assert.equal(refreshCalls.length, 2);

  paired = false;
  autoRefresh.sync();

  assert.equal(fakeWindow.intervalCount(), 0);
});

test('auto refresh resumes when hidden page becomes visible', () => {
  const fakeWindow = createFakeWindow();
  const fakeDocument = createFakeDocument('hidden');
  const refreshCalls = [];
  const autoRefresh = createPartnerStatusAutoRefresh({
    windowObject: fakeWindow,
    documentObject: fakeDocument,
    getBridgeReady: () => true,
    getPaired: () => true,
    refresh: () => {
      refreshCalls.push('refresh');
    },
  });

  autoRefresh.attach();

  assert.equal(refreshCalls.length, 0);
  assert.equal(fakeWindow.intervalCount(), 0);

  fakeDocument.visibilityState = 'visible';
  fakeDocument.dispatch('visibilitychange');

  assert.equal(refreshCalls.length, 1);
  assert.equal(fakeWindow.intervalCount(), 1);
});

function createFakeWindow() {
  let nextIntervalId = 1;
  const intervals = new Map();
  const listeners = new Map();

  return {
    setInterval(callback, intervalMs) {
      const intervalId = nextIntervalId;
      nextIntervalId += 1;
      intervals.set(intervalId, { callback, intervalMs });
      return intervalId;
    },
    clearInterval(intervalId) {
      intervals.delete(intervalId);
    },
    addEventListener(eventName, callback) {
      listeners.set(eventName, callback);
    },
    removeEventListener(eventName) {
      listeners.delete(eventName);
    },
    dispatch(eventName) {
      listeners.get(eventName)?.();
    },
    intervalCount() {
      return intervals.size;
    },
    runOnlyInterval() {
      assert.equal(intervals.size, 1);
      intervals.values().next().value.callback();
    },
  };
}

function createFakeDocument(visibilityState) {
  const listeners = new Map();

  return {
    visibilityState,
    addEventListener(eventName, callback) {
      listeners.set(eventName, callback);
    },
    removeEventListener(eventName) {
      listeners.delete(eventName);
    },
    dispatch(eventName) {
      listeners.get(eventName)?.();
    },
  };
}
