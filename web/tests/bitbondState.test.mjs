import test from 'node:test';
import assert from 'node:assert/strict';
import * as bitbondState from '../src/lib/bitbondState.mjs';

const {
  advanceRoomMotionPhase,
  applyAcceptInviteResult,
  applyAvatarSelectionResult,
  applyDebugForegroundResult,
  applyDeleteAccountResult,
  applyLatestInteractionsResult,
  applyMarkInteractionsSeenResult,
  applyPairInviteResult,
  applyPartnerStatusResult,
  applyPauseSharingResult,
  applyPrivacySettingsResult,
  applyResumeSharingResult,
  applyUnlinkResult,
  buildAvatarViewModel,
  buildDebugViewModel,
  buildHomeStatusViewModel,
  buildInitialState,
  buildInteractionsViewModel,
  buildPairingViewModel,
  buildPermissionViewModel,
  buildPrivacySettingsUpdatePayload,
  buildPrivacySettingsViewModel,
  buildSettingsViewModel,
  buildSharingViewModel,
  createFallbackBridgeState,
  getRoomPresentation,
  privacyCategoryCodes,
  sanitizeDebugForeground,
  statusConfigs,
} = bitbondState;

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

test('privacy category codes include only shareable abstract activity categories', () => {
  assert.deepEqual(privacyCategoryCodes, [
    'short_video',
    'watching_show',
    'reading',
    'music',
    'gaming',
    'social',
    'online',
    'resting',
  ]);
  assert.equal(privacyCategoryCodes.includes('offline'), false);
  assert.equal(privacyCategoryCodes.includes('paused'), false);
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

test('applyPartnerStatusResult shows partner paused without leaking app details', () => {
  const state = applyPartnerStatusResult(
    createFallbackBridgeState(),
    JSON.stringify({
      ok: true,
      data: {
        partnerStatus: {
          paired: true,
          partner: {
            nickname: '小禾',
          },
          statusCode: 'reading',
          statusUpdatedAt: '刚刚',
          isPaused: true,
          appName: 'PrivateApp',
          content: 'Private reading title',
        },
      },
      token: 'paused-token-secret',
    }),
  );
  const viewModel = buildHomeStatusViewModel(state);

  assert.equal(state.partner.statusCode, 'paused');
  assert.equal(state.partner.statusText, '已暂停共享');
  assert.equal(viewModel.state, 'partner_paused');
  assert.match(viewModel.message, /对方已暂停共享/);
  assertPrivateFieldsAbsent(state);
  assertSerializedValuesAbsent(state, ['PrivateApp', 'Private reading title', 'paused-token-secret']);
});

test('applyPartnerStatusResult clears old timestamp when partner is offline', () => {
  const fallback = createFallbackBridgeState();
  const current = {
    ...fallback,
    partner: {
      ...fallback.partner,
      statusCode: 'music',
      statusText: '正在听歌',
      updatedAt: '2026-05-26T08:30:00.000Z',
    },
  };

  const state = applyPartnerStatusResult(
    current,
    JSON.stringify({
      ok: true,
      data: {
        partnerStatus: {
          paired: true,
          partner: {
            nickname: '小禾',
          },
          statusCode: 'offline',
          statusUpdatedAt: null,
          expiresAt: null,
          isPaused: false,
        },
      },
    }),
  );

  assert.equal(state.partner.statusCode, 'offline');
  assert.equal(state.partner.updatedAt, '');
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

test('home status view model exposes unpaired and network empty states', () => {
  const unpaired = {
    ...createFallbackBridgeState(),
    pair: {
      paired: false,
      nickname: '',
    },
  };
  const networkError = {
    ...createFallbackBridgeState(),
    notice: {
      kind: 'error',
      message: '状态同步失败，请稍后再试',
    },
  };

  assert.equal(buildHomeStatusViewModel(unpaired).state, 'unpaired');
  assert.match(buildHomeStatusViewModel(unpaired).message, /还没有配对对象/);
  assert.equal(buildHomeStatusViewModel(networkError).state, 'network_error');
  assert.match(buildHomeStatusViewModel(networkError).message, /网络暂时不可用/);
});

test('pause and resume sharing results update only self sharing state', () => {
  const paused = applyPauseSharingResult(
    createFallbackBridgeState(),
    JSON.stringify({
      ok: true,
      data: {
        sharing: false,
        statusText: '已暂停共享',
        appName: 'PrivateApp',
      },
      token: 'pause-token-secret',
    }),
  );
  const resumed = applyResumeSharingResult(
    paused,
    JSON.stringify({
      ok: true,
      data: {
        sharing: true,
        statusText: '你正在共享抽象状态',
        packageName: 'com.example.private',
      },
      token: 'resume-token-secret',
    }),
  );

  assert.equal(paused.self.sharing, false);
  assert.equal(paused.self.statusText, '已暂停共享');
  assert.equal(paused.notice.kind, 'success');
  assert.equal(resumed.self.sharing, true);
  assert.equal(resumed.self.statusText, '你正在共享抽象状态');
  assert.equal(buildSharingViewModel(paused).primaryAction.bridgeMethod, 'resumeSharing');
  assert.equal(buildSharingViewModel(resumed).primaryAction.bridgeMethod, 'pauseSharing');
  assertPrivateFieldsAbsent(resumed);
  assertSerializedValuesAbsent(resumed, [
    'PrivateApp',
    'com.example.private',
    'pause-token-secret',
    'resume-token-secret',
  ]);
});

test('privacy settings result sanitizes categories and exposes toggle actions', () => {
  const result = applyPrivacySettingsResult(
    { enabledCategories: ['music'] },
    JSON.stringify({
      ok: true,
      data: {
        enabledCategories: ['paused', 'reading', 'offline', 'music'],
        packageName: 'com.example.private',
      },
      token: 'privacy-token-secret',
    }),
  );
  const viewModel = buildPrivacySettingsViewModel(result.settings);

  assert.deepEqual(result.settings.enabledCategories, ['reading', 'music']);
  assert.equal(result.notice.kind, 'success');
  assert.deepEqual(
    viewModel.categories.map((category) => category.code),
    privacyCategoryCodes,
  );
  assert.deepEqual(
    viewModel.categories.filter((category) => category.enabled).map((category) => category.code),
    ['reading', 'music'],
  );
  assert.deepEqual(
    viewModel.actions.map((action) => action.bridgeMethod),
    ['getPrivacySettings', 'updatePrivacySettings'],
  );
  assertSerializedValuesAbsent(result, ['com.example.private', 'privacy-token-secret', 'offline', 'paused']);
});

test('privacy settings accepts Android allowedStatuses and update payload emits only allowedStatuses', () => {
  const result = applyPrivacySettingsResult(
    { enabledCategories: ['music'] },
    JSON.stringify({
      ok: true,
      data: {
        allowedStatuses: ['paused', 'reading', 'offline', 'gaming'],
      },
    }),
  );
  const payload = buildPrivacySettingsUpdatePayload(['paused', 'reading', 'offline', 'gaming'], {
    source: 'bitbond_web_privacy',
    requestedAt: '2026-05-27T09:00:00.000Z',
  });
  const parsedPayload = JSON.parse(payload);

  assert.deepEqual(result.settings.enabledCategories, ['reading', 'gaming']);
  assert.deepEqual(parsedPayload, {
    allowedStatuses: ['reading', 'gaming'],
    source: 'bitbond_web_privacy',
    requestedAt: '2026-05-27T09:00:00.000Z',
  });
  assert.equal('enabledCategories' in parsedPayload, false);
  assert.equal(parsedPayload.allowedStatuses.includes('offline'), false);
  assert.equal(parsedPayload.allowedStatuses.includes('paused'), false);
});

test('latest interactions keeps unread hearts without storing private fields', () => {
  const state = applyLatestInteractionsResult(
    createFallbackBridgeState(),
    JSON.stringify({
      ok: true,
      data: {
        interactions: [
          {
            id: 'heart-1',
            type: 'heart',
            createdAt: '2026-05-27T08:00:00.000Z',
            seen: false,
            fromUserId: 'private-user-id',
            access_token: 'interaction-access-secret',
            message: 'private note',
          },
          {
            id: 'note-1',
            type: 'message',
            content: 'private message',
          },
        ],
      },
      token: 'interaction-token-secret',
    }),
  );
  const viewModel = buildInteractionsViewModel(state);
  const seen = applyMarkInteractionsSeenResult(
    state,
    JSON.stringify({
      ok: true,
      data: {
        seen: true,
      },
      token: 'seen-token-secret',
    }),
  );

  assert.equal(state.interactions.unreadCount, 1);
  assert.deepEqual(state.interactions.items, [
    {
      id: 'heart-1',
      type: 'heart',
      createdAt: '2026-05-27T08:00:00.000Z',
      seen: false,
    },
  ]);
  assert.equal(viewModel.hasUnreadHeart, true);
  assert.deepEqual(viewModel.unreadInteractionIds, ['heart-1']);
  assert.equal(seen.interactions.unreadCount, 0);
  assert.equal(seen.interactions.items[0].seen, true);
  assertPrivateFieldsAbsent(seen);
  assertSerializedValuesAbsent(seen, [
    'private-user-id',
    'interaction-access-secret',
    'private note',
    'private message',
    'interaction-token-secret',
    'seen-token-secret',
  ]);
});

test('latest interactions accepts Android interactionId wire key', () => {
  const state = applyLatestInteractionsResult(
    createFallbackBridgeState(),
    JSON.stringify({
      ok: true,
      data: {
        interactions: [
          {
            interactionId: 'heart-android-1',
            type: 'heart',
            createdAt: '2026-05-27T08:10:00.000Z',
            seen: false,
            packageName: 'com.example.private',
          },
        ],
      },
      token: 'android-interaction-token-secret',
    }),
  );
  const viewModel = buildInteractionsViewModel(state);

  assert.equal(state.interactions.unreadCount, 1);
  assert.deepEqual(state.interactions.items, [
    {
      id: 'heart-android-1',
      type: 'heart',
      createdAt: '2026-05-27T08:10:00.000Z',
      seen: false,
    },
  ]);
  assert.deepEqual(viewModel.unreadInteractionIds, ['heart-android-1']);
  assertSerializedValuesAbsent(state, [
    'interactionId',
    'com.example.private',
    'android-interaction-token-secret',
  ]);
});

test('delete account result clears local pair and partner only after bridge confirms', () => {
  const initial = {
    ...createFallbackBridgeState(),
    pair: {
      paired: true,
      nickname: '小禾',
      inviteCode: 'PAIR-2468',
      coupleId: 'couple-public-id',
    },
  };
  const rejected = applyDeleteAccountResult(initial, JSON.stringify({ ok: false, token: 'delete-rejected-token' }));
  const deleted = applyDeleteAccountResult(
    initial,
    JSON.stringify({
      ok: true,
      data: {
        deleted: true,
        packageName: 'com.example.private',
      },
      token: 'delete-token-secret',
    }),
  );

  assert.equal(rejected.pair.paired, true);
  assert.equal(rejected.notice.kind, 'error');
  assert.equal(deleted.pair.paired, false);
  assert.equal(deleted.pair.nickname, '');
  assert.equal(deleted.partner.statusCode, 'offline');
  assert.equal(deleted.partner.updatedAt, '账号已删除');
  assert.equal(deleted.self.sharing, false);
  assert.equal(deleted.notice.kind, 'success');
  assertPrivateFieldsAbsent(deleted);
  assertSerializedValuesAbsent(deleted, ['com.example.private', 'delete-token-secret']);
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

test('debug foreground sanitizer and view model never expose private bridge fields when enabled', () => {
  const rawDebugData = {
    enabled: true,
    code: 'debug_foreground',
    packageName: 'com.example.private',
    appName: 'PrivateApp',
    usageDuration: 3600,
    content: 'Private content',
    chatTarget: 'Alice',
    token: 'debug-token-secret',
    access_token: 'debug-access-secret',
    refresh_token: 'debug-refresh-secret',
  };
  const sanitized = sanitizeDebugForeground(rawDebugData);
  const viewModel = buildDebugViewModel(rawDebugData);

  assert.deepEqual(sanitized, { enabled: true });
  assert.equal(viewModel.enabled, true);
  assert.deepEqual(viewModel.packageFields, []);
  assertPrivateFieldsAbsent(sanitized);
  assertPrivateFieldsAbsent(viewModel);
  assertSerializedValuesAbsent(sanitized, [
    'debug_foreground',
    'com.example.private',
    'PrivateApp',
    '3600',
    'Private content',
    'Alice',
    'debug-token-secret',
    'debug-access-secret',
    'debug-refresh-secret',
  ]);
  assertSerializedValuesAbsent(viewModel, [
    'debug_foreground',
    'com.example.private',
    'PrivateApp',
    '3600',
    'Private content',
    'Alice',
    'debug-token-secret',
    'debug-access-secret',
    'debug-refresh-secret',
  ]);
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

test('permission view model shows both setup actions when no status sync permission is enabled', () => {
  const viewModel = buildPermissionViewModel({
    hasUsageAccess: false,
    hasAccessibilityAccess: false,
    isIgnoringBatteryOptimizations: false,
  });

  assert.equal(viewModel.hasUsageAccess, false);
  assert.equal(viewModel.hasAccessibilityAccess, false);
  assert.equal(viewModel.isIgnoringBatteryOptimizations, false);
  assert.equal(viewModel.mode, 'missing');
  assert.deepEqual(
    viewModel.actions.map((action) => action.bridgeMethod),
    [
      'checkUsageAccess',
      'checkAccessibilityAccess',
      'checkBatteryOptimization',
      'openUsageAccessSettings',
      'openAccessibilitySettings',
      'openBatteryOptimizationSettings',
    ],
  );
});

test('permission view model treats usage access as polling fallback when accessibility is disabled', () => {
  const viewModel = buildPermissionViewModel({
    hasUsageAccess: true,
    hasAccessibilityAccess: false,
    isIgnoringBatteryOptimizations: false,
  });

  assert.equal(viewModel.mode, 'polling');
  assert.equal(viewModel.isIgnoringBatteryOptimizations, false);
  assert.match(viewModel.description, /轮询/);
  assert.deepEqual(
    viewModel.actions.map((action) => action.bridgeMethod),
    [
      'checkUsageAccess',
      'checkAccessibilityAccess',
      'checkBatteryOptimization',
      'openAccessibilitySettings',
      'openBatteryOptimizationSettings',
    ],
  );
});

test('permission view model prefers accessibility event mode when enabled', () => {
  const viewModel = buildPermissionViewModel({
    hasUsageAccess: false,
    hasAccessibilityAccess: true,
    isIgnoringBatteryOptimizations: false,
  });

  assert.equal(viewModel.mode, 'accessibility');
  assert.match(viewModel.description, /不读取屏幕内容/);
  assert.deepEqual(
    viewModel.actions.map((action) => action.bridgeMethod),
    [
      'checkUsageAccess',
      'checkAccessibilityAccess',
      'checkBatteryOptimization',
      'openBatteryOptimizationSettings',
    ],
  );
});

test('pairing view model exposes create and accept actions when unpaired', () => {
  const state = {
    ...createFallbackBridgeState(),
    pair: {
      paired: false,
      nickname: '',
      inviteCode: '',
    },
  };
  const viewModel = buildPairingViewModel(state);

  assert.equal(viewModel.isPaired, false);
  assert.deepEqual(
    viewModel.actions.map((action) => action.bridgeMethod),
    ['createPairInvite', 'acceptPairInvite'],
  );
});

test('avatar view model exposes 8 avatar choices', () => {
  const viewModel = buildAvatarViewModel({ selectedAvatar: 'cat' });

  assert.equal(viewModel.avatarChoices.length, 8);
  assert.deepEqual(
    viewModel.avatarChoices.map((avatar) => avatar.id),
    ['cat', 'dog', 'rabbit', 'bear', 'fox', 'panda', 'penguin', 'duck'],
  );
  assert.deepEqual(
    viewModel.avatarChoices.map((avatar) => avatar.name),
    ['小猫', '小狗', '小兔', '小熊', '小狐', '熊猫', '企鹅', '小鸭'],
  );
  assert.deepEqual(
    viewModel.avatarChoices.map((avatar) => avatar.assetKey),
    [
      'avatars/cat',
      'avatars/dog',
      'avatars/rabbit',
      'avatars/bear',
      'avatars/fox',
      'avatars/panda',
      'avatars/penguin',
      'avatars/duck',
    ],
  );
});

test('settings view model exposes unlink, pause, resume, and delete account actions', () => {
  const viewModel = buildSettingsViewModel({
    ...createFallbackBridgeState(),
    pair: {
      paired: true,
      nickname: '小禾',
    },
  });

  const bridgeMethods = viewModel.actions.map((action) => action.bridgeMethod);

  assert.ok(bridgeMethods.includes('unlink'));
  assert.ok(bridgeMethods.includes('pauseSharing'));
  assert.ok(bridgeMethods.includes('resumeSharing'));
  assert.ok(bridgeMethods.includes('deleteAccount'));
});

test('debug view model hides package fields when disabled', () => {
  const viewModel = buildDebugViewModel({
    enabled: false,
    packageName: 'com.example.private',
    appName: 'PrivateApp',
  });

  assert.equal(viewModel.enabled, false);
  assert.equal('packageName' in viewModel, false);
  assert.equal('appName' in viewModel, false);
  assert.equal('usageDuration' in viewModel, false);
  assert.deepEqual(viewModel.packageFields, []);
  assert.deepEqual(
    viewModel.actions.map((action) => action.bridgeMethod),
    ['getDebugForegroundApp'],
  );
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
