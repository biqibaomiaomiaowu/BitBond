<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, shallowRef, watch } from 'vue';
// @ts-expect-error bitbondState is authored as .mjs for Android/web sharing in this phase.
import * as bitbondState from './lib/bitbondState.mjs';
// @ts-expect-error bridgeClient is authored as .mjs for Android/web sharing in this phase.
import { callBridgeMethod as invokeBridgeMethod, normalizeBridgeJson } from './lib/bridgeClient.mjs';
// @ts-expect-error partnerStatusAutoRefresh is authored as .mjs for Android/web sharing in this phase.
import { createPartnerStatusAutoRefresh } from './lib/partnerStatusAutoRefresh.mjs';

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

type NoticeKind = 'idle' | 'success' | 'error';
type AppView = 'home' | 'pairing' | 'avatar' | 'permission' | 'settings' | 'info' | 'debug';
type StatusConfig = {
  code: string;
  label: string;
  statusText: string;
  areaLabel: string;
  propFile: string;
};
type BridgeState = {
  bridge: {
    ready: boolean;
    message: string;
  };
  self: {
    sharing: boolean;
    statusText: string;
    selectedAvatar?: string;
  };
  pair: {
    paired: boolean;
    nickname: string;
    inviteCode?: string;
    expiresAt?: string;
    coupleId?: string;
  };
  partner: {
    statusCode: string;
    statusText: string;
    updatedAt: string;
    areaLabel: string;
  };
  debugForeground?: {
    enabled: boolean;
  };
  notice: {
    kind: NoticeKind;
    message: string;
  };
  interactions?: {
    unreadCount: number;
    items: Array<{
      id: string;
      type: 'heart';
      createdAt: string;
      seen: boolean;
    }>;
  };
};
type PrivacySettings = {
  enabledCategories: string[];
};
type BridgeJsonValue = string | Record<string, unknown> | null | undefined;
type BitBondBridge = {
  ping?: () => BridgeJsonValue | Promise<BridgeJsonValue>;
  getInitialState?: () => BridgeJsonValue | Promise<BridgeJsonValue>;
  checkUsageAccess?: () => BridgeJsonValue | Promise<BridgeJsonValue>;
  openUsageAccessSettings?: () => BridgeJsonValue | Promise<BridgeJsonValue>;
  checkAccessibilityAccess?: () => BridgeJsonValue | Promise<BridgeJsonValue>;
  openAccessibilitySettings?: () => BridgeJsonValue | Promise<BridgeJsonValue>;
  checkBatteryOptimization?: () => BridgeJsonValue | Promise<BridgeJsonValue>;
  openBatteryOptimizationSettings?: () => BridgeJsonValue | Promise<BridgeJsonValue>;
  getDebugForegroundApp?: () => BridgeJsonValue | Promise<BridgeJsonValue>;
  createPairInvite?: () => BridgeJsonValue | Promise<BridgeJsonValue>;
  acceptPairInvite?: (payload: string) => BridgeJsonValue | Promise<BridgeJsonValue>;
  unlink?: (payload?: string) => BridgeJsonValue | Promise<BridgeJsonValue>;
  listAvatars?: () => BridgeJsonValue | Promise<BridgeJsonValue>;
  selectAvatar?: (payload: string) => BridgeJsonValue | Promise<BridgeJsonValue>;
  refreshPartnerStatus?: () => BridgeJsonValue | Promise<BridgeJsonValue>;
  uploadCurrentStatus?: () => BridgeJsonValue | Promise<BridgeJsonValue>;
  pauseSharing?: () => BridgeJsonValue | Promise<BridgeJsonValue>;
  resumeSharing?: () => BridgeJsonValue | Promise<BridgeJsonValue>;
  getPrivacySettings?: () => BridgeJsonValue | Promise<BridgeJsonValue>;
  updatePrivacySettings?: (payload: string) => BridgeJsonValue | Promise<BridgeJsonValue>;
  sendHeart?: () => BridgeJsonValue | Promise<BridgeJsonValue>;
  getLatestInteractions?: () => BridgeJsonValue | Promise<BridgeJsonValue>;
  markInteractionsSeen?: (payload: string) => BridgeJsonValue | Promise<BridgeJsonValue>;
  deleteAccount?: (payload: string) => BridgeJsonValue | Promise<BridgeJsonValue>;
  recordAnalyticsEvent?: (payload: string) => BridgeJsonValue | Promise<BridgeJsonValue>;
};
type BridgeCallableName = Exclude<keyof BitBondBridge, 'ping' | 'getInitialState'>;
type RoomMotion = {
  statusCode: string;
  phase: 'move' | 'action';
  roomPosition: string;
};

declare global {
  interface Window {
    BitBondBridge?: BitBondBridge;
  }
}

const visibleStatusConfigs = statusConfigs as StatusConfig[];
const navItems: Array<{ id: AppView; label: string }> = [
  { id: 'home', label: '房间' },
  { id: 'pairing', label: '配对' },
  { id: 'avatar', label: '头像' },
  { id: 'permission', label: '权限' },
  { id: 'settings', label: '设置' },
  { id: 'info', label: '说明' },
  { id: 'debug', label: '调试' },
];
const viewTitles: Record<AppView, string> = {
  home: '伴侣小房间',
  pairing: '配对',
  avatar: '头像',
  permission: '使用权限',
  settings: '设置',
  info: '隐私说明',
  debug: '调试',
};
const assetPaths = {
  room: './pixel/room/room_main.png',
  catWalk: './pixel/avatars/avatar_cat_walk_down_strip.png',
  catAction: './pixel/avatars/avatar_cat_listen_music_strip.png',
  statusProps: './pixel/status_props/status_props_sheet.png',
};

const state = ref<BridgeState>(createFallbackBridgeState());
const activeView = shallowRef<AppView>('home');
const statusAccess = ref({
  hasUsageAccess: false,
  hasAccessibilityAccess: false,
  isIgnoringBatteryOptimizations: false,
});
const privacySettings = ref<PrivacySettings>({
  enabledCategories: [...(privacyCategoryCodes as string[])],
});
const bridgeAvatars = ref<Array<Record<string, unknown>>>([]);
const debugForeground = ref<Record<string, unknown>>({ enabled: false });
const acceptCode = shallowRef('');
const localMessage = shallowRef('');
const unlinkStep = shallowRef<'idle' | 'confirm' | 'done'>('idle');
const deleteAccountStep = shallowRef<'idle' | 'confirm' | 'done'>('idle');
const roomMotion = ref<RoomMotion>({
  statusCode: state.value.partner.statusCode,
  phase: 'action',
  roomPosition: 'speaker_side',
});
const roomPositionCursor = shallowRef(0);
const refreshing = shallowRef(false);
const uploading = shallowRef(false);
const pairingBusy = shallowRef<'idle' | 'create' | 'accept'>('idle');
const avatarBusy = shallowRef<'idle' | 'list' | 'select'>('idle');
const permissionBusy = shallowRef<'idle' | 'check' | 'open-usage' | 'open-accessibility' | 'open-battery'>('idle');
const privacyBusy = shallowRef<'idle' | 'load' | 'update'>('idle');
const debugBusy = shallowRef(false);
const unlinking = shallowRef(false);
const sharingBusy = shallowRef(false);
const heartBusy = shallowRef(false);
const interactionsBusy = shallowRef<'idle' | 'load' | 'mark'>('idle');
const deletingAccount = shallowRef(false);
let roomMotionTimer: number | undefined;
let partnerStatusAutoRefresh:
  | {
      attach: () => void;
      detach: () => void;
      sync: () => void;
    }
  | undefined;

const currentStatus = computed(
  () => visibleStatusConfigs.find((item) => item.code === state.value.partner.statusCode) ?? visibleStatusConfigs[6],
);
const bridgeTone = computed(() => (state.value.bridge.ready ? 'ready' : 'preview'));
const bridgeLabel = computed(() => (state.value.bridge.ready ? 'Bridge ready' : 'Preview mode'));
const viewTitle = computed(() => viewTitles[activeView.value]);
const noticeMessage = computed(() => state.value.notice.message || localMessage.value);
const noticeKind = computed(() => state.value.notice.kind || 'idle');
const roomPresentation = computed(() => getRoomPresentation(state.value.partner.statusCode));
const permissionViewModel = computed(() => buildPermissionViewModel(statusAccess.value));
const pairingViewModel = computed(() => buildPairingViewModel(state.value));
const avatarViewModel = computed(() =>
  buildAvatarViewModel({
    selectedAvatar: state.value.self.selectedAvatar,
    avatars: bridgeAvatars.value,
  }),
);
const settingsViewModel = computed(() => buildSettingsViewModel(state.value));
const sharingViewModel = computed(() => buildSharingViewModel(state.value));
const privacySettingsViewModel = computed(() => buildPrivacySettingsViewModel(privacySettings.value));
const interactionsViewModel = computed(() => buildInteractionsViewModel(state.value));
const homeStatusViewModel = computed(() => buildHomeStatusViewModel(state.value));
const debugViewModel = computed(() => buildDebugViewModel(debugForeground.value));
const partnerName = computed(() => pairingViewModel.value.partnerName);
const selectedAvatarName = computed(() => {
  const selected = avatarViewModel.value.avatarChoices.find((avatar: { selected?: boolean }) => avatar.selected);
  return selected?.name ?? '未选择';
});
const roomAvatarStyle = computed(() => ({
  backgroundImage: `url(${roomMotion.value.phase === 'move' ? assetPaths.catWalk : assetPaths.catAction})`,
}));
const statusPropStyle = computed(() => ({
  backgroundImage: `url(${assetPaths.statusProps})`,
  backgroundPosition: `${roomPresentation.value.propFrame * 11.1111}% 0`,
}));
const roomSpriteLabel = computed(() =>
  roomMotion.value.phase === 'move'
    ? `${partnerName.value}正在移动到${state.value.partner.areaLabel}`
    : `${partnerName.value}${currentStatus.value.statusText}`,
);

onMounted(async () => {
  await initializeBridge();
  const autoRefresh = createPartnerStatusAutoRefresh({
    windowObject: window,
    documentObject: document,
    getBridgeReady: () => state.value.bridge.ready,
    getPaired: () => state.value.pair.paired,
    refresh: refreshPartnerStatus,
  });
  partnerStatusAutoRefresh = autoRefresh;
  autoRefresh.attach();
  void checkStatusAccess({ silent: true });
  void listAvatars({ silent: true });
  void loadPrivacySettings({ silent: true });
  void loadLatestInteractions({ silent: true });
});

onUnmounted(() => {
  partnerStatusAutoRefresh?.detach();
  partnerStatusAutoRefresh = undefined;
  clearRoomMotionTimer();
});

watch(
  () => state.value.partner.statusCode,
  (statusCode, previousStatusCode) => {
    startRoomMotion(statusCode, previousStatusCode !== undefined);
  },
  { immediate: true },
);

watch(
  () => [state.value.bridge.ready, state.value.pair.paired] as const,
  () => {
    partnerStatusAutoRefresh?.sync();
  },
);

async function initializeBridge() {
  const bridge = getBridge();

  if (!bridge || (typeof bridge.ping !== 'function' && typeof bridge.getInitialState !== 'function')) {
    state.value = createFallbackBridgeState();
    return;
  }

  let pingMessage = 'Android bridge connected';
  let bridgeReady = true;

  if (typeof bridge.ping === 'function') {
    try {
      const pingResult = await bridge.ping();
      if (pingResult !== undefined && pingResult !== null && String(pingResult).trim()) {
        pingMessage = String(pingResult);
      }
    } catch {
      bridgeReady = false;
      pingMessage = 'Bridge ping failed';
    }
  }

  try {
    const rawState = typeof bridge.getInitialState === 'function' ? await bridge.getInitialState() : '';
    const nextState = buildInitialState(normalizeBridgeJson(rawState, null));
    const bridgeMessage =
      nextState.bridge.message && nextState.bridge.message !== '浏览器预览模式'
        ? nextState.bridge.message
        : pingMessage;

    state.value = {
      ...nextState,
      bridge: {
        ...nextState.bridge,
        ready: bridgeReady || nextState.bridge.ready === true,
        message: bridgeMessage,
      },
    };
  } catch {
    state.value = {
      ...createFallbackBridgeState(),
      bridge: {
        ready: false,
        message: 'Bridge 初始化失败，已切换浏览器预览',
      },
    };
  }
}

function getBridge() {
  if (typeof window === 'undefined') {
    return null;
  }
  return window.BitBondBridge ?? null;
}

async function callBridgeMethod(
  method: BridgeCallableName,
  fallback: Record<string, unknown>,
  payload?: string,
) {
  return invokeBridgeMethod(getBridge(), method, fallback, payload);
}

function parseBridgeObject(rawBridgeJson: string) {
  try {
    const parsed = JSON.parse(rawBridgeJson);
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

function readBridgeData(rawBridgeJson: string) {
  const parsed = parseBridgeObject(rawBridgeJson);
  const data = parsed?.data;
  return data && typeof data === 'object' && !Array.isArray(data) ? (data as Record<string, unknown>) : {};
}

function bridgeResultOk(rawBridgeJson: string) {
  return parseBridgeObject(rawBridgeJson)?.ok === true;
}

function setActiveView(view: AppView) {
  activeView.value = view;
  localMessage.value = '';
  if (view !== 'settings') {
    unlinkStep.value = 'idle';
    deleteAccountStep.value = 'idle';
  } else {
    void loadPrivacySettings({ silent: true });
  }
  if (view === 'home') {
    void loadLatestInteractions({ silent: true });
  }
}

function setNotice(kind: NoticeKind, message: string) {
  state.value = {
    ...state.value,
    notice: {
      kind,
      message,
    },
  };
}

async function refreshPartnerStatus() {
  if (refreshing.value) {
    return;
  }

  refreshing.value = true;
  try {
    const fallbackStatusCode = nextPreviewStatusCode();
    const rawResult = await callBridgeMethod('refreshPartnerStatus', {
    ok: true,
    data: {
      upload: {
        code: fallbackStatusCode,
      },
      partnerStatus: {
        paired: state.value.pair.paired,
        partner: {
          nickname: state.value.pair.nickname,
        },
        statusCode: fallbackStatusCode,
        statusUpdatedAt: '刚刚',
        isPaused: false,
      },
    },
  });

    state.value = applyPartnerStatusResult(state.value, rawResult);
  } finally {
    refreshing.value = false;
  }
}

async function uploadCurrentStatus() {
  if (uploading.value) {
    return;
  }

  uploading.value = true;
  const rawResult = await callBridgeMethod('uploadCurrentStatus', {
    ok: true,
    data: {
      code: currentStatus.value.code,
    },
  });

  if (bridgeResultOk(rawResult)) {
    const code = readBridgeData(rawResult).code;
    setNotice('success', typeof code === 'string' ? `本机状态已上传：${code}` : '本机状态已上传');
  } else {
    setNotice('error', '本机状态上传失败，请稍后再试');
  }
  uploading.value = false;
}

async function createPairInvite() {
  if (pairingBusy.value !== 'idle') {
    return;
  }

  pairingBusy.value = 'create';
  const rawResult = await callBridgeMethod('createPairInvite', {
    ok: true,
    data: {
      code: 'DEMO-2468',
      expiresAt: '浏览器预览',
    },
  });

  state.value = applyPairInviteResult(state.value, rawResult);
  pairingBusy.value = 'idle';
}

async function acceptPairInvite() {
  const code = acceptCode.value.trim();

  if (!code || pairingBusy.value !== 'idle') {
    setNotice('error', '请输入配对码');
    return;
  }

  pairingBusy.value = 'accept';
  const rawResult = await callBridgeMethod(
    'acceptPairInvite',
    {
      ok: true,
      data: {
        paired: true,
        coupleId: 'preview-couple',
        partner: {
          nickname: '小禾',
          avatarId: 'rabbit',
        },
      },
    },
    JSON.stringify({ code }),
  );

  state.value = applyAcceptInviteResult(state.value, rawResult);
  if (state.value.notice.kind === 'success') {
    acceptCode.value = '';
  }
  pairingBusy.value = 'idle';
}

async function listAvatars(options: { silent?: boolean } = {}) {
  if (avatarBusy.value !== 'idle') {
    return;
  }

  avatarBusy.value = 'list';
  const rawResult = await callBridgeMethod('listAvatars', {
    ok: true,
    data: {
      avatars: [],
    },
  });
  const data = readBridgeData(rawResult);

  bridgeAvatars.value = Array.isArray(data.avatars) ? data.avatars : [];
  if (!options.silent) {
    setNotice(bridgeResultOk(rawResult) ? 'success' : 'error', bridgeResultOk(rawResult) ? '头像列表已刷新' : '头像列表刷新失败');
  }
  avatarBusy.value = 'idle';
}

async function selectAvatar(avatarId: string) {
  if (!avatarId || avatarBusy.value !== 'idle') {
    return;
  }

  avatarBusy.value = 'select';
  const rawResult = await callBridgeMethod(
    'selectAvatar',
    {
      ok: true,
      data: {
        avatarId,
      },
    },
    JSON.stringify({ avatarId }),
  );

  state.value = applyAvatarSelectionResult(state.value, rawResult);
  avatarBusy.value = 'idle';
}

async function checkStatusAccess(options: { silent?: boolean } = {}) {
  if (permissionBusy.value !== 'idle') {
    return;
  }

  permissionBusy.value = 'check';
  const [usageRawResult, accessibilityRawResult, batteryRawResult] = await Promise.all([
    callBridgeMethod('checkUsageAccess', {
      ok: true,
      data: {
        hasUsageAccess: false,
      },
    }),
    callBridgeMethod('checkAccessibilityAccess', {
      ok: true,
      data: {
        hasAccessibilityAccess: false,
      },
    }),
    callBridgeMethod('checkBatteryOptimization', {
      ok: true,
      data: {
        isIgnoringBatteryOptimizations: false,
      },
    }),
  ]);
  const usageData = readBridgeData(usageRawResult);
  const accessibilityData = readBridgeData(accessibilityRawResult);
  const batteryData = readBridgeData(batteryRawResult);

  statusAccess.value = {
    hasUsageAccess: usageData.hasUsageAccess === true,
    hasAccessibilityAccess: accessibilityData.hasAccessibilityAccess === true,
    isIgnoringBatteryOptimizations: batteryData.isIgnoringBatteryOptimizations === true,
  };
  if (!options.silent) {
    const allChecksOk =
      bridgeResultOk(usageRawResult) &&
      bridgeResultOk(accessibilityRawResult) &&
      bridgeResultOk(batteryRawResult);
    setNotice(
      allChecksOk ? 'success' : 'error',
      statusAccessNotice(statusAccess.value),
    );
  }
  permissionBusy.value = 'idle';
}

async function openUsageAccessSettings() {
  if (permissionBusy.value !== 'idle') {
    return;
  }

  permissionBusy.value = 'open-usage';
  const rawResult = await callBridgeMethod('openUsageAccessSettings', {
    ok: true,
    data: {
      opened: true,
    },
  });

  setNotice(bridgeResultOk(rawResult) ? 'success' : 'error', bridgeResultOk(rawResult) ? '已打开系统权限设置' : '无法打开系统权限设置');
  permissionBusy.value = 'idle';
}

async function openAccessibilitySettings() {
  if (permissionBusy.value !== 'idle') {
    return;
  }

  permissionBusy.value = 'open-accessibility';
  const rawResult = await callBridgeMethod('openAccessibilitySettings', {
    ok: true,
    data: {
      opened: true,
    },
  });

  setNotice(
    bridgeResultOk(rawResult) ? 'success' : 'error',
    bridgeResultOk(rawResult) ? '已打开无障碍设置' : '无法打开无障碍设置',
  );
  permissionBusy.value = 'idle';
}

async function openBatteryOptimizationSettings() {
  if (permissionBusy.value !== 'idle') {
    return;
  }

  permissionBusy.value = 'open-battery';
  const rawResult = await callBridgeMethod('openBatteryOptimizationSettings', {
    ok: true,
    data: {
      opened: true,
    },
  });

  setNotice(
    bridgeResultOk(rawResult) ? 'success' : 'error',
    bridgeResultOk(rawResult) ? '已打开电池优化设置' : '无法打开电池优化设置',
  );
  permissionBusy.value = 'idle';
}

function statusAccessNotice(access: {
  hasUsageAccess: boolean;
  hasAccessibilityAccess: boolean;
  isIgnoringBatteryOptimizations: boolean;
}) {
  if (access.hasAccessibilityAccess && access.isIgnoringBatteryOptimizations) {
    return '实时刷新已开启，电池优化已放行';
  }
  if (access.hasAccessibilityAccess) {
    return '实时刷新已开启，建议再放行电池优化';
  }
  if (access.hasUsageAccess && access.isIgnoringBatteryOptimizations) {
    return '轮询刷新已开启，系统仍可能延迟后台任务';
  }
  if (access.hasUsageAccess) {
    return '轮询刷新已开启，建议放行电池优化以减少延迟';
  }
  return '还未开启状态刷新权限';
}

async function pauseSharing() {
  if (sharingBusy.value || !sharingViewModel.value.isSharing) {
    return;
  }

  sharingBusy.value = true;
  const rawResult = await callBridgeMethod('pauseSharing', {
    ok: true,
    data: {
      sharing: false,
    },
  });

  state.value = applyPauseSharingResult(state.value, rawResult);
  sharingBusy.value = false;
}

async function resumeSharing() {
  if (sharingBusy.value || sharingViewModel.value.isSharing) {
    return;
  }

  sharingBusy.value = true;
  const rawResult = await callBridgeMethod('resumeSharing', {
    ok: true,
    data: {
      sharing: true,
    },
  });

  state.value = applyResumeSharingResult(state.value, rawResult);
  sharingBusy.value = false;
}

async function loadPrivacySettings(options: { silent?: boolean } = {}) {
  if (privacyBusy.value !== 'idle') {
    return;
  }

  privacyBusy.value = 'load';
  const rawResult = await callBridgeMethod('getPrivacySettings', {
    ok: true,
    data: {
      allowedStatuses: privacySettings.value.enabledCategories,
    },
  });
  const result = applyPrivacySettingsResult(privacySettings.value, rawResult);

  privacySettings.value = result.settings;
  if (!options.silent || result.notice.kind === 'error') {
    setNotice(result.notice.kind, result.notice.message);
  }
  privacyBusy.value = 'idle';
}

async function updatePrivacyCategory(categoryCode: string, enabled: boolean) {
  if (privacyBusy.value !== 'idle') {
    return;
  }

  const currentEnabled = new Set(privacySettingsViewModel.value.enabledCategories);
  if (enabled) {
    currentEnabled.add(categoryCode);
  } else {
    currentEnabled.delete(categoryCode);
  }
  const nextEnabledCategories = privacySettingsViewModel.value.categories
    .map((category: { code: string }) => category.code)
    .filter((code: string) => currentEnabled.has(code));

  privacyBusy.value = 'update';
  const payload = buildPrivacySettingsUpdatePayload(nextEnabledCategories, {
    source: 'bitbond_web_privacy',
    requestedAt: new Date().toISOString(),
  });
  const rawResult = await callBridgeMethod(
    'updatePrivacySettings',
    {
      ok: true,
      data: {
        allowedStatuses: nextEnabledCategories,
      },
    },
    payload,
  );
  const result = applyPrivacySettingsResult(privacySettings.value, rawResult);

  privacySettings.value = result.settings;
  setNotice(result.notice.kind, result.notice.message);
  privacyBusy.value = 'idle';
}

async function loadLatestInteractions(options: { silent?: boolean } = {}) {
  if (interactionsBusy.value !== 'idle') {
    return;
  }

  interactionsBusy.value = 'load';
  const rawResult = await callBridgeMethod('getLatestInteractions', {
    ok: true,
    data: {
      interactions: [],
    },
  });

  state.value = applyLatestInteractionsResult(state.value, rawResult);
  if (!options.silent && state.value.notice.kind === 'error') {
    localMessage.value = state.value.notice.message;
  }
  interactionsBusy.value = 'idle';
}

async function sendHeart() {
  if (heartBusy.value || !pairingViewModel.value.isPaired) {
    setNotice('error', '配对后才能发送爱心');
    return;
  }

  heartBusy.value = true;
  const rawResult = await callBridgeMethod('sendHeart', {
    ok: true,
    data: {
      sent: true,
    },
  });

  if (bridgeResultOk(rawResult)) {
    setNotice('success', '爱心已送达');
    void loadLatestInteractions({ silent: true });
  } else {
    setNotice('error', '爱心发送失败，请稍后再试');
  }
  heartBusy.value = false;
}

async function markHeartInteractionsSeen() {
  if (interactionsBusy.value !== 'idle' || interactionsViewModel.value.unreadInteractionIds.length === 0) {
    return;
  }

  interactionsBusy.value = 'mark';
  const payload = JSON.stringify({
    interactionIds: interactionsViewModel.value.unreadInteractionIds,
    seen: true,
    source: 'bitbond_web_home',
    requestedAt: new Date().toISOString(),
  });
  const rawResult = await callBridgeMethod(
    'markInteractionsSeen',
    {
      ok: true,
      data: {
        seen: true,
      },
    },
    payload,
  );

  state.value = applyMarkInteractionsSeenResult(state.value, rawResult);
  interactionsBusy.value = 'idle';
}

async function getDebugForegroundApp() {
  if (debugBusy.value) {
    return;
  }

  debugBusy.value = true;
  const rawResult = await callBridgeMethod('getDebugForegroundApp', {
    ok: true,
    data: {
      enabled: false,
    },
  });

  debugForeground.value = sanitizeDebugForeground(readBridgeData(rawResult));
  state.value = applyDebugForegroundResult(state.value, rawResult);
  debugBusy.value = false;
}

function requestUnlink() {
  if (!settingsViewModel.value.paired || unlinking.value) {
    return;
  }
  unlinkStep.value = 'confirm';
  localMessage.value = '';
}

function cancelUnlink() {
  if (unlinking.value) {
    return;
  }
  unlinkStep.value = 'idle';
}

function requestDeleteAccount() {
  if (deletingAccount.value) {
    return;
  }
  deleteAccountStep.value = 'confirm';
  localMessage.value = '';
}

function cancelDeleteAccount() {
  if (deletingAccount.value) {
    return;
  }
  deleteAccountStep.value = 'idle';
}

async function confirmUnlink() {
  if (unlinking.value) {
    return;
  }

  unlinking.value = true;
  const payload = JSON.stringify({
    action: 'unlink',
    source: 'bitbond_web_settings',
    confirmed: true,
    requestedAt: new Date().toISOString(),
  });
  const rawResult = await callBridgeMethod(
    'unlink',
    {
      ok: true,
      data: {
        unlinked: true,
      },
    },
    payload,
  );

  state.value = applyUnlinkResult(state.value, rawResult);
  unlinkStep.value = state.value.notice.kind === 'success' ? 'done' : 'idle';
  unlinking.value = false;
}

async function confirmDeleteAccount() {
  if (deletingAccount.value) {
    return;
  }

  deletingAccount.value = true;
  const payload = JSON.stringify({
    action: 'delete_account',
    source: 'bitbond_web_settings',
    confirmed: true,
    requestedAt: new Date().toISOString(),
  });
  const rawResult = await callBridgeMethod(
    'deleteAccount',
    {
      ok: true,
      data: {
        deleted: true,
      },
    },
    payload,
  );

  state.value = applyDeleteAccountResult(state.value, rawResult);
  deleteAccountStep.value = state.value.notice.kind === 'success' ? 'done' : 'idle';
  unlinkStep.value = 'idle';
  deletingAccount.value = false;
}

function startRoomMotion(statusCode: string, animated: boolean) {
  const presentation = getRoomPresentation(statusCode);
  const roomCandidates = presentation.roomCandidates.length > 0 ? presentation.roomCandidates : ['room_center'];
  const nextIndex = animated ? (roomPositionCursor.value + 1) % roomCandidates.length : roomPositionCursor.value;
  const phase = animated ? 'move' : 'action';

  roomPositionCursor.value = nextIndex;
  clearRoomMotionTimer();
  roomMotion.value = {
    statusCode: presentation.statusCode,
    phase,
    roomPosition: roomCandidates[nextIndex],
  };

  if (animated) {
    roomMotionTimer = window.setTimeout(() => {
      roomMotion.value = advanceRoomMotionPhase(roomMotion.value, { movementComplete: true });
    }, presentation.movePhase.durationMs);
  }
}

function clearRoomMotionTimer() {
  if (roomMotionTimer !== undefined) {
    window.clearTimeout(roomMotionTimer);
    roomMotionTimer = undefined;
  }
}

function nextPreviewStatusCode() {
  const currentIndex = visibleStatusConfigs.findIndex((item) => item.code === state.value.partner.statusCode);
  const nextIndex = currentIndex >= 0 ? (currentIndex + 1) % visibleStatusConfigs.length : 0;
  return visibleStatusConfigs[nextIndex].code;
}
</script>

<template>
  <main class="app-shell">
    <section class="phone-frame" aria-label="BitBond">
      <header class="topbar">
        <div class="title-block">
          <p class="eyebrow">BitBond</p>
          <h1>{{ viewTitle }}</h1>
        </div>
        <div class="bridge-pill" :data-tone="bridgeTone" aria-live="polite">
          <span class="bridge-dot" aria-hidden="true"></span>
          <span>{{ bridgeLabel }}</span>
        </div>
      </header>

      <p class="bridge-message">{{ state.bridge.message }}</p>

      <Transition name="view" mode="out-in">
        <section v-if="activeView === 'home'" key="home" class="view-stack home-view">
          <section class="room-panel" aria-label="伴侣房间">
            <div class="room-stage">
              <img class="pixel-art room-art" :src="assetPaths.room" alt="" draggable="false" />
              <span
                class="pixel-art sprite-frame avatar-sprite"
                :data-phase="roomMotion.phase"
                :data-position="roomMotion.roomPosition"
                :style="roomAvatarStyle"
                :aria-label="roomSpriteLabel"
              ></span>
              <span
                class="pixel-art sprite-frame prop-sprite"
                :data-position="roomMotion.roomPosition"
                :style="statusPropStyle"
                aria-hidden="true"
              ></span>
            </div>
          </section>

          <section class="status-panel">
            <div class="status-head">
              <div>
                <p class="section-label">{{ partnerName }}</p>
                <h2>{{ homeStatusViewModel.title }}</h2>
              </div>
              <span class="status-chip">{{ currentStatus.label }}</span>
            </div>
            <p class="empty-note">{{ homeStatusViewModel.message }}</p>
            <dl class="status-list">
              <div>
                <dt>房间区域</dt>
                <dd>{{ state.partner.areaLabel }}</dd>
              </div>
              <div>
                <dt>更新时间</dt>
                <dd>{{ state.partner.updatedAt }}</dd>
              </div>
              <div>
                <dt>动作阶段</dt>
                <dd>{{ roomMotion.phase === 'move' ? '移动中' : '互动中' }}</dd>
              </div>
              <div>
                <dt>我的头像</dt>
                <dd>{{ selectedAvatarName }}</dd>
              </div>
            </dl>
          </section>

          <div class="action-grid" aria-label="房间操作">
            <button class="primary-button" type="button" :disabled="refreshing" @click="refreshPartnerStatus">
              {{ refreshing ? '刷新中' : '刷新伴侣' }}
            </button>
            <button class="secondary-button" type="button" :disabled="uploading" @click="uploadCurrentStatus">
              {{ uploading ? '上传中' : '上传本机' }}
            </button>
            <button class="heart-button" type="button" :disabled="heartBusy || !pairingViewModel.isPaired" @click="sendHeart">
              {{ heartBusy ? '发送中' : '送爱心' }}
            </button>
          </div>

          <section
            v-if="interactionsViewModel.hasUnreadHeart"
            class="heart-alert"
            :data-unread="interactionsViewModel.hasUnreadHeart"
            aria-live="polite"
          >
            <span class="heart-glyph" aria-hidden="true">心</span>
            <div>
              <strong>收到一个爱心</strong>
              <p>对方刚刚向你发来轻量互动。</p>
            </div>
            <button
              class="small-button"
              type="button"
              :disabled="interactionsBusy !== 'idle'"
              @click="markHeartInteractionsSeen"
            >
              知道了
            </button>
          </section>
        </section>

        <section v-else-if="activeView === 'pairing'" key="pairing" class="view-stack">
          <section class="panel">
            <div class="section-head">
              <div>
                <p class="section-label">配对状态</p>
                <h2>{{ pairingViewModel.isPaired ? `已与 ${partnerName} 配对` : '还没有配对对象' }}</h2>
              </div>
              <span class="status-chip">{{ pairingViewModel.isPaired ? '已连接' : '待配对' }}</span>
            </div>

            <div v-if="!pairingViewModel.isPaired" class="pair-stack">
              <button class="primary-button" type="button" :disabled="pairingBusy !== 'idle'" @click="createPairInvite">
                {{ pairingBusy === 'create' ? '生成中' : '生成配对码' }}
              </button>
              <div v-if="pairingViewModel.inviteCode" class="code-box" aria-live="polite">
                <span>配对码</span>
                <strong>{{ pairingViewModel.inviteCode }}</strong>
                <small>{{ pairingViewModel.expiresAt }}</small>
              </div>
              <label class="input-field">
                <span>输入对方配对码</span>
                <input v-model.trim="acceptCode" type="text" inputmode="text" autocomplete="one-time-code" />
              </label>
              <button class="secondary-button" type="button" :disabled="pairingBusy !== 'idle'" @click="acceptPairInvite">
                {{ pairingBusy === 'accept' ? '接受中' : '接受配对码' }}
              </button>
            </div>

            <div v-else class="paired-summary">
              <p>你们可以在房间里查看彼此的抽象状态，具体应用和内容不会展示。</p>
              <button class="secondary-button" type="button" :disabled="refreshing" @click="refreshPartnerStatus">
                {{ refreshing ? '刷新中' : '刷新状态' }}
              </button>
            </div>
          </section>
        </section>

        <section v-else-if="activeView === 'avatar'" key="avatar" class="view-stack">
          <section class="panel">
            <div class="section-head">
              <div>
                <p class="section-label">当前头像</p>
                <h2>{{ selectedAvatarName }}</h2>
              </div>
              <button class="small-button" type="button" :disabled="avatarBusy !== 'idle'" @click="listAvatars()">
                {{ avatarBusy === 'list' ? '刷新中' : '刷新' }}
              </button>
            </div>

            <div class="avatar-grid" aria-label="头像选择">
              <button
                v-for="avatar in avatarViewModel.avatarChoices"
                :key="avatar.id"
                class="avatar-choice"
                :class="{ 'is-selected': avatar.selected }"
                type="button"
                :disabled="avatarBusy !== 'idle'"
                @click="selectAvatar(avatar.id)"
              >
                <span class="avatar-mark" :data-avatar="avatar.id">{{ avatar.name.slice(0, 1) }}</span>
                <strong>{{ avatar.name }}</strong>
              </button>
            </div>
          </section>
        </section>

        <section v-else-if="activeView === 'permission'" key="permission" class="view-stack">
          <section class="panel">
            <div class="section-head">
              <div>
                <p class="section-label">Status Sync</p>
                <h2>{{ permissionViewModel.title }}</h2>
              </div>
              <span class="status-chip">
                {{ permissionViewModel.mode === 'accessibility' ? '实时' : permissionViewModel.mode === 'polling' ? '轮询' : '未开启' }}
              </span>
            </div>
            <p class="body-copy">{{ permissionViewModel.description }}</p>
            <div class="permission-options">
              <div class="permission-option">
                <div>
                  <strong>无障碍实时刷新</strong>
                  <small>切换应用时触发上传，只读取应用包名</small>
                </div>
                <span class="status-chip">{{ permissionViewModel.hasAccessibilityAccess ? '已开启' : '可选' }}</span>
              </div>
              <div class="permission-option">
                <div>
                  <strong>UsageStats 轮询刷新</strong>
                  <small>不开无障碍时使用，后台及时性受系统限制</small>
                </div>
                <span class="status-chip">{{ permissionViewModel.hasUsageAccess ? '已开启' : '未开启' }}</span>
              </div>
              <div class="permission-option">
                <div>
                  <strong>电池优化放行</strong>
                  <small>减少小米等系统延迟后台轮询和上传</small>
                </div>
                <span class="status-chip">{{ permissionViewModel.isIgnoringBatteryOptimizations ? '已放行' : '建议设置' }}</span>
              </div>
            </div>
            <div class="action-grid">
              <button class="secondary-button" type="button" :disabled="permissionBusy !== 'idle'" @click="checkStatusAccess()">
                {{ permissionBusy === 'check' ? '检查中' : '检查权限' }}
              </button>
              <button
                v-if="!permissionViewModel.hasUsageAccess && !permissionViewModel.hasAccessibilityAccess"
                class="primary-button"
                type="button"
                :disabled="permissionBusy !== 'idle'"
                @click="openUsageAccessSettings"
              >
                {{ permissionBusy === 'open-usage' ? '打开中' : '开启轮询' }}
              </button>
              <button
                v-if="!permissionViewModel.hasAccessibilityAccess"
                :class="permissionViewModel.mode === 'missing' ? 'primary-button' : 'secondary-button'"
                type="button"
                :disabled="permissionBusy !== 'idle'"
                @click="openAccessibilitySettings"
              >
                {{ permissionBusy === 'open-accessibility' ? '打开中' : '开启实时' }}
              </button>
              <button
                v-if="!permissionViewModel.isIgnoringBatteryOptimizations"
                class="secondary-button"
                type="button"
                :disabled="permissionBusy !== 'idle'"
                @click="openBatteryOptimizationSettings"
              >
                {{ permissionBusy === 'open-battery' ? '打开中' : '电池优化' }}
              </button>
            </div>
          </section>
        </section>

        <section v-else-if="activeView === 'settings'" key="settings" class="view-stack">
          <section class="panel">
            <div class="section-head">
              <div>
                <p class="section-label">共享状态</p>
                <h2>{{ sharingViewModel.title }}</h2>
              </div>
              <span class="status-chip">{{ sharingViewModel.isSharing ? '共享中' : '已暂停' }}</span>
            </div>
            <p class="body-copy">{{ sharingViewModel.statusText }}</p>
            <div class="action-grid single-action">
              <button
                :class="sharingViewModel.isSharing ? 'secondary-button' : 'primary-button'"
                type="button"
                :disabled="sharingBusy"
                @click="sharingViewModel.isSharing ? pauseSharing() : resumeSharing()"
              >
                {{ sharingBusy ? '处理中' : sharingViewModel.primaryAction.label }}
              </button>
            </div>
          </section>

          <section class="panel compact-panel">
            <div class="section-head">
              <div>
                <p class="section-label">隐私类别</p>
                <h2>选择可以共享的抽象状态</h2>
              </div>
              <button class="small-button" type="button" :disabled="privacyBusy !== 'idle'" @click="loadPrivacySettings()">
                {{ privacyBusy === 'load' ? '刷新中' : '刷新' }}
              </button>
            </div>
            <div class="privacy-list" aria-label="隐私类别开关">
              <label v-for="category in privacySettingsViewModel.categories" :key="category.code" class="privacy-row">
                <input
                  type="checkbox"
                  :checked="category.enabled"
                  :disabled="privacyBusy !== 'idle'"
                  @change="updatePrivacyCategory(category.code, !category.enabled)"
                />
                <span>
                  <strong>{{ category.label }}</strong>
                  <small>{{ category.statusText }}</small>
                </span>
              </label>
            </div>
          </section>

          <section class="panel">
            <div class="section-head">
              <div>
                <p class="section-label">配对管理</p>
                <h2>{{ settingsViewModel.paired ? `当前与 ${settingsViewModel.partnerName} 配对` : '当前没有配对对象' }}</h2>
              </div>
              <span class="status-chip">{{ settingsViewModel.paired ? '可解除' : '未配对' }}</span>
            </div>
            <p class="body-copy">这里仅管理当前配对关系；房间状态以服务端同步结果为准。</p>
            <button class="danger-button" type="button" :disabled="!settingsViewModel.paired || unlinking" @click="requestUnlink">
              {{ unlinking ? '解除中' : '解除配对' }}
            </button>
            <div v-if="unlinkStep === 'confirm'" class="confirm-box" role="alert">
              <p>解除后双方将停止看到彼此的抽象状态，需要重新配对才能恢复。</p>
              <div class="confirm-actions">
                <button class="secondary-button" type="button" :disabled="unlinking" @click="cancelUnlink">取消</button>
                <button class="danger-button" type="button" :disabled="unlinking" @click="confirmUnlink">
                  {{ unlinking ? '解除中' : '确认解除' }}
                </button>
              </div>
            </div>
          </section>

          <section class="panel">
            <div class="section-head">
              <div>
                <p class="section-label">删除账号</p>
                <h2>二次确认后清空本机配对状态</h2>
              </div>
              <span class="status-chip">危险操作</span>
            </div>
            <p class="body-copy">删除会停止本机共享，并把当前房间恢复到未配对状态。</p>
            <button class="danger-button" type="button" :disabled="deletingAccount" @click="requestDeleteAccount">
              {{ deletingAccount ? '删除中' : '删除账号' }}
            </button>
            <div v-if="deleteAccountStep === 'confirm'" class="confirm-box" role="alert">
              <p>请再次确认删除账号。本操作只会在点击确认后调用 Bridge 删除接口。</p>
              <div class="confirm-actions">
                <button class="secondary-button" type="button" :disabled="deletingAccount" @click="cancelDeleteAccount">取消</button>
                <button class="danger-button" type="button" :disabled="deletingAccount" @click="confirmDeleteAccount">
                  {{ deletingAccount ? '删除中' : '确认删除' }}
                </button>
              </div>
            </div>
          </section>
        </section>

        <section v-else-if="activeView === 'info'" key="info" class="view-stack">
          <section class="panel">
            <div class="section-head">
              <div>
                <p class="section-label">隐私承诺</p>
                <h2>房间只显示抽象状态</h2>
              </div>
              <span class="status-chip">内测</span>
            </div>
            <ul class="info-list">
              <li>不展示具体 App 名称。</li>
              <li>不展示使用内容、聊天对象、浏览记录或使用时长。</li>
              <li>可以随时暂停共享、解绑或删除数据。</li>
              <li>安装和内测期间，Bridge 接入失败时会保留浏览器预览态，不会输出密钥或私密字段。</li>
              <li>内测反馈只需描述抽象状态误判、配对或小组件问题，不要提交包含私密内容的截图。</li>
            </ul>
          </section>

          <section class="panel compact-panel">
            <p class="section-label">使用入口</p>
            <div class="info-actions">
              <button class="secondary-button" type="button" @click="setActiveView('settings')">管理隐私</button>
              <button class="secondary-button" type="button" @click="setActiveView('pairing')">管理配对</button>
            </div>
          </section>
        </section>

        <section v-else key="debug" class="view-stack">
          <section class="panel">
            <div class="section-head">
              <div>
                <p class="section-label">Debug</p>
                <h2>{{ debugViewModel.title }}</h2>
              </div>
              <span class="status-chip">{{ debugViewModel.enabled ? 'enabled' : 'disabled' }}</span>
            </div>
            <p class="body-copy">{{ debugViewModel.description }}</p>
            <button class="secondary-button" type="button" :disabled="debugBusy" @click="getDebugForegroundApp">
              {{ debugBusy ? '读取中' : '读取前台调试' }}
            </button>
            <dl v-if="debugViewModel.packageFields.length" class="debug-list">
              <div v-for="field in debugViewModel.packageFields" :key="field.key">
                <dt>{{ field.label }}</dt>
                <dd>{{ field.value }}</dd>
              </div>
            </dl>
            <p v-else class="empty-note">当前不会展示包名、应用名或使用时长字段。</p>
          </section>
        </section>
      </Transition>

      <p v-if="noticeMessage" class="notice" :data-kind="noticeKind" aria-live="polite">
        {{ noticeMessage }}
      </p>

      <nav class="bottom-tabs" aria-label="底部导航">
        <button
          v-for="item in navItems"
          :key="item.id"
          type="button"
          :aria-current="activeView === item.id ? 'page' : undefined"
          @click="setActiveView(item.id)"
        >
          {{ item.label }}
        </button>
      </nav>
    </section>
  </main>
</template>
