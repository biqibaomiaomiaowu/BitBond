package com.bitbond.app.status;

import android.content.Context;

import com.bitbond.app.api.HttpSupabaseRpcClient;
import com.bitbond.app.api.ApiError;
import com.bitbond.app.api.ApiResult;
import com.bitbond.app.api.Transport;
import com.bitbond.app.auth.AuthGateway;
import com.bitbond.app.auth.AuthRepository;
import com.bitbond.app.auth.SessionStore;
import com.bitbond.app.config.SupabaseConfig;
import com.bitbond.app.auth.AuthSession;
import com.bitbond.app.MainActivity;
import com.bitbond.app.MainActivity.SessionPersistence;
import com.bitbond.app.MainActivity.SharedPreferencesSessionPersistence;
import com.bitbond.app.MainActivity.UrlConnectionTransport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public final class StatusMonitorDependencies {
    private static final String AUTH_PREFS_NAME = "bitbond_auth";

    private StatusMonitorDependencies() {
    }

    public static StatusMonitorRunner createRunner(Context context) {
        Context applicationContext = context.getApplicationContext();
        Context serviceContext = applicationContext == null ? context : applicationContext;
        SupabaseConfig.Values config = SupabaseConfig.current();
        UsageAccessGateway usageAccessGateway = new UsageAccessHelper(serviceContext);
        if (!config.isConfigured()) {
            return new StatusMonitorRunner(
                    false,
                    MainActivity.failingAuthGateway("supabase_not_configured", "Supabase is not configured"),
                    usageAccessGateway,
                    ForegroundAppReader.fromContext(serviceContext),
                    StatusMapper.fromJson("[]"),
                    new NoOpStatusUploader(),
                    Instant::now);
        }

        Transport transport = new UrlConnectionTransport();
        HttpSupabaseRpcClient rpcClient = new HttpSupabaseRpcClient(config.url(), config.anonKey(), transport);
        SessionStore sessionStore = new SessionStore();
        AuthRepository authRepository = new AuthRepository(
                config.url(),
                config.anonKey(),
                transport,
                rpcClient,
                sessionStore);
        SessionPersistence persistence = new SharedPreferencesSessionPersistence(
                serviceContext.getSharedPreferences(AUTH_PREFS_NAME, Context.MODE_PRIVATE));
        AuthGateway authGateway = MainActivity.serialAuthGateway(MainActivity.persistingAuthGateway(
                authRepository,
                sessionStore,
                persistence));
        StatusRepository statusRepository = new StatusRepository(rpcClient);
        StatusMapper statusMapper = loadStatusMapper(serviceContext);

        return new StatusMonitorRunner(
                true,
                authGateway,
                usageAccessGateway,
                ForegroundAppReader.fromContext(serviceContext),
                statusMapper,
                statusRepository,
                Instant::now);
    }

    private static StatusMapper loadStatusMapper(Context context) {
        try {
            return StatusMapper.fromJson(readAssetText(context, "status_map_cn.json"));
        } catch (IOException | RuntimeException exception) {
            return StatusMapper.fromJson("[]");
        }
    }

    private static String readAssetText(Context context, String assetName) throws IOException {
        try (InputStream stream = context.getAssets().open(assetName);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static final class NoOpStatusUploader implements StatusUploader {
        @Override
        public ApiResult<StatusModels.CurrentStatusResult> uploadCurrentStatus(
                AuthSession session,
                String statusCode,
                Instant statusUpdatedAt) {
            return ApiResult.error(new ApiError("supabase_not_configured", "Supabase is not configured"));
        }
    }
}
