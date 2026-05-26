export const statusConfigs = [
  {
    code: 'short_video',
    label: '短视频',
    statusText: '正在刷短视频',
    areaLabel: '沙发角',
    propFile: 'prop_short_video_phone.png',
    roomCandidates: ['sofa_corner', 'window_seat'],
    propFrame: 0,
    movePhase: { name: 'move', sprite: 'walk', durationMs: 900 },
    actionPhase: { name: 'action', sprite: 'watch_phone', durationMs: 1400 },
  },
  {
    code: 'watching_show',
    label: '追剧',
    statusText: '正在追剧',
    areaLabel: '电视旁',
    propFile: 'prop_watching_show_tv.png',
    roomCandidates: ['tv_side', 'sofa_center'],
    propFrame: 1,
    movePhase: { name: 'move', sprite: 'walk', durationMs: 900 },
    actionPhase: { name: 'action', sprite: 'watch_tv', durationMs: 1600 },
  },
  {
    code: 'reading',
    label: '阅读',
    statusText: '正在阅读',
    areaLabel: '书架前',
    propFile: 'prop_reading_book.png',
    roomCandidates: ['bookshelf', 'desk'],
    propFrame: 2,
    movePhase: { name: 'move', sprite: 'walk', durationMs: 900 },
    actionPhase: { name: 'action', sprite: 'read', durationMs: 1500 },
  },
  {
    code: 'music',
    label: '音乐',
    statusText: '正在听音乐',
    areaLabel: '音响旁',
    propFile: 'prop_music_headphone.png',
    roomCandidates: ['speaker_side', 'rug_center'],
    propFrame: 3,
    movePhase: { name: 'move', sprite: 'walk', durationMs: 900 },
    actionPhase: { name: 'action', sprite: 'listen_music', durationMs: 1500 },
  },
  {
    code: 'gaming',
    label: '游戏',
    statusText: '正在游戏',
    areaLabel: '地毯边',
    propFile: 'prop_gaming_gamepad.png',
    roomCandidates: ['rug_edge', 'desk'],
    propFrame: 4,
    movePhase: { name: 'move', sprite: 'walk', durationMs: 900 },
    actionPhase: { name: 'action', sprite: 'play_game', durationMs: 1300 },
  },
  {
    code: 'social',
    label: '社交',
    statusText: '正在聊天',
    areaLabel: '窗边',
    propFile: 'prop_social_bubble.png',
    roomCandidates: ['window_side', 'sofa_corner'],
    propFrame: 5,
    movePhase: { name: 'move', sprite: 'walk', durationMs: 900 },
    actionPhase: { name: 'action', sprite: 'chat', durationMs: 1300 },
  },
  {
    code: 'online',
    label: '在线',
    statusText: '刚刚在线',
    areaLabel: '房间中央',
    propFile: 'prop_online_presence.png',
    roomCandidates: ['room_center', 'door_side'],
    propFrame: 6,
    movePhase: { name: 'move', sprite: 'walk', durationMs: 900 },
    actionPhase: { name: 'action', sprite: 'idle', durationMs: 1200 },
  },
  {
    code: 'resting',
    label: '休息',
    statusText: '正在休息',
    areaLabel: '床边',
    propFile: 'prop_resting_moon.png',
    roomCandidates: ['bed_side', 'window_seat'],
    propFrame: 7,
    movePhase: { name: 'move', sprite: 'walk', durationMs: 900 },
    actionPhase: { name: 'action', sprite: 'rest', durationMs: 1700 },
  },
  {
    code: 'offline',
    label: '离线',
    statusText: '暂时离线',
    areaLabel: '门口',
    propFile: 'prop_offline_cloud.png',
    roomCandidates: ['doorway', 'room_center'],
    propFrame: 8,
    movePhase: { name: 'move', sprite: 'walk', durationMs: 900 },
    actionPhase: { name: 'action', sprite: 'idle', durationMs: 1200 },
  },
  {
    code: 'paused',
    label: '已暂停',
    statusText: '已暂停共享',
    areaLabel: '隐私模式',
    propFile: 'prop_paused_badge.png',
    roomCandidates: ['privacy_corner', 'bed_side'],
    propFrame: 9,
    movePhase: { name: 'move', sprite: 'walk', durationMs: 900 },
    actionPhase: { name: 'action', sprite: 'idle', durationMs: 1200 },
  },
];

const avatarChoicesSeed = [
  { id: 'cat', name: '小猫', assetKey: 'avatars/cat' },
  { id: 'dog', name: '小狗', assetKey: 'avatars/dog' },
  { id: 'rabbit', name: '小兔', assetKey: 'avatars/rabbit' },
  { id: 'bear', name: '小熊', assetKey: 'avatars/bear' },
  { id: 'fox', name: '小狐', assetKey: 'avatars/fox' },
  { id: 'panda', name: '熊猫', assetKey: 'avatars/panda' },
  { id: 'penguin', name: '企鹅', assetKey: 'avatars/penguin' },
  { id: 'duck', name: '小鸭', assetKey: 'avatars/duck' },
];

export function getStatusConfig(code) {
  return statusConfigs.find((status) => status.code === code) ?? statusConfigs[6];
}

export function createFallbackBridgeState() {
  const music = getStatusConfig('music');

  return {
    bridge: {
      ready: false,
      message: '浏览器预览模式',
    },
    self: {
      sharing: true,
      statusText: '你正在共享抽象状态',
    },
    pair: {
      paired: true,
      nickname: '小禾',
    },
    partner: {
      statusCode: music.code,
      statusText: music.statusText,
      updatedAt: '刚刚',
      areaLabel: music.areaLabel,
    },
    notice: {
      kind: 'idle',
      message: '',
    },
  };
}

export function buildInitialState(rawBridgeJson) {
  const fallback = createFallbackBridgeState();
  const parsed = parseBridgeJson(rawBridgeJson);

  if (!parsed) {
    return fallback;
  }

  return {
    bridge: {
      ...fallback.bridge,
      ...pickTypedObject(parsed.bridge, { ready: 'boolean', message: 'string' }),
    },
    self: {
      ...fallback.self,
      ...pickTypedObject(parsed.self, { sharing: 'boolean', statusText: 'string' }),
    },
    pair: {
      ...fallback.pair,
      ...pickTypedObject(parsed.pair, { paired: 'boolean', nickname: 'string' }),
    },
    partner: sanitizePartner(parsed.partner, fallback.partner),
    notice: fallback.notice,
  };
}

export function applyPairInviteResult(currentState, rawBridgeJson) {
  const parsed = parseBridgeJson(rawBridgeJson);

  if (parsed?.ok !== true) {
    return {
      ...currentState,
      notice: {
        kind: 'error',
        message: '生成配对码失败，请稍后再试',
      },
    };
  }

  const data = pickDataObject(parsed);
  const inviteCode = typeof data.code === 'string' ? data.code : '';

  return {
    ...currentState,
    pair: {
      ...currentState.pair,
      inviteCode,
      expiresAt: typeof data.expiresAt === 'string' ? data.expiresAt : '',
    },
    notice: {
      kind: 'success',
      message: '配对码已生成',
    },
  };
}

export function applyAcceptInviteResult(currentState, rawBridgeJson) {
  const parsed = parseBridgeJson(rawBridgeJson);
  const data = pickDataObject(parsed);

  if (parsed?.ok !== true || data.paired !== true) {
    return {
      ...currentState,
      notice: {
        kind: 'error',
        message: '接受邀请失败，请确认配对码后重试',
      },
    };
  }

  const partner = pickObject(data.partner);
  const nickname = typeof partner.nickname === 'string' ? partner.nickname : currentState.pair.nickname;

  return {
    ...currentState,
    pair: {
      ...currentState.pair,
      paired: true,
      nickname,
      coupleId: typeof data.coupleId === 'string' ? data.coupleId : currentState.pair.coupleId,
      inviteCode: '',
      expiresAt: undefined,
    },
    notice: {
      kind: 'success',
      message: '已完成配对',
    },
  };
}

export function applyUnlinkResult(currentState, rawBridgeJson) {
  const parsed = parseBridgeJson(rawBridgeJson);

  if (parsed?.ok !== true) {
    return {
      ...currentState,
      notice: {
        kind: 'error',
        message: '解除配对失败，请稍后再试',
      },
    };
  }

  const offline = getStatusConfig('offline');

  return {
    ...currentState,
    pair: {
      ...currentState.pair,
      paired: false,
      nickname: '',
      inviteCode: '',
      expiresAt: '',
      coupleId: '',
    },
    partner: {
      statusCode: offline.code,
      statusText: offline.statusText,
      updatedAt: '已解除配对',
      areaLabel: offline.areaLabel,
    },
    notice: {
      kind: 'success',
      message: '已解除配对',
    },
  };
}

export function applyAvatarSelectionResult(currentState, rawBridgeJson) {
  const parsed = parseBridgeJson(rawBridgeJson);
  const data = pickDataObject(parsed);

  if (parsed?.ok !== true || typeof data.avatarId !== 'string') {
    return {
      ...currentState,
      notice: {
        kind: 'error',
        message: '头像更新失败，请稍后再试',
      },
    };
  }

  return {
    ...currentState,
    self: {
      ...currentState.self,
      selectedAvatar: data.avatarId,
    },
    notice: {
      kind: 'success',
      message: '头像已更新',
    },
  };
}

export function applyPartnerStatusResult(currentState, rawBridgeJson) {
  const parsed = parseBridgeJson(rawBridgeJson);

  if (parsed?.ok !== true) {
    return {
      ...currentState,
      notice: {
        kind: 'error',
        message: '状态同步失败，请稍后再试',
      },
    };
  }

  const data = pickDataObject(parsed);
  const partnerStatus = pickObject(data.partnerStatus);
  const partner = pickObject(partnerStatus.partner);

  if (partnerStatus.paired === false) {
    const offline = getStatusConfig('offline');

    return {
      ...currentState,
      pair: {
        ...currentState.pair,
        paired: false,
        nickname: '',
        inviteCode: '',
        expiresAt: '',
        coupleId: '',
      },
      partner: {
        statusCode: offline.code,
        statusText: offline.statusText,
        updatedAt: '未配对',
        areaLabel: offline.areaLabel,
      },
      notice: {
        kind: 'success',
        message: '状态已更新',
      },
    };
  }

  const statusCode =
    partnerStatus.isPaused === true
      ? 'paused'
      : typeof partnerStatus.statusCode === 'string'
        ? partnerStatus.statusCode
        : currentState.partner?.statusCode;
  const updatedAt =
    typeof partnerStatus.statusUpdatedAt === 'string'
      ? partnerStatus.statusUpdatedAt
      : partnerStatus.updatedAt;

  return {
    ...currentState,
    pair: {
      ...currentState.pair,
      paired: typeof partnerStatus.paired === 'boolean' ? partnerStatus.paired : currentState.pair.paired,
      nickname: typeof partner.nickname === 'string' ? partner.nickname : currentState.pair.nickname,
    },
    partner: sanitizePartner(
      {
        statusCode,
        updatedAt,
      },
      currentState.partner,
    ),
    notice: {
      kind: 'success',
      message: '状态已更新',
    },
  };
}

export function applyDebugForegroundResult(currentState, rawBridgeJson) {
  const parsed = parseBridgeJson(rawBridgeJson);
  const data = pickDataObject(parsed);

  if (parsed?.ok !== true) {
    return {
      ...currentState,
      notice: {
        kind: 'error',
        message: '调试前台状态读取失败',
      },
    };
  }

  return {
    ...currentState,
    debugForeground: {
      enabled: data.enabled === true,
    },
  };
}

export function getRoomPresentation(statusCode) {
  const status = getStatusConfig(statusCode);

  return {
    statusCode: status.code,
    roomCandidates: [...status.roomCandidates],
    propFrame: status.propFrame,
    movePhase: { ...status.movePhase },
    actionPhase: { ...status.actionPhase },
  };
}

export function advanceRoomMotionPhase(roomMotion, motionResult = {}) {
  const currentMotion = roomMotion && typeof roomMotion === 'object' ? roomMotion : { phase: 'move' };

  if (currentMotion.phase === 'move' && motionResult.movementComplete === true) {
    return {
      ...currentMotion,
      phase: 'action',
    };
  }

  return {
    ...currentMotion,
  };
}

export function buildPermissionViewModel(usageAccess) {
  const access = pickObject(usageAccess);
  const hasUsageAccess = access.hasUsageAccess === true;
  const hasAccessibilityAccess = access.hasAccessibilityAccess === true;
  const isIgnoringBatteryOptimizations = access.isIgnoringBatteryOptimizations === true;
  const mode = hasAccessibilityAccess ? 'accessibility' : hasUsageAccess ? 'polling' : 'missing';

  return {
    hasUsageAccess,
    hasAccessibilityAccess,
    isIgnoringBatteryOptimizations,
    mode,
    title:
      mode === 'accessibility'
        ? '实时刷新已开启'
        : mode === 'polling'
          ? '轮询刷新已开启'
          : '需要开启状态刷新权限',
    description:
      mode === 'accessibility'
        ? '切换应用会通过无障碍事件触发上传；只读取应用包名，不读取屏幕内容。'
        : mode === 'polling'
          ? '未开启无障碍时，BitBond 会尽量用使用情况访问进行后台轮询；部分系统可能延迟。'
          : '至少开启使用情况访问；需要更及时刷新时，可以开启无障碍事件模式。',
    actions: [
      {
        id: 'check-usage-access',
        label: '检查权限',
        bridgeMethod: 'checkUsageAccess',
        kind: 'secondary',
      },
      {
        id: 'check-accessibility-access',
        label: '检查无障碍',
        bridgeMethod: 'checkAccessibilityAccess',
        kind: 'secondary',
      },
      {
        id: 'check-battery-optimization',
        label: '检查电池优化',
        bridgeMethod: 'checkBatteryOptimization',
        kind: 'secondary',
      },
      ...(
        hasUsageAccess || hasAccessibilityAccess
          ? []
          : [
              {
                id: 'open-usage-access-settings',
                label: '打开系统设置',
                bridgeMethod: 'openUsageAccessSettings',
                kind: 'primary',
              },
            ]
      ),
      ...(
        hasAccessibilityAccess
          ? []
          : [
              {
                id: 'open-accessibility-settings',
                label: '打开无障碍设置',
                bridgeMethod: 'openAccessibilitySettings',
                kind: mode === 'missing' ? 'primary' : 'secondary',
              },
            ]
      ),
      ...(
        isIgnoringBatteryOptimizations
          ? []
          : [
              {
                id: 'open-battery-optimization-settings',
                label: '打开电池优化设置',
                bridgeMethod: 'openBatteryOptimizationSettings',
                kind: 'secondary',
              },
            ]
      ),
    ],
  };
}

export function buildPairingViewModel(state) {
  const pair = pickObject(state?.pair);
  const isPaired = pair.paired === true;
  const partnerName = isPaired ? pair.nickname || '对方' : '未配对';

  return {
    isPaired,
    partnerName,
    inviteCode: typeof pair.inviteCode === 'string' ? pair.inviteCode : '',
    expiresAt: typeof pair.expiresAt === 'string' ? pair.expiresAt : '',
    actions: isPaired
      ? [
          {
            id: 'refresh-partner-status',
            label: '刷新伴侣状态',
            bridgeMethod: 'refreshPartnerStatus',
            kind: 'primary',
          },
        ]
      : [
          {
            id: 'create-pair-invite',
            label: '生成配对码',
            bridgeMethod: 'createPairInvite',
            kind: 'primary',
          },
          {
            id: 'accept-pair-invite',
            label: '接受配对码',
            bridgeMethod: 'acceptPairInvite',
            kind: 'secondary',
          },
        ],
  };
}

export function buildAvatarViewModel(options = {}) {
  const source = pickObject(options);
  const selectedAvatar = normalizeAvatarId(source.selectedAvatar ?? source.avatarId ?? source.self?.selectedAvatar);
  const avatarChoices = sanitizeAvatarChoices(source.avatars);

  return {
    selectedAvatar,
    avatarChoices: avatarChoices.map((avatar) => ({
      ...avatar,
      selected: avatar.id === selectedAvatar,
    })),
    actions: [
      {
        id: 'list-avatars',
        label: '刷新头像',
        bridgeMethod: 'listAvatars',
        kind: 'secondary',
      },
      {
        id: 'select-avatar',
        label: '选择头像',
        bridgeMethod: 'selectAvatar',
        kind: 'primary',
      },
    ],
  };
}

export function buildSettingsViewModel(state) {
  const pair = pickObject(state?.pair);
  const paired = pair.paired === true;

  return {
    paired,
    partnerName: paired ? pair.nickname || '对方' : '未配对',
    actions: paired
      ? [
          {
            id: 'unlink',
            label: '解除配对',
            bridgeMethod: 'unlink',
            kind: 'danger',
          },
        ]
      : [],
  };
}

export function buildDebugViewModel(debugForeground) {
  const source = pickObject(debugForeground?.debugForeground ?? debugForeground?.data ?? debugForeground);
  const enabled = source.enabled === true;

  return {
    enabled,
    title: enabled ? '调试信息已开启' : '调试信息不可用',
    description: enabled ? '当前构建允许读取前台调试字段。' : '非调试构建会隐藏包名和应用字段。',
    packageFields: enabled ? buildDebugPackageFields(source) : [],
    actions: [
      {
        id: 'get-debug-foreground-app',
        label: '读取前台调试',
        bridgeMethod: 'getDebugForegroundApp',
        kind: 'secondary',
      },
    ],
  };
}

function sanitizePartner(partner, fallbackPartner) {
  if (!partner || typeof partner !== 'object') {
    return fallbackPartner;
  }

  const safeStatus =
    statusConfigs.find((status) => status.code === partner.statusCode) ??
    getStatusConfig(fallbackPartner.statusCode);
  const safePartner = {
    statusCode: safeStatus.code,
    statusText: safeStatus.statusText,
    updatedAt: typeof partner.updatedAt === 'string' ? partner.updatedAt : fallbackPartner.updatedAt,
    areaLabel: safeStatus.areaLabel,
  };

  return safePartner;
}

function sanitizeAvatarChoices(avatars) {
  const source = Array.isArray(avatars) && avatars.length > 0 ? avatars : avatarChoicesSeed;
  const safeChoices = source
    .map((avatar) => pickObject(avatar))
    .map((avatar) => ({
      id: normalizeAvatarId(avatar.id),
      name: typeof avatar.name === 'string' ? avatar.name : '',
      assetKey: typeof avatar.assetKey === 'string' ? avatar.assetKey : '',
    }))
    .filter((avatar) => avatar.id && avatar.name && avatar.assetKey);

  return safeChoices.length > 0 ? safeChoices : avatarChoicesSeed;
}

function normalizeAvatarId(value) {
  return typeof value === 'string' ? value.trim() : '';
}

function buildDebugPackageFields(source) {
  return [
    { key: 'code', label: '状态码', value: source.code },
    { key: 'packageName', label: '包名', value: source.packageName },
    { key: 'appName', label: '应用名', value: source.appName },
  ].filter((field) => typeof field.value === 'string' && field.value.trim());
}

function pickDataObject(parsed) {
  return pickObject(parsed?.data);
}

function pickObject(value) {
  return value && typeof value === 'object' && !Array.isArray(value) ? value : {};
}

function parseBridgeJson(rawBridgeJson) {
  if (!rawBridgeJson || typeof rawBridgeJson !== 'string') {
    return null;
  }

  try {
    const parsed = JSON.parse(rawBridgeJson);
    return parsed && typeof parsed === 'object' ? parsed : null;
  } catch {
    return null;
  }
}

function pickTypedObject(source, schema) {
  if (!source || typeof source !== 'object') {
    return {};
  }

  return Object.entries(schema).reduce((picked, [key, type]) => {
    if (key in source && typeof source[key] === type) {
      picked[key] = source[key];
    }
    return picked;
  }, {});
}
