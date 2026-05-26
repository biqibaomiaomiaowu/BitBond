export async function callBridgeMethod(bridge, method, fallback, payload) {
  const bridgeMethod = bridge?.[method];

  if (typeof bridgeMethod !== 'function') {
    return JSON.stringify(fallback);
  }

  try {
    const rawResult =
      payload === undefined ? await bridgeMethod.call(bridge) : await bridgeMethod.call(bridge, payload);
    return normalizeBridgeJson(rawResult, fallback);
  } catch {
    return JSON.stringify({ ok: false });
  }
}

export function normalizeBridgeJson(value, fallback) {
  if (typeof value === 'string') {
    return value;
  }
  if (value && typeof value === 'object') {
    return JSON.stringify(value);
  }
  return fallback ? JSON.stringify(fallback) : '';
}
