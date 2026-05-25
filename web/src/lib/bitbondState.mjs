export const statusConfigs = [
  {
    code: 'short_video',
    label: '短视频',
    statusText: '正在刷短视频',
    areaLabel: '沙发角',
    propFile: 'prop_short_video_phone.png',
  },
  {
    code: 'watching_show',
    label: '追剧',
    statusText: '正在追剧',
    areaLabel: '电视旁',
    propFile: 'prop_watching_show_tv.png',
  },
  {
    code: 'reading',
    label: '阅读',
    statusText: '正在阅读',
    areaLabel: '书架前',
    propFile: 'prop_reading_book.png',
  },
  {
    code: 'music',
    label: '音乐',
    statusText: '正在听音乐',
    areaLabel: '音响旁',
    propFile: 'prop_music_headphone.png',
  },
  {
    code: 'gaming',
    label: '游戏',
    statusText: '正在游戏',
    areaLabel: '地毯边',
    propFile: 'prop_gaming_gamepad.png',
  },
  {
    code: 'social',
    label: '社交',
    statusText: '正在聊天',
    areaLabel: '窗边',
    propFile: 'prop_social_bubble.png',
  },
  {
    code: 'online',
    label: '在线',
    statusText: '刚刚在线',
    areaLabel: '房间中央',
    propFile: 'prop_online_presence.png',
  },
  {
    code: 'resting',
    label: '休息',
    statusText: '正在休息',
    areaLabel: '床边',
    propFile: 'prop_resting_moon.png',
  },
  {
    code: 'offline',
    label: '离线',
    statusText: '暂时离线',
    areaLabel: '门口',
    propFile: 'prop_offline_cloud.png',
  },
  {
    code: 'paused',
    label: '已暂停',
    statusText: '已暂停共享',
    areaLabel: '隐私模式',
    propFile: 'prop_paused_badge.png',
  },
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
