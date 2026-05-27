package com.bitbond.app.widget;

import android.content.Context;

import com.bitbond.app.MainActivity;
import com.bitbond.app.api.ApiResult;
import com.bitbond.app.api.HttpSupabaseRpcClient;
import com.bitbond.app.api.Transport;
import com.bitbond.app.auth.AuthGateway;
import com.bitbond.app.auth.AuthRepository;
import com.bitbond.app.auth.AuthSession;
import com.bitbond.app.auth.SessionStore;
import com.bitbond.app.config.SupabaseConfig;

public final class WidgetStatusRefreshCoordinator {
    private WidgetStatusRefreshCoordinator() {
    }

    public static boolean refreshCache(AuthGateway authGateway, WidgetStatusGateway widgetStatusGateway, WidgetStatusCache cache) {
        try {
            ApiResult<AuthSession> sessionResult = authGateway.ensureSession();
            if (!sessionResult.isSuccess()) {
                return false;
            }

            ApiResult<WidgetStatusSnapshot> statusResult = widgetStatusGateway.getWidgetStatus(sessionResult.value());
            if (!statusResult.isSuccess()) {
                return false;
            }

            cache.write(statusResult.value());
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public static boolean refreshFromNetwork(Context context) {
        SupabaseConfig.Values config = SupabaseConfig.current();
        if (!config.isConfigured()) {
            return false;
        }

        try {
            Transport transport = new MainActivity.UrlConnectionTransport();
            HttpSupabaseRpcClient rpcClient = new HttpSupabaseRpcClient(config.url(), config.anonKey(), transport);
            SessionStore sessionStore = new SessionStore();
            AuthRepository authRepository = new AuthRepository(
                    config.url(),
                    config.anonKey(),
                    transport,
                    rpcClient,
                    sessionStore);
            AuthGateway authGateway = MainActivity.persistingAuthGateway(
                    authRepository,
                    sessionStore,
                    new MainActivity.SharedPreferencesSessionPersistence(
                            context.getSharedPreferences(MainActivity.AUTH_PREFS_NAME, Context.MODE_PRIVATE)));
            return refreshCache(authGateway, new WidgetStatusRepository(rpcClient), new WidgetStatusCache(context));
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
