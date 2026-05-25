<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
// @ts-expect-error bitbondState is authored as .mjs for Android/web sharing in this phase.
import { applyUnlinkResult, buildInitialState, createFallbackBridgeState, statusConfigs } from './lib/bitbondState.mjs';

type NoticeKind = 'idle' | 'success' | 'error';
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
  };
  pair: {
    paired: boolean;
    nickname: string;
  };
  partner: {
    statusCode: string;
    statusText: string;
    updatedAt: string;
    areaLabel: string;
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
  unlink?: (payload: string) => BridgeJsonValue | Promise<BridgeJsonValue>;
};

declare global {
  interface Window {
    BitBondBridge?: BitBondBridge;
  }
}

const visibleStatusConfigs = statusConfigs as StatusConfig[];
const assetPaths = {
  room: './pixel/room/room_main.png',
  catWalk: './pixel/avatars/avatar_cat_walk_down_strip.png',
  catListen: './pixel/avatars/avatar_cat_listen_music_strip.png',
  statusProps: './pixel/status_props/status_props_sheet.png',
};

const state = ref<BridgeState>(createFallbackBridgeState());
const activeView = ref('home');
const categoriesOpen = ref(false);
const loved = ref(false);
const refreshing = ref(false);
const localMessage = ref('');
const unlinkStep = ref('idle');
const unlinking = ref(false);

const currentStatus = computed(
  () => visibleStatusConfigs.find((item) => item.code === state.value.partner.statusCode) ?? visibleStatusConfigs[6],
);
const partnerName = computed(() => {
  if (!state.value.pair.paired) {
    return '未配对';
  }
  return state.value.pair.nickname || '对方';
});
const bridgeTone = computed(() => (state.value.bridge.ready ? 'ready' : 'preview'));
const bridgeLabel = computed(() => (state.value.bridge.ready ? 'Bridge ready' : 'Preview mode'));
const selfSharingText = computed(() =>
  state.value.self.sharing ? state.value.self.statusText : '你已暂停共享抽象状态',
);
const noticeMessage = computed(() => state.value.notice.message || localMessage.value);
const noticeKind = computed(() => state.value.notice.kind || 'idle');

onMounted(() => {
  initializeBridge();
});

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

function normalizeBridgeJson(value: BridgeJsonValue, fallback: Record<string, unknown> | null) {
  if (typeof value === 'string') {
    return value;
  }
  if (value && typeof value === 'object') {
    return JSON.stringify(value);
  }
  return fallback ? JSON.stringify(fallback) : '';
}

function openHome() {
  activeView.value = 'home';
  unlinkStep.value = 'idle';
}

function openSettings() {
  activeView.value = 'settings';
  localMessage.value = '';
}

function refreshRoom() {
  refreshing.value = true;
  localMessage.value = '已刷新房间状态';
  window.setTimeout(() => {
    refreshing.value = false;
  }, 520);
}

function toggleLove() {
  loved.value = !loved.value;
  localMessage.value = loved.value ? '已发送轻触' : '已收回轻触';
}

function toggleSharing() {
  const nextSharing = !state.value.self.sharing;
  state.value = {
    ...state.value,
    self: {
      ...state.value.self,
      sharing: nextSharing,
      statusText: nextSharing ? '你正在共享抽象状态' : '你已暂停共享抽象状态',
    },
    notice: {
      kind: nextSharing ? 'success' : 'idle',
      message: nextSharing ? '共享已开启' : '',
    },
  };
}

function pauseSharing() {
  state.value = {
    ...state.value,
    self: {
      ...state.value.self,
      sharing: false,
      statusText: '你已暂停共享抽象状态',
    },
    notice: {
      kind: 'success',
      message: '已暂停共享，对方只会看到已暂停',
    },
  };
}

function requestUnlink() {
  if (!state.value.pair.paired || unlinking.value) {
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

  try {
    const bridge = getBridge();
    const payload = JSON.stringify({
      action: 'unlink',
      source: 'bitbond_web_settings',
      confirmed: true,
      requestedAt: new Date().toISOString(),
    });

    let rawResult = JSON.stringify({ ok: true, source: 'browser_fallback' });

    if (bridge && typeof bridge.unlink === 'function') {
      try {
        rawResult = normalizeBridgeJson(await bridge.unlink(payload), { ok: false });
      } catch {
        rawResult = JSON.stringify({ ok: false });
      }
    }

    state.value = applyUnlinkResult(state.value, rawResult);
    unlinkStep.value = state.value.notice.kind === 'success' ? 'done' : 'idle';
  } finally {
    unlinking.value = false;
  }
}
</script>

<template>
  <main class="app-shell">
    <section class="phone-frame" aria-label="BitBond">
      <header class="topbar">
        <div>
          <p class="eyebrow">BitBond</p>
          <h1>{{ activeView === 'home' ? '伴侣小房间' : '设置' }}</h1>
        </div>
        <div class="bridge-pill" :data-tone="bridgeTone" aria-live="polite">
          <span class="bridge-dot" aria-hidden="true"></span>
          <span>{{ bridgeLabel }}</span>
        </div>
      </header>

      <p class="bridge-message">{{ state.bridge.message }}</p>

      <Transition name="view" mode="out-in">
        <section v-if="activeView === 'home'" key="home" class="view-stack home-view">
          <section class="room-card" aria-label="BitBond 首页 mock">
            <div class="room-stage">
              <img class="pixel-art room-art" :src="assetPaths.room" alt="" draggable="false" />
              <span
                class="pixel-art sprite-frame cat-sprite cat-walk"
                :style="{ backgroundImage: `url(${assetPaths.catWalk})` }"
                aria-label="小猫正在房间里走动"
              ></span>
              <span
                class="pixel-art sprite-frame cat-sprite cat-listen"
                :style="{ backgroundImage: `url(${assetPaths.catListen})` }"
                aria-label="小猫正在听音乐"
              ></span>
              <span
                class="pixel-art sprite-frame prop-sprite"
                :style="{ backgroundImage: `url(${assetPaths.statusProps})` }"
                aria-label="音乐状态道具"
              ></span>
            </div>

            <div class="quick-actions" aria-label="房间操作">
              <button class="icon-button" :class="{ 'is-active': refreshing }" type="button" @click="refreshRoom">
                刷新
              </button>
              <button class="icon-button" :class="{ 'is-active': loved }" type="button" @click="toggleLove">
                爱心
              </button>
              <button class="icon-button" type="button" @click="openSettings">设置</button>
            </div>
          </section>

          <section class="status-card">
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
            </dl>
          </section>

          <section class="self-card">
            <div>
              <p class="section-label">我的共享</p>
              <p>{{ selfSharingText }}</p>
            </div>
            <span class="sharing-badge" :data-enabled="state.self.sharing">
              {{ state.self.sharing ? '开启' : '暂停' }}
            </span>
          </section>

          <p v-if="noticeMessage" class="notice" :data-kind="noticeKind" aria-live="polite">
            {{ noticeMessage }}
          </p>

          <section class="privacy-note">
            <h2>隐私安全</h2>
            <p>仅共享抽象状态类别和更新时间；状态同步通过 HTTPS 传输；你可以随时暂停共享或解除配对。</p>
          </section>
        </section>

        <section v-else key="settings" class="view-stack settings-view">
          <section class="settings-panel">
            <div class="setting-row">
              <div>
                <h2>共享状态</h2>
                <p>{{ selfSharingText }}</p>
              </div>
              <button
                class="switch-button"
                :class="{ 'is-on': state.self.sharing }"
                type="button"
                :aria-pressed="state.self.sharing"
                @click="toggleSharing"
              >
                <span></span>
              </button>
            </div>

            <button class="setting-link" type="button" @click="categoriesOpen = !categoriesOpen">
              <span>
                <strong>状态类别</strong>
                <small>短视频、音乐、阅读、游戏等抽象类别</small>
              </span>
              <b aria-hidden="true">{{ categoriesOpen ? '收起' : '展开' }}</b>
            </button>

            <div v-if="categoriesOpen" class="category-grid" aria-label="状态类别">
              <span
                v-for="item in visibleStatusConfigs"
                :key="item.code"
                :class="{ 'is-current': item.code === currentStatus.code }"
              >
                {{ item.label }}
              </span>
            </div>

            <button class="setting-link" type="button" @click="pauseSharing">
              <span>
                <strong>暂停共享</strong>
                <small>暂停后，对方只会看到已暂停</small>
              </span>
              <b aria-hidden="true">暂停</b>
            </button>
          </section>

          <section class="danger-panel">
            <div class="danger-copy">
              <h2>配对管理</h2>
              <p>{{ state.pair.paired ? `当前与 ${partnerName} 配对` : '当前没有配对对象' }}</p>
            </div>

            <button class="danger-button" type="button" :disabled="!state.pair.paired || unlinking" @click="requestUnlink">
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

            <button class="disabled-risk" type="button" disabled>删除账号</button>
          </section>

          <p v-if="noticeMessage" class="notice" :data-kind="noticeKind" aria-live="polite">
            {{ noticeMessage }}
          </p>

          <section class="privacy-note">
            <h2>隐私说明</h2>
            <p>仅共享抽象状态类别和更新时间；状态同步通过 HTTPS 传输；你可以随时暂停共享或解除配对。</p>
          </section>
        </section>
      </Transition>

      <nav class="bottom-tabs" aria-label="底部导航">
        <button type="button" :aria-current="activeView === 'home' ? 'page' : undefined" @click="openHome">
          首页
        </button>
        <button type="button" :aria-current="activeView === 'settings' ? 'page' : undefined" @click="openSettings">
          设置
        </button>
      </nav>
    </section>
  </main>
</template>
