package com.bitbond.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.bitbond.app.account.AccountRepository;
import com.bitbond.app.analytics.AnalyticsRepository;
import com.bitbond.app.api.ApiError;
import com.bitbond.app.api.ApiResult;
import com.bitbond.app.api.HttpSupabaseRpcClient;
import com.bitbond.app.api.Transport;
import com.bitbond.app.api.TransportResponse;
import com.bitbond.app.auth.AuthGateway;
import com.bitbond.app.auth.AuthRepository;
import com.bitbond.app.auth.AuthSession;
import com.bitbond.app.auth.SessionStore;
import com.bitbond.app.avatar.AvatarGateway;
import com.bitbond.app.avatar.AvatarModels.AvatarOption;
import com.bitbond.app.avatar.AvatarRepository;
import com.bitbond.app.bridge.BitBondBridgeController;
import com.bitbond.app.config.SupabaseConfig;
import com.bitbond.app.interaction.InteractionRepository;
import com.bitbond.app.pairing.PairingGateway;
import com.bitbond.app.pairing.PairingModels.CoupleState;
import com.bitbond.app.pairing.PairingModels.PairInvite;
import com.bitbond.app.pairing.PairingRepository;
import com.bitbond.app.privacy.PrivacyRepository;
import com.bitbond.app.sharing.SharingRepository;
import com.bitbond.app.status.DebugForegroundGateway;
import com.bitbond.app.status.DebugForegroundService;
import com.bitbond.app.status.ForegroundAppReader;
import com.bitbond.app.status.AccessibilityAccessGateway;
import com.bitbond.app.status.AccessibilityAccessHelper;
import com.bitbond.app.status.BatteryOptimizationGateway;
import com.bitbond.app.status.BatteryOptimizationHelper;
import com.bitbond.app.status.StatusGateway;
import com.bitbond.app.status.StatusMapper;
import com.bitbond.app.status.StatusModels.PartnerStatus;
import com.bitbond.app.status.StatusMonitorService;
import com.bitbond.app.status.StatusRepository;
import com.bitbond.app.status.StatusUploadCoordinator;
import com.bitbond.app.status.StatusUploadTrigger;
import com.bitbond.app.status.UsageAccessGateway;
import com.bitbond.app.status.UsageAccessHelper;
import com.bitbond.app.widget.WidgetStatusCache;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final int NETWORK_TIMEOUT_MS = 10000;
    private static final int POST_NOTIFICATIONS_REQUEST_CODE = 2401;
    public static final String AUTH_PREFS_NAME = "bitbond_auth";
    private static final String AUTH_PREF_ACCESS_TOKEN = "access_token";
    private static final String AUTH_PREF_REFRESH_TOKEN = "refresh_token";
    private static final String AUTH_PREF_EXPIRES_AT = "expires_at";
    private static final SessionStore PROCESS_SESSION_STORE = new SessionStore();

    private WebView webView;
    private AuthGateway authGateway;
    private StatusUploadTrigger statusUploadTrigger;
    private UsageAccessGateway usageAccessGateway;
    private AccessibilityAccessGateway accessibilityAccessGateway;
    private BatteryOptimizationGateway batteryOptimizationGateway;
    private boolean supabaseConfigured;
    private final ExecutorService statusSyncExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        configureWebViewDebugging();
        BitBondBridgeController bridgeController = createBridgeController();

        webView = new WebView(this);
        webView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        configureWebView(webView, bridgeController);
        setContentView(webView);

        webView.loadUrl(initialWebUrl());
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView(WebView targetWebView, BitBondBridgeController bridgeController) {
        WebSettings settings = targetWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccess(true);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);

        targetWebView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetResponseForUrl(request.getUrl().toString());
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                return assetResponseForUrl(url);
            }
        });
        targetWebView.addJavascriptInterface(bridgeController, "BitBondBridge");
    }

    public static String initialWebUrl() {
        return BitBondWebAssets.INDEX_URL;
    }

    private WebResourceResponse assetResponseForUrl(String url) {
        String assetPath = BitBondWebAssets.assetPathForUrl(url);
        if (assetPath == null) {
            return null;
        }

        try {
            String mimeType = BitBondWebAssets.mimeTypeForAssetPath(assetPath);
            return new WebResourceResponse(
                    mimeType,
                    responseEncoding(mimeType),
                    getAssets().open(assetPath));
        } catch (IOException exception) {
            return null;
        }
    }

    private static String responseEncoding(String mimeType) {
        if (mimeType.startsWith("text/")
                || "application/javascript".equals(mimeType)
                || "application/json".equals(mimeType)) {
            return StandardCharsets.UTF_8.name();
        }
        return null;
    }

    private void configureWebViewDebugging() {
        WebView.setWebContentsDebuggingEnabled(shouldEnableWebContentsDebugging(BuildConfig.DEBUG));
    }

    public static boolean shouldEnableWebContentsDebugging(boolean buildDebug) {
        return buildDebug;
    }

    public static boolean shouldRequestPostNotificationsPermission(int sdkInt, boolean permissionGranted) {
        return sdkInt >= 33 && !permissionGranted;
    }

    private void ensureNotificationPermissionForMonitor() {
        if (shouldRequestPostNotificationsPermission(
                Build.VERSION.SDK_INT,
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    POST_NOTIFICATIONS_REQUEST_CODE);
        }
    }

    private BitBondBridgeController createBridgeController() {
        SupabaseConfig.Values config = SupabaseConfig.current();
        usageAccessGateway = new UsageAccessHelper(this);
        accessibilityAccessGateway = new AccessibilityAccessHelper(this);
        batteryOptimizationGateway = new BatteryOptimizationHelper(this);
        ForegroundAppReader foregroundAppReader = ForegroundAppReader.fromContext(this);
        DebugForegroundGateway debugForeground = new DebugForegroundService(BuildConfig.DEBUG, foregroundAppReader);
        supabaseConfigured = config.isConfigured();

        if (!supabaseConfigured) {
            authGateway = new FailingAuthGateway("supabase_not_configured", "Supabase is not configured");
            statusUploadTrigger = null;
            return new BitBondBridgeController(
                    authGateway,
                    new FailingPairingGateway(),
                    new FailingAvatarGateway(),
                    new FailingStatusGateway(),
                    new FailingStatusUploadTrigger(),
                    usageAccessGateway,
                    accessibilityAccessGateway,
                    batteryOptimizationGateway,
                    debugForeground);
        }

        try {
            Transport transport = new UrlConnectionTransport();
            HttpSupabaseRpcClient rpcClient = new HttpSupabaseRpcClient(config.url(), config.anonKey(), transport);
            SessionStore sessionStore = PROCESS_SESSION_STORE;
            AuthRepository authRepository = new AuthRepository(
                    config.url(),
                    config.anonKey(),
                    transport,
                    rpcClient,
                    sessionStore);
            PairingRepository pairingRepository = new PairingRepository(rpcClient);
            AvatarRepository avatarRepository = new AvatarRepository(rpcClient);
            StatusRepository statusRepository = new StatusRepository(rpcClient);
            SharingRepository sharingRepository = new SharingRepository(rpcClient);
            PrivacyRepository privacyRepository = new PrivacyRepository(rpcClient);
            InteractionRepository interactionRepository = new InteractionRepository(rpcClient);
            AccountRepository accountRepository = new AccountRepository(rpcClient);
            AnalyticsRepository analyticsRepository = new AnalyticsRepository(rpcClient);
            StatusMapper statusMapper = loadStatusMapper();
            StatusUploadCoordinator statusUploadCoordinator = new StatusUploadCoordinator(
                    usageAccessGateway,
                    foregroundAppReader,
                    statusMapper,
                    statusRepository,
                    Instant::now);
            authGateway = serialAuthGateway(persistingAuthGateway(
                    authRepository,
                    sessionStore,
                    new SharedPreferencesSessionPersistence(getSharedPreferences(AUTH_PREFS_NAME, MODE_PRIVATE))));
            statusUploadTrigger = serialStatusUploadTrigger(statusUploadCoordinator);

            return new BitBondBridgeController(
                    authGateway,
                    pairingRepository,
                    avatarRepository,
                    statusRepository,
                    sharingRepository,
                    privacyRepository,
                    interactionRepository,
                    accountRepository,
                    analyticsRepository,
                    statusUploadTrigger,
                    usageAccessGateway,
                    accessibilityAccessGateway,
                    batteryOptimizationGateway,
                    debugForeground,
                    new WidgetStatusCache(this));
        } catch (RuntimeException exception) {
            authGateway = new FailingAuthGateway("bridge_not_configured", "Bridge could not be configured");
            statusUploadTrigger = null;
            return new BitBondBridgeController(
                    authGateway,
                    new FailingPairingGateway(),
                    new FailingAvatarGateway(),
                    new FailingStatusGateway(),
                    new FailingStatusUploadTrigger(),
                    usageAccessGateway,
                    accessibilityAccessGateway,
                    batteryOptimizationGateway,
                    debugForeground);
        }
    }

    private StatusMapper loadStatusMapper() {
        try {
            return StatusMapper.fromJson(readAssetText("status_map_cn.json"));
        } catch (IOException | RuntimeException exception) {
            return StatusMapper.fromJson("[]");
        }
    }

    private String readAssetText(String assetName) throws IOException {
        try (InputStream stream = getAssets().open(assetName);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        ensureNotificationPermissionForMonitor();
        startStatusMonitorService();
        syncStatusOnResume(
                supabaseConfigured,
                authGateway,
                statusUploadTrigger,
                usageAccessGateway,
                statusSyncExecutor);
    }

    public static void syncStatusOnResume(
            boolean supabaseConfigured,
            AuthGateway authGateway,
            StatusUploadTrigger uploadTrigger,
            UsageAccessGateway usageAccessGateway,
            Executor executor
    ) {
        try {
            if (!supabaseConfigured
                    || uploadTrigger == null
                    || authGateway == null
                    || usageAccessGateway == null
                    || executor == null
                    || !usageAccessGateway.hasUsageAccess()) {
                return;
            }

            executor.execute(() -> {
                try {
                    ApiResult<AuthSession> sessionResult = authGateway.ensureSession();
                    if (sessionResult.isSuccess()) {
                        uploadTrigger.uploadDetectedStatus(sessionResult.value());
                    }
                } catch (RuntimeException exception) {
                    // Best-effort foreground sync must never take down the UI.
                }
            });
        } catch (RuntimeException exception) {
            // Best-effort foreground sync must never take down the UI.
        }
    }

    private void startStatusMonitorService() {
        try {
            Intent intent = new Intent(this, StatusMonitorService.class);
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (RuntimeException exception) {
            // Monitor startup is best-effort; manual status upload must remain usable.
        }
    }

    public static AuthGateway serialAuthGateway(AuthGateway delegate) {
        return new SerialAuthGateway(delegate);
    }

    public static AuthGateway persistingAuthGateway(
            AuthGateway delegate,
            SessionStore sessionStore,
            SessionPersistence sessionPersistence
    ) {
        return new PersistingAuthGateway(delegate, sessionStore, sessionPersistence);
    }

    public static StatusUploadTrigger serialStatusUploadTrigger(StatusUploadTrigger delegate) {
        return new SerialStatusUploadTrigger(delegate);
    }

    public interface SessionPersistence {
        AuthSession read();

        void write(AuthSession session);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }

        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }

        statusSyncExecutor.shutdownNow();
        super.onDestroy();
    }

    public static AuthGateway failingAuthGateway(String code, String message) {
        return new FailingAuthGateway(code, message);
    }

    public static StatusUploadTrigger failingStatusUploadTrigger() {
        return new FailingStatusUploadTrigger();
    }

    private static ApiError unavailableError() {
        return new ApiError("bridge_unavailable", "Bridge is unavailable");
    }

    public static final class UrlConnectionTransport implements Transport {
        @Override
        public TransportResponse post(String url, Map<String, String> headers, String body) throws IOException {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(NETWORK_TIMEOUT_MS);
                connection.setReadTimeout(NETWORK_TIMEOUT_MS);
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);

                for (Map.Entry<String, String> header : headers.entrySet()) {
                    connection.setRequestProperty(header.getKey(), header.getValue());
                }

                byte[] requestBytes = body.getBytes(StandardCharsets.UTF_8);
                try (OutputStream outputStream = connection.getOutputStream()) {
                    outputStream.write(requestBytes);
                }

                int statusCode = connection.getResponseCode();
                InputStream responseStream = statusCode >= 200 && statusCode < 400
                        ? connection.getInputStream()
                        : connection.getErrorStream();
                String responseBody = readBody(responseStream);
                return new TransportResponse(statusCode, responseBody);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        private static String readBody(InputStream inputStream) throws IOException {
            if (inputStream == null) {
                return "";
            }

            try (InputStream stream = inputStream;
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                return output.toString(StandardCharsets.UTF_8.name());
            }
        }
    }

    private static final class FailingAuthGateway implements AuthGateway {
        private final ApiError error;

        private FailingAuthGateway(String code, String message) {
            error = new ApiError(code, message);
        }

        @Override
        public ApiResult<AuthSession> ensureSession() {
            return ApiResult.error(error);
        }
    }

    private static final class SerialAuthGateway implements AuthGateway {
        private final AuthGateway delegate;

        private SerialAuthGateway(AuthGateway delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public synchronized ApiResult<AuthSession> ensureSession() {
            return delegate.ensureSession();
        }
    }

    private static final class PersistingAuthGateway implements AuthGateway {
        private final AuthGateway delegate;
        private final SessionStore sessionStore;
        private final SessionPersistence sessionPersistence;

        private PersistingAuthGateway(
                AuthGateway delegate,
                SessionStore sessionStore,
                SessionPersistence sessionPersistence
        ) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            this.sessionStore = Objects.requireNonNull(sessionStore, "sessionStore");
            this.sessionPersistence = Objects.requireNonNull(sessionPersistence, "sessionPersistence");
        }

        @Override
        public ApiResult<AuthSession> ensureSession() {
            restorePersistedSessionIfNeeded();
            ApiResult<AuthSession> result = delegate.ensureSession();
            if (result.isSuccess()) {
                sessionPersistence.write(result.value());
            }
            return result;
        }

        private void restorePersistedSessionIfNeeded() {
            if (sessionStore.read() != null) {
                return;
            }

            try {
                AuthSession persistedSession = sessionPersistence.read();
                if (persistedSession != null) {
                    sessionStore.write(persistedSession);
                }
            } catch (RuntimeException exception) {
                // Invalid persisted auth data should not block anonymous sign-in.
            }
        }
    }

    public static final class SharedPreferencesSessionPersistence implements SessionPersistence {
        private final SharedPreferences preferences;

        public SharedPreferencesSessionPersistence(SharedPreferences preferences) {
            this.preferences = Objects.requireNonNull(preferences, "preferences");
        }

        @Override
        public AuthSession read() {
            String accessToken = preferences.getString(AUTH_PREF_ACCESS_TOKEN, "");
            String refreshToken = preferences.getString(AUTH_PREF_REFRESH_TOKEN, "");
            long expiresAt = preferences.getLong(AUTH_PREF_EXPIRES_AT, Long.MIN_VALUE);
            if (expiresAt == Long.MIN_VALUE) {
                return null;
            }

            try {
                return new AuthSession(accessToken, refreshToken, expiresAt);
            } catch (RuntimeException exception) {
                return null;
            }
        }

        @Override
        public void write(AuthSession session) {
            preferences.edit()
                    .putString(AUTH_PREF_ACCESS_TOKEN, session.accessToken())
                    .putString(AUTH_PREF_REFRESH_TOKEN, session.refreshToken())
                    .putLong(AUTH_PREF_EXPIRES_AT, session.expiresAtEpochSeconds())
                    .apply();
        }
    }

    private static final class SerialStatusUploadTrigger implements StatusUploadTrigger {
        private final StatusUploadTrigger delegate;

        private SerialStatusUploadTrigger(StatusUploadTrigger delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public synchronized ApiResult<String> uploadDetectedStatus(AuthSession session) {
            return delegate.uploadDetectedStatus(session);
        }
    }

    private static final class FailingPairingGateway implements PairingGateway {
        @Override
        public ApiResult<PairInvite> createInvite(AuthSession session) {
            return ApiResult.error(unavailableError());
        }

        @Override
        public ApiResult<CoupleState> acceptInvite(AuthSession session, String code) {
            return ApiResult.error(unavailableError());
        }

        @Override
        public ApiResult<CoupleState> getCurrentCouple(AuthSession session) {
            return ApiResult.error(unavailableError());
        }

        @Override
        public ApiResult<Boolean> unlink(AuthSession session) {
            return ApiResult.error(unavailableError());
        }
    }

    private static final class FailingAvatarGateway implements AvatarGateway {
        @Override
        public ApiResult<List<AvatarOption>> listAvatars(AuthSession session) {
            return ApiResult.success(Collections.emptyList());
        }

        @Override
        public ApiResult<String> selectAvatar(AuthSession session, String avatarId) {
            return ApiResult.error(unavailableError());
        }
    }

    private static final class FailingStatusGateway implements StatusGateway {
        @Override
        public ApiResult<PartnerStatus> getPartnerStatus(AuthSession session) {
            return ApiResult.error(unavailableError());
        }
    }

    private static final class FailingStatusUploadTrigger implements StatusUploadTrigger {
        @Override
        public ApiResult<String> uploadDetectedStatus(AuthSession session) {
            return ApiResult.error(unavailableError());
        }
    }
}
