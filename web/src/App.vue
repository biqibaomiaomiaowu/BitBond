<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, shallowRef, watch } from 'vue';
// @ts-expect-error bitbondState is authored as .mjs for Android/web sharing in this phase.
import * as bitbondState from './lib/bitbondState.mjs';
// @ts-expect-error bridgeClient is authored as .mjs for Android/web sharing in this phase.
import { callBridgeMethod as invokeBridgeMethod, normalizeBridgeJson } from './lib/bridgeClient.mjs';

const {
  advanceRoomMotionPhase,
  applyAcceptInviteResult,
  applyAvatarSelectionResult,
  applyDebugForegroundResult,
  applyPairInviteResult,
  applyPartnerStatusResult,
  applyUnlinkResult,
  buildAvatarViewModel,
  buildDebugViewModel,
  buildInitialState,
  buildPairingViewModel,
  buildPermissionViewModel,
  buildSettingsViewModel,
  createFallbackBridgeState,
  getRoomPresentation,
  statusConfigs,
} = bitbondState;

type NoticeKind = 'idle' | 'success' | 'error';
type AppView = 'home' | 'pairing' | 'avatar' | 'permission' | 'settings' | 'debug';
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
};
type BridgeJsonValue = string | Record<string, unknown> | null | undefined;
type BitBondBridge = {
  ping?: () => BridgeJsonValue | Promise<BridgeJsonValue>;
  getInitialState?: () => BridgeJsonValue | Promise<BridgeJsonValue>;
  checkUsageAccess?: () => BridgeJsonValue | Promise<BridgeJsonValue>;
  openUsageAccessSettings?: () => BridgeJsonValue | Promise<BridgeJsonValue>;
  getDebugForegroundApp?: () => BridgeJsonValue | Promise<BridgeJsonValue>;
  createPairInvite?: () => BridgeJsonValue | Promise<BridgeJsonValue>;
  acceptPairInvite?: (payload: string) => BridgeJsonValue | Promise<BridgeJsonValue>;
  unlink?: (payload?: string) => BridgeJsonValue | Promise<BridgeJsonValue>;
  listAvatars?: () => BridgeJsonValue | Promise<BridgeJsonValue>;
  selectAvatar?: (payload: string) => BridgeJsonValue | Promise<BridgeJsonValue>;
  refreshPartnerStatus?: () => BridgeJsonValue | Promise<BridgeJsonValue>;
  uploadCurrentStatus?: () => BridgeJsonValue | Promise<BridgeJsonValue>;
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
  { id: 'debug', label: '调试' },
];
const viewTitles: Record<AppView, string> = {
  home: '伴侣小房间',
  pairing: '配对',
  avatar: '头像',
  permission: '使用权限',
  settings: '设置',
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
const usageAccess = ref({ hasUsageAccess: false });
const bridgeAvatars = ref<Array<Record<string, unknown>>>([]);
const debugForeground = ref<Record<string, unknown>>({ enabled: false });
const acceptCode = shallowRef('');
const localMessage = shallowRef('');
const unlinkStep = shallowRef<'idle' | 'confirm' | 'done'>('idle');
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
const permissionBusy = shallowRef<'idle' | 'check' | 'open'>('idle');
const debugBusy = shallowRef(false);
const unlinking = shallowRef(false);
let roomMotionTimer: number | undefined;

const currentStatus = computed(
  () => visibleStatusConfigs.find((item) => item.code === state.value.partner.statusCode) ?? visibleStatusConfigs[6],
);
const bridgeTone = computed(() => (state.value.bridge.ready ? 'ready' : 'preview'));
const bridgeLabel = computed(() => (state.value.bridge.ready ? 'Bridge ready' : 'Preview mode'));
const viewTitle = computed(() => viewTitles[activeView.value]);
const noticeMessage = computed(() => state.value.notice.message || localMessage.value);
const noticeKind = computed(() => state.value.notice.kind || 'idle');
const roomPresentation = computed(() => getRoomPresentation(state.value.partner.statusCode));
const permissionViewModel = computed(() => buildPermissionViewModel(usageAccess.value));
const pairingViewModel = computed(() => buildPairingViewModel(state.value));
const avatarViewModel = computed(() =>
  buildAvatarViewModel({
    selectedAvatar: state.value.self.selectedAvatar,
    avatars: bridgeAvatars.value,
  }),
);
const settingsViewModel = computed(() => buildSettingsViewModel(state.value));
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
  void checkUsageAccess({ silent: true });
  void listAvatars({ silent: true });
});

onUnmounted(() => {
  clearRoomMotionTimer();
});

watch(
  () => state.value.partner.statusCode,
  (statusCode, previousStatusCode) => {
    startRoomMotion(statusCode, previousStatusCode !== undefined);
  },
  { immediate: true },
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
  refreshing.value = false;
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

async function checkUsageAccess(options: { silent?: boolean } = {}) {
  if (permissionBusy.value !== 'idle') {
    return;
  }

  permissionBusy.value = 'check';
  const rawResult = await callBridgeMethod('checkUsageAccess', {
    ok: true,
    data: {
      hasUsageAccess: false,
    },
  });
  const data = readBridgeData(rawResult);

  usageAccess.value = {
    hasUsageAccess: data.hasUsageAccess === true,
  };
  if (!options.silent) {
    setNotice(
      bridgeResultOk(rawResult) ? 'success' : 'error',
      data.hasUsageAccess === true ? '使用情况访问权限已开启' : '还未开启使用情况访问权限',
    );
  }
  permissionBusy.value = 'idle';
}

async function openUsageAccessSettings() {
  if (permissionBusy.value !== 'idle') {
    return;
  }

  permissionBusy.value = 'open';
  const rawResult = await callBridgeMethod('openUsageAccessSettings', {
    ok: true,
    data: {
      opened: true,
    },
  });

  setNotice(bridgeResultOk(rawResult) ? 'success' : 'error', bridgeResultOk(rawResult) ? '已打开系统权限设置' : '无法打开系统权限设置');
  permissionBusy.value = 'idle';
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

  debugForeground.value = readBridgeData(rawResult);
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
                <h2>{{ currentStatus.statusText }}</h2>
              </div>
              <span class="status-chip">{{ currentStatus.label }}</span>
            </div>
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
          </div>
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
                <p class="section-label">Usage Access</p>
                <h2>{{ permissionViewModel.title }}</h2>
              </div>
              <span class="status-chip">{{ permissionViewModel.hasUsageAccess ? '已开启' : '未开启' }}</span>
            </div>
            <p class="body-copy">{{ permissionViewModel.description }}</p>
            <div class="action-grid">
              <button class="secondary-button" type="button" :disabled="permissionBusy !== 'idle'" @click="checkUsageAccess()">
                {{ permissionBusy === 'check' ? '检查中' : '检查权限' }}
              </button>
              <button
                v-if="!permissionViewModel.hasUsageAccess"
                class="primary-button"
                type="button"
                :disabled="permissionBusy !== 'idle'"
                @click="openUsageAccessSettings"
              >
                {{ permissionBusy === 'open' ? '打开中' : '打开系统设置' }}
              </button>
            </div>
          </section>
        </section>

        <section v-else-if="activeView === 'settings'" key="settings" class="view-stack">
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

          <section class="panel compact-panel">
            <p class="section-label">抽象状态类别</p>
            <div class="category-grid" aria-label="状态类别">
              <span
                v-for="item in visibleStatusConfigs"
                :key="item.code"
                :class="{ 'is-current': item.code === currentStatus.code }"
              >
                {{ item.label }}
              </span>
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
