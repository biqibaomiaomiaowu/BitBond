package com.bitbond.app.status;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.AlarmManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import com.bitbond.app.R;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class StatusMonitorService extends Service {
    private static final String TAG = "BitBondStatus";
    static final long POLL_INTERVAL_MS = 15_000L;
    private static final int NOTIFICATION_ID = 2601;
    private static final String CHANNEL_ID = "bitbond_status_monitor";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private StatusMonitorRunner runner;
    private StatusMonitorScheduler monitorScheduler;
    private StatusMonitorWakeLock monitorWakeLock;
    private StatusMonitorMode monitorMode;

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            acquireMonitorWakeLock();
            try {
                StatusMonitorRunner currentRunner = runner;
                if (currentRunner != null) {
                    StatusMonitorMode currentMode = monitorMode;
                    if (currentMode != null && !currentMode.shouldPollUsageStats()) {
                        Log.d(TAG, "status monitor poll skipped: accessibility event mode enabled");
                        return;
                    }
                    Log.d(TAG, "status monitor poll");
                    currentRunner.runOnce();
                }
            } finally {
                releaseMonitorWakeLock();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "status monitor create");
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        monitorWakeLock = createMonitorWakeLock();
        runner = StatusMonitorDependencies.createRunner(this);
        monitorMode = new StatusMonitorMode(new AccessibilityAccessHelper(this));
        monitorScheduler = new StatusMonitorScheduler(
                new AlarmScheduler(this, executor),
                pollRunnable,
                POLL_INTERVAL_MS,
                throwable -> Log.w(TAG, "status monitor poll failed", throwable));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "status monitor start startId=" + startId);
        scheduleNextPoll();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "status monitor destroy");
        if (monitorScheduler != null) {
            monitorScheduler.stop();
        }
        releaseMonitorWakeLock();
        executor.shutdownNow();
        super.onDestroy();
    }

    void scheduleNextPoll() {
        if (monitorScheduler != null) {
            monitorScheduler.start();
        }
    }

    private StatusMonitorWakeLock createMonitorWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager == null) {
            return null;
        }

        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "BitBond:StatusMonitor");
        return new StatusMonitorWakeLock(new PowerWakeLockHandle(wakeLock));
    }

    private void acquireMonitorWakeLock() {
        if (monitorWakeLock == null) {
            return;
        }

        try {
            monitorWakeLock.acquire();
            Log.d(TAG, "status monitor wake lock acquired");
        } catch (RuntimeException exception) {
            Log.w(TAG, "status monitor wake lock acquire failed", exception);
        }
    }

    private void releaseMonitorWakeLock() {
        if (monitorWakeLock == null) {
            return;
        }

        try {
            monitorWakeLock.release();
            Log.d(TAG, "status monitor wake lock released");
        } catch (RuntimeException exception) {
            Log.w(TAG, "status monitor wake lock release failed", exception);
        }
    }

    private static final class AlarmScheduler implements StatusMonitorScheduler.Scheduler {
        private final AlarmManager alarmManager;
        private final Handler alarmHandler = new Handler(Looper.getMainLooper());
        private final ExecutorService executor;

        private AlarmScheduler(Context context, ExecutorService executor) {
            alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            this.executor = Objects.requireNonNull(executor, "executor");
        }

        @Override
        public StatusMonitorScheduler.Cancellable schedule(Runnable runnable, long delayMillis) {
            AlarmCancellable cancellable = new AlarmCancellable(alarmManager);
            if (alarmManager == null) {
                executor.execute(cancellable.wrap(runnable));
                return cancellable;
            }

            alarmManager.setExact(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + Math.max(0L, delayMillis),
                    TAG,
                    cancellable.listenerFor(() -> executor.execute(cancellable.wrap(runnable))),
                    alarmHandler);
            return cancellable;
        }
    }

    private static final class AlarmCancellable implements StatusMonitorScheduler.Cancellable {
        private final AlarmManager alarmManager;
        private AlarmManager.OnAlarmListener listener;
        private boolean canceled;
        private boolean fired;

        private AlarmCancellable(AlarmManager alarmManager) {
            this.alarmManager = alarmManager;
        }

        @Override
        public synchronized void cancel() {
            canceled = true;
            if (alarmManager != null && listener != null) {
                alarmManager.cancel(listener);
            }
        }

        @Override
        public synchronized boolean isPending() {
            return !canceled && !fired;
        }

        private synchronized AlarmManager.OnAlarmListener listenerFor(Runnable runnable) {
            listener = () -> {
                synchronized (AlarmCancellable.this) {
                    if (canceled) {
                        return;
                    }
                    fired = true;
                }
                runnable.run();
            };
            return listener;
        }

        private Runnable wrap(Runnable runnable) {
            return () -> {
                synchronized (AlarmCancellable.this) {
                    if (canceled) {
                        return;
                    }
                    fired = true;
                }
                runnable.run();
            };
        }
    }

    private static final class PowerWakeLockHandle implements StatusMonitorWakeLock.WakeLockHandle {
        private final PowerManager.WakeLock wakeLock;

        private PowerWakeLockHandle(PowerManager.WakeLock wakeLock) {
            this.wakeLock = Objects.requireNonNull(wakeLock, "wakeLock");
        }

        @Override
        public void setReferenceCounted(boolean referenceCounted) {
            wakeLock.setReferenceCounted(referenceCounted);
        }

        @Override
        public void acquire() {
            wakeLock.acquire();
        }

        @Override
        public void release() {
            wakeLock.release();
        }

        @Override
        public boolean isHeld() {
            return wakeLock.isHeld();
        }
    }

    static Notification buildNotification(Context context) {
        Objects.requireNonNull(context, "context");
        return new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("BitBond 状态同步中")
                .setContentText("正在检测前台应用变化并同步抽象状态")
                .setOngoing(true)
                .build();
    }

    private Notification buildNotification() {
        return buildNotification(this);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "BitBond 状态同步",
                NotificationManager.IMPORTANCE_LOW);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }
}
