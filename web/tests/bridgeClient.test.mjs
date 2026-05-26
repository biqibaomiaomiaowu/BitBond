import test from 'node:test';
import assert from 'node:assert/strict';
import { callBridgeMethod } from '../src/lib/bridgeClient.mjs';

test('callBridgeMethod invokes injected bridge methods with the bridge receiver', async () => {
  const expected = JSON.stringify({
    ok: true,
    data: {
      code: 'ABC123',
      expiresAt: 'later',
    },
  });
  const bridge = {
    createPairInvite() {
      if (this !== bridge) {
        throw new Error("Java bridge method can't be invoked on a non-injected object");
      }
      return expected;
    },
  };

  const result = await callBridgeMethod(bridge, 'createPairInvite', { ok: false });

  assert.equal(result, expected);
});

test('callBridgeMethod passes payload while preserving the bridge receiver', async () => {
  const bridge = {
    acceptPairInvite(payload) {
      if (this !== bridge) {
        throw new Error("Java bridge method can't be invoked on a non-injected object");
      }
      return {
        ok: true,
        data: JSON.parse(payload),
      };
    },
  };

  const result = await callBridgeMethod(
    bridge,
    'acceptPairInvite',
    { ok: false },
    JSON.stringify({ code: 'ABC123' }),
  );

  assert.deepEqual(JSON.parse(result), {
    ok: true,
    data: {
      code: 'ABC123',
    },
  });
});
