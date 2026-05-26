import test from 'node:test';
import assert from 'node:assert/strict';
import {
  advanceRoomMotionPhase,
  applyAcceptInviteResult,
  applyAvatarSelectionResult,
  applyDebugForegroundResult,
  applyPairInviteResult,
  applyPartnerStatusResult,
  applyUnlinkResult,
  buildInitialState,
  createFallbackBridgeState,
  getRoomPresentation,
  statusConfigs,
} from '../src/lib/bitbondState.mjs';

test('fallback bridge state exposes abstract partner status without private app details', () => {
  const state = createFallbackBridgeState();

  assert.equal(state.bridge.ready, false);
  assert.equal(state.partner.statusCode, 'music');
  assert.equal(state.partner.statusText, '正在听音乐');
  assert.equal('packageName' in state.partner, false);
  assert.equal('appName' in state.partner, false);
  assert.equal('usageDuration' in state.partner, false);
  assert.equal('content' in state.partner, false);
  assert.equal('chatTarget' in state.partner, false);
});

test('status config covers the first-version abstract status set', () => {
  const expected = [
    'short_video',
    'watching_show',
    'reading',
    'music',
    'gaming',
    'social',
    'online',
    'resting',
    'offline',
    'paused',
  ];

  assert.deepEqual(
    statusConfigs.map((item) => item.code),
    expected,
  );
  assert.equal(statusConfigs.find((item) => item.code === 'music')?.propFile, 'prop_music_headphone.png');
});

test('buildInitialState merges valid bridge JSON over the local fallback', () => {
  const state = buildInitialState(
    JSON.stringify({
      bridge: { ready: true, message: 'Android bridge connected' },
      partner: {
        statusCode: 'reading',
        statusText: '正在使用 ExampleApp 阅读私密内容',
        areaLabel: '私密浏览页',
        updatedAt: '刚刚',
        packageName: 'com.example.reader',
        appName: 'ExampleApp',
        usageDuration: 3600,
        content: '私密文章标题',
        chatTarget: 'Alice',
      },
      self: { sharing: true },
      pair: { paired: true, nickname: '小禾' },
    }),
  );

  assert.equal(state.bridge.ready, true);
  assert.equal(state.partner.statusCode, 'reading');
  assert.equal(state.partner.statusText, '正在阅读');
  assert.equal(state.partner.areaLabel, '书架前');
  assert.equal(state.pair.nickname, '小禾');
  assert.equal('packageName' in state.partner, false);
  assert.equal('appName' in state.partner, false);
  assert.equal('usageDuration' in state.partner, false);
  assert.equal('content' in state.partner, false);
  assert.equal('chatTarget' in state.partner, false);
});

test('buildInitialState ignores malformed primitive bridge fields and keeps fallbacks', () => {
  const fallback = createFallbackBridgeState();
  const state = buildInitialState(
    JSON.stringify({
      bridge: { ready: 'false', message: 42 },
      self: { sharing: 'false', statusText: 12 },
      pair: { paired: 'false', nickname: false },
    }),
  );

  assert.equal(state.bridge.ready, fallback.bridge.ready);
  assert.equal(state.bridge.message, fallback.bridge.message);
  assert.equal(state.self.sharing, fallback.self.sharing);
  assert.equal(state.self.statusText, fallback.self.statusText);
  assert.equal(state.pair.paired, fallback.pair.paired);
  assert.equal(state.pair.nickname, fallback.pair.nickname);
});

test('buildInitialState keeps fallback partner status when Android sends a partial partner', () => {
  const state = buildInitialState(JSON.stringify({ partner: { updatedAt: '刚刚' } }));

  assert.equal(state.partner.statusCode, 'music');
  assert.equal(state.partner.statusText, '正在听音乐');
  assert.equal(state.partner.areaLabel, '音响旁');
});

test('buildInitialState keeps fallback partner status when Android sends an unknown status code', () => {
  const state = buildInitialState(
    JSON.stringify({
      partner: {
        statusCode: 'unknown_status',
        updatedAt: '1 分钟前',
      },
    }),
  );

  assert.equal(state.partner.statusCode, 'music');
  assert.equal(state.partner.statusText, '正在听音乐');
  assert.equal(state.partner.areaLabel, '音响旁');
});

test('applyPairInviteResult stores invite code without storing tokens', () => {
  const state = applyPairInviteResult(
    createFallbackBridgeState(),
    JSON.stringify({
      ok: true,
      data: {
        code: 'PAIR-2468',
        expiresAt: '2026-05-27T00:00:00.000Z',
      },
      access_token: 'access-secret',
      refresh_token: 'refresh-secret',
      token: 'token-secret',
    }),
  );

  assert.equal(state.pair.inviteCode, 'PAIR-2468');
  assert.equal(state.pair.expiresAt, '2026-05-27T00:00:00.000Z');
  assert.equal(state.notice.kind, 'success');
  assertPrivateFieldsAbsent(state);
  assertSerializedValuesAbsent(state, ['access-secret', 'refresh-secret', 'token-secret']);
});

test('applyAcceptInviteResult marks pair as paired', () => {
  const initial = {
    ...createFallbackBridgeState(),
    pair: {
      paired: false,
      nickname: '',
      inviteCode: 'PAIR-2468',
    },
  };
  const state = applyAcceptInviteResult(
    initial,
    JSON.stringify({
      ok: true,
      data: {
        paired: true,
        coupleId: 'couple-public-id',
        partner: {
          nickname: '小禾',
          avatarId: 'avatar_rabbit',
        },
      },
      access_token: 'accept-access-secret',
      refresh_token: 'accept-refresh-secret',
      token: 'accept-token-secret',
    }),
  );

  assert.equal(state.pair.paired, true);
  assert.equal(state.pair.nickname, '小禾');
  assert.equal(state.pair.coupleId, 'couple-public-id');
  assert.equal(state.pair.inviteCode, '');
  assert.equal(state.pair.expiresAt, undefined);
  assert.equal(state.notice.kind, 'success');
  assertPrivateFieldsAbsent(state);
  assertSerializedValuesAbsent(state, ['accept-access-secret', 'accept-refresh-secret', 'accept-token-secret']);
});

test('applyUnlinkResult clears pair and partner', () => {
  const initial = {
    ...createFallbackBridgeState(),
    pair: {
      paired: true,
      nickname: '小禾',
      inviteCode: 'PAIR-2468',
      expiresAt: '2026-05-27T00:00:00.000Z',
      coupleId: 'couple-public-id',
    },
    partner: {
      statusCode: 'reading',
      statusText: '正在阅读',
      updatedAt: '刚刚',
      areaLabel: '书架前',
      packageName: 'com.example.reader',
    },
  };
  const state = applyUnlinkResult(
    initial,
    JSON.stringify({
      ok: true,
      token: 'unlink-token-secret',
    }),
  );

  assert.equal(state.pair.paired, false);
  assert.equal(state.pair.nickname, '');
  assert.equal(state.pair.inviteCode, '');
  assert.equal(state.pair.expiresAt, '');
  assert.equal(state.pair.coupleId, '');
  assert.equal(state.partner.statusCode, 'offline');
  assert.equal(state.partner.statusText, '暂时离线');
  assert.equal(state.partner.areaLabel, '门口');
  assert.equal(state.partner.updatedAt, '已解除配对');
  assertPrivateFieldsAbsent(state);
  assertSerializedValuesAbsent(state, ['com.example.reader', 'unlink-token-secret']);
});

test('applyAvatarSelectionResult updates selected avatar', () => {
  const state = applyAvatarSelectionResult(
    createFallbackBridgeState(),
    JSON.stringify({
      ok: true,
      data: {
        avatarId: 'avatar_cat',
      },
    }),
  );

  assert.equal(state.self.selectedAvatar, 'avatar_cat');
  assert.equal(state.notice.kind, 'success');
});

test('applyPartnerStatusResult keeps only abstract partner status', () => {
  const state = applyPartnerStatusResult(
    createFallbackBridgeState(),
    JSON.stringify({
      ok: true,
      data: {
        upload: {
          code: 'social',
        },
        partnerStatus: {
          paired: true,
          partner: {
            nickname: '小禾',
            avatarId: 'avatar_rabbit',
          },
          statusCode: 'social',
          statusUpdatedAt: '2026-05-26T08:30:00.000Z',
          expiresAt: '2026-05-26T08:35:00.000Z',
          isPaused: false,
          statusText: '正在使用 WeChat 与 Alice 聊天',
          areaLabel: 'WeChat 聊天页',
          packageName: 'com.tencent.mm',
          appName: 'WeChat',
          usageDuration: 3600,
          content: '聊天内容',
          chatTarget: 'Alice',
        },
      },
      token: 'status-token-secret',
    }),
  );

  assert.equal(state.partner.statusCode, 'social');
  assert.equal(state.partner.statusText, '正在聊天');
  assert.equal(state.partner.areaLabel, '窗边');
  assert.equal(state.partner.updatedAt, '2026-05-26T08:30:00.000Z');
  assertPrivateFieldsAbsent(state);
  assertSerializedValuesAbsent(state, ['WeChat', 'Alice', '聊天内容', 'status-token-secret']);
});

test('applyPartnerStatusResult keeps current partner status when partner status code is missing', () => {
  const state = applyPartnerStatusResult(
    createFallbackBridgeState(),
    JSON.stringify({
      ok: true,
      data: {
        upload: {
          code: 'gaming',
        },
        partnerStatus: {
          paired: true,
          partner: {
            nickname: 'partner',
          },
          statusUpdatedAt: '2026-05-26T08:30:00.000Z',
        },
      },
    }),
  );

  assert.equal(state.partner.statusCode, 'music');
  assertSerializedValuesAbsent(state, ['gaming']);
});

test('applyPartnerStatusResult clears partner to offline when partner status is unpaired', () => {
  const fallback = createFallbackBridgeState();
  const initial = {
    ...fallback,
    pair: {
      ...fallback.pair,
      inviteCode: 'PAIR-2468',
      expiresAt: '2026-05-27T00:00:00.000Z',
      coupleId: 'couple-public-id',
    },
  };
  const state = applyPartnerStatusResult(
    initial,
    JSON.stringify({
      ok: true,
      data: {
        upload: {
          code: 'deduplicated',
        },
        partnerStatus: {
          paired: false,
          packageName: 'com.example.private',
          appName: 'PrivateApp',
        },
      },
      token: 'unpaired-token-secret',
    }),
  );

  assert.equal(state.pair.paired, false);
  assert.equal(state.pair.inviteCode, '');
  assert.equal(state.pair.expiresAt, '');
  assert.equal(state.pair.coupleId, '');
  assert.equal(state.partner.statusCode, 'offline');
  assert.equal(state.partner.statusText, '暂时离线');
  assert.equal(state.partner.areaLabel, '门口');
  assertPrivateFieldsAbsent(state);
  assertSerializedValuesAbsent(state, ['deduplicated', 'com.example.private', 'PrivateApp', 'unpaired-token-secret']);
});

test('applyDebugForegroundResult reads data enabled and never stores package name', () => {
  const state = applyDebugForegroundResult(
    createFallbackBridgeState(),
    JSON.stringify({
      ok: true,
      data: {
        enabled: true,
        code: 'debug_foreground',
        packageName: 'com.example.private',
        appName: 'PrivateApp',
      },
    }),
  );

  assert.deepEqual(state.debugForeground, { enabled: true });
  assertPrivateFieldsAbsent(state);
  assertSerializedValuesAbsent(state, ['com.example.private', 'PrivateApp']);
});

test('applyDebugForegroundResult keeps disabled foreground result abstract', () => {
  const state = applyDebugForegroundResult(
    createFallbackBridgeState(),
    JSON.stringify({
      ok: true,
      data: {
        enabled: false,
        code: 'debug_disabled',
        packageName: 'com.example.private',
        appName: 'PrivateApp',
      },
    }),
  );

  assert.deepEqual(state.debugForeground, { enabled: false });
  assertPrivateFieldsAbsent(state);
  assertSerializedValuesAbsent(state, ['debug_disabled', 'com.example.private', 'PrivateApp']);
});

test('every first-version status has room presentation metadata', () => {
  for (const status of statusConfigs) {
    const presentation = getRoomPresentation(status.code);

    assert.ok(Array.isArray(status.roomCandidates), status.code);
    assert.ok(status.roomCandidates.length > 0, status.code);
    assert.equal(typeof status.propFrame, 'number', status.code);
    assert.equal(status.movePhase.name, 'move', status.code);
    assert.equal(status.actionPhase.name, 'action', status.code);
    assert.equal(presentation.statusCode, status.code);
    assert.deepEqual(presentation.roomCandidates, status.roomCandidates);
    assert.equal(presentation.propFrame, status.propFrame);
    assert.deepEqual(presentation.movePhase, status.movePhase);
    assert.deepEqual(presentation.actionPhase, status.actionPhase);
  }
});

test('advanceRoomMotionPhase changes move to action only after movement completes', () => {
  const moving = { phase: 'move', statusCode: 'music' };

  assert.equal(advanceRoomMotionPhase(moving, { movementComplete: false }).phase, 'move');
  assert.equal(advanceRoomMotionPhase(moving, {}).phase, 'move');
  assert.equal(advanceRoomMotionPhase(moving, { movementComplete: true }).phase, 'action');
  assert.equal(advanceRoomMotionPhase({ phase: 'action', statusCode: 'music' }, { movementComplete: false }).phase, 'action');
});

test('applyUnlinkResult clears paired state only when bridge confirms unlink', () => {
  const initial = buildInitialState();
  const unlinked = applyUnlinkResult(initial, JSON.stringify({ ok: true, reason: 'user_unlinked' }));

  assert.equal(unlinked.pair.paired, false);
  assert.equal(unlinked.partner.statusCode, 'offline');
  assert.equal(unlinked.notice.kind, 'success');
});

test('applyUnlinkResult keeps paired state when bridge rejects or returns malformed unlink result', () => {
  const initial = buildInitialState(
    JSON.stringify({
      pair: { paired: true, nickname: '小禾' },
      partner: { statusCode: 'reading', updatedAt: '刚刚' },
    }),
  );

  const rejected = applyUnlinkResult(initial, JSON.stringify({ ok: false }));
  const malformed = applyUnlinkResult(initial, '{not valid json');

  assert.deepEqual(rejected.pair, initial.pair);
  assert.equal(rejected.partner.statusCode, 'reading');
  assert.equal(rejected.notice.kind, 'error');
  assert.deepEqual(malformed.pair, initial.pair);
  assert.equal(malformed.partner.statusCode, 'reading');
  assert.equal(malformed.notice.kind, 'error');
});

function assertPrivateFieldsAbsent(value) {
  const privateFields = new Set([
    'packageName',
    'appName',
    'usageDuration',
    'content',
    'chatTarget',
    'access_token',
    'refresh_token',
    'token',
  ]);

  visit(value, 'state');

  function visit(node, path) {
    if (!node || typeof node !== 'object') {
      return;
    }

    for (const [key, child] of Object.entries(node)) {
      assert.equal(privateFields.has(key), false, `${path}.${key} should not be stored`);
      visit(child, `${path}.${key}`);
    }
  }
}

function assertSerializedValuesAbsent(value, forbiddenValues) {
  const serialized = JSON.stringify(value);

  for (const forbiddenValue of forbiddenValues) {
    assert.equal(serialized.includes(forbiddenValue), false, `${forbiddenValue} should not be stored`);
  }
}
