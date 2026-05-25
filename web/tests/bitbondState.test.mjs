import test from 'node:test';
import assert from 'node:assert/strict';
import {
  applyUnlinkResult,
  buildInitialState,
  createFallbackBridgeState,
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
