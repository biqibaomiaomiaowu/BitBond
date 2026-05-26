export const PARTNER_STATUS_REFRESH_INTERVAL_MS = 15_000;

export function shouldRefreshPartnerStatus({ bridgeReady, paired, visibilityState }) {
  return bridgeReady === true && paired === true && visibilityState !== 'hidden';
}

export function createPartnerStatusAutoRefresh({
  windowObject,
  documentObject,
  getBridgeReady,
  getPaired,
  refresh,
  intervalMs = PARTNER_STATUS_REFRESH_INTERVAL_MS,
}) {
  let intervalId;

  function canRefresh() {
    return shouldRefreshPartnerStatus({
      bridgeReady: getBridgeReady(),
      paired: getPaired(),
      visibilityState: documentObject?.visibilityState ?? 'visible',
    });
  }

  function runRefresh() {
    if (!canRefresh()) {
      return;
    }

    void Promise.resolve(refresh()).catch(() => {
      // Auto refresh is best-effort; manual refresh keeps surfacing errors.
    });
  }

  function start() {
    if (intervalId !== undefined || !canRefresh()) {
      return;
    }

    runRefresh();
    intervalId = windowObject.setInterval(runRefresh, intervalMs);
  }

  function stop() {
    if (intervalId === undefined) {
      return;
    }

    windowObject.clearInterval(intervalId);
    intervalId = undefined;
  }

  function sync() {
    if (canRefresh()) {
      start();
      return;
    }

    stop();
  }

  function handleVisibilityChange() {
    sync();
  }

  function handleFocus() {
    runRefresh();
  }

  function attach() {
    documentObject?.addEventListener?.('visibilitychange', handleVisibilityChange);
    windowObject?.addEventListener?.('focus', handleFocus);
    sync();
  }

  function detach() {
    stop();
    documentObject?.removeEventListener?.('visibilitychange', handleVisibilityChange);
    windowObject?.removeEventListener?.('focus', handleFocus);
  }

  return {
    attach,
    detach,
    sync,
  };
}
