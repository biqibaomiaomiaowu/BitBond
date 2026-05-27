package com.bitbond.app.bridge;

import android.webkit.JavascriptInterface;

import com.bitbond.app.account.AccountGateway;
import com.bitbond.app.account.AccountModels.DeleteAccountResult;
import com.bitbond.app.analytics.AnalyticsGateway;
import com.bitbond.app.analytics.AnalyticsModels.AnalyticsEventResult;
import com.bitbond.app.api.ApiError;
import com.bitbond.app.api.ApiResult;
import com.bitbond.app.auth.AuthGateway;
import com.bitbond.app.auth.AuthSession;
import com.bitbond.app.avatar.AvatarGateway;
import com.bitbond.app.avatar.AvatarModels.AvatarOption;
import com.bitbond.app.interaction.InteractionGateway;
import com.bitbond.app.interaction.InteractionModels.HeartInteraction;
import com.bitbond.app.interaction.InteractionModels.InteractionList;
import com.bitbond.app.interaction.InteractionModels.MarkSeenResult;
import com.bitbond.app.pairing.PairingGateway;
import com.bitbond.app.pairing.PairingModels.CoupleState;
import com.bitbond.app.pairing.PairingModels.PairInvite;
import com.bitbond.app.pairing.PairingModels.PartnerProfile;
import com.bitbond.app.privacy.PrivacyGateway;
import com.bitbond.app.privacy.PrivacyModels;
import com.bitbond.app.privacy.PrivacyModels.PrivacySettings;
import com.bitbond.app.sharing.SharingGateway;
import com.bitbond.app.sharing.SharingModels.SharingState;
import com.bitbond.app.status.DebugForegroundGateway;
import com.bitbond.app.status.StatusGateway;
import com.bitbond.app.status.StatusModels.PartnerStatus;
import com.bitbond.app.status.StatusUploadTrigger;
import com.bitbond.app.status.AccessibilityAccessGateway;
import com.bitbond.app.status.BatteryOptimizationGateway;
import com.bitbond.app.status.UsageAccessGateway;
import com.bitbond.app.widget.WidgetStatusSink;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class BitBondBridgeController {
    private final AuthGateway auth;
    private final PairingGateway pairing;
    private final AvatarGateway avatar;
    private final StatusGateway status;
    private final SharingGateway sharing;
    private final PrivacyGateway privacy;
    private final InteractionGateway interaction;
    private final AccountGateway account;
    private final AnalyticsGateway analytics;
    private final StatusUploadTrigger uploadTrigger;
    private final UsageAccessGateway usageAccess;
    private final AccessibilityAccessGateway accessibilityAccess;
    private final BatteryOptimizationGateway batteryOptimization;
    private final DebugForegroundGateway debugForeground;
    private final WidgetStatusSink widgetStatusSink;

    public BitBondBridgeController(
            AuthGateway auth,
            PairingGateway pairing,
            AvatarGateway avatar,
            StatusGateway status,
            StatusUploadTrigger uploadTrigger,
            UsageAccessGateway usageAccess,
            AccessibilityAccessGateway accessibilityAccess,
            BatteryOptimizationGateway batteryOptimization,
            DebugForegroundGateway debugForeground
    ) {
        this(
                auth,
                pairing,
                avatar,
                status,
                new UnavailableSharingGateway(),
                new UnavailablePrivacyGateway(),
                new UnavailableInteractionGateway(),
                new UnavailableAccountGateway(),
                new UnavailableAnalyticsGateway(),
                uploadTrigger,
                usageAccess,
                accessibilityAccess,
                batteryOptimization,
                debugForeground,
                WidgetStatusSink.noop());
    }

    public BitBondBridgeController(
            AuthGateway auth,
            PairingGateway pairing,
            AvatarGateway avatar,
            StatusGateway status,
            SharingGateway sharing,
            PrivacyGateway privacy,
            InteractionGateway interaction,
            AccountGateway account,
            AnalyticsGateway analytics,
            StatusUploadTrigger uploadTrigger,
            UsageAccessGateway usageAccess,
            AccessibilityAccessGateway accessibilityAccess,
            BatteryOptimizationGateway batteryOptimization,
            DebugForegroundGateway debugForeground
    ) {
        this(
                auth,
                pairing,
                avatar,
                status,
                sharing,
                privacy,
                interaction,
                account,
                analytics,
                uploadTrigger,
                usageAccess,
                accessibilityAccess,
                batteryOptimization,
                debugForeground,
                WidgetStatusSink.noop());
    }

    public BitBondBridgeController(
            AuthGateway auth,
            PairingGateway pairing,
            AvatarGateway avatar,
            StatusGateway status,
            SharingGateway sharing,
            PrivacyGateway privacy,
            InteractionGateway interaction,
            AccountGateway account,
            AnalyticsGateway analytics,
            StatusUploadTrigger uploadTrigger,
            UsageAccessGateway usageAccess,
            AccessibilityAccessGateway accessibilityAccess,
            BatteryOptimizationGateway batteryOptimization,
            DebugForegroundGateway debugForeground,
            WidgetStatusSink widgetStatusSink
    ) {
        this.auth = Objects.requireNonNull(auth, "auth");
        this.pairing = Objects.requireNonNull(pairing, "pairing");
        this.avatar = Objects.requireNonNull(avatar, "avatar");
        this.status = Objects.requireNonNull(status, "status");
        this.sharing = Objects.requireNonNull(sharing, "sharing");
        this.privacy = Objects.requireNonNull(privacy, "privacy");
        this.interaction = Objects.requireNonNull(interaction, "interaction");
        this.account = Objects.requireNonNull(account, "account");
        this.analytics = Objects.requireNonNull(analytics, "analytics");
        this.uploadTrigger = Objects.requireNonNull(uploadTrigger, "uploadTrigger");
        this.usageAccess = Objects.requireNonNull(usageAccess, "usageAccess");
        this.accessibilityAccess = Objects.requireNonNull(accessibilityAccess, "accessibilityAccess");
        this.batteryOptimization = Objects.requireNonNull(batteryOptimization, "batteryOptimization");
        this.debugForeground = Objects.requireNonNull(debugForeground, "debugForeground");
        this.widgetStatusSink = Objects.requireNonNull(widgetStatusSink, "widgetStatusSink");
    }

    @JavascriptInterface
    public String ping() {
        return "Android WebView bridge connected";
    }

    @JavascriptInterface
    public String getInitialState() {
        try {
            ApiResult<AuthSession> sessionResult = auth.ensureSession();
            if (!sessionResult.isSuccess()) {
                return connectedOfflineInitialStateJson();
            }

            AuthSession session = sessionResult.value();
            ApiResult<CoupleState> result = pairing.getCurrentCouple(session);
            if (!result.isSuccess()) {
                return connectedOfflineInitialStateJson();
            }

            return initialStateJson(result.value(), initialSharingState(session)).toString();
        } catch (JSONException | RuntimeException exception) {
            return safeInitialStateJson("Bridge 初始化失败，已切换浏览器预览");
        }
    }

    @JavascriptInterface
    public String checkUsageAccess() {
        return authenticated(session -> BridgeResponse.success(new JSONObject()
                .put("hasUsageAccess", usageAccess.hasUsageAccess())));
    }

    @JavascriptInterface
    public String openUsageAccessSettings() {
        return authenticated(session -> {
            usageAccess.openUsageAccessSettings();
            return BridgeResponse.success(new JSONObject().put("opened", true));
        });
    }

    @JavascriptInterface
    public String checkAccessibilityAccess() {
        return authenticated(session -> BridgeResponse.success(new JSONObject()
                .put("hasAccessibilityAccess", accessibilityAccess.hasAccessibilityAccess())));
    }

    @JavascriptInterface
    public String openAccessibilitySettings() {
        return authenticated(session -> {
            accessibilityAccess.openAccessibilitySettings();
            return BridgeResponse.success(new JSONObject().put("opened", true));
        });
    }

    @JavascriptInterface
    public String checkBatteryOptimization() {
        return authenticated(session -> BridgeResponse.success(new JSONObject()
                .put("isIgnoringBatteryOptimizations", batteryOptimization.isIgnoringBatteryOptimizations())));
    }

    @JavascriptInterface
    public String openBatteryOptimizationSettings() {
        return authenticated(session -> {
            batteryOptimization.openBatteryOptimizationSettings();
            return BridgeResponse.success(new JSONObject().put("opened", true));
        });
    }

    @JavascriptInterface
    public String getDebugForegroundApp() {
        return authenticated(session -> {
            JSONObject result = debugForeground.debugForegroundApp();
            return BridgeResponse.success(result == null ? new JSONObject() : new JSONObject(result.toString()));
        });
    }

    @JavascriptInterface
    public String createPairInvite() {
        return authenticated(session -> {
            ApiResult<PairInvite> result = pairing.createInvite(session);
            if (!result.isSuccess()) {
                return error(result.error());
            }

            return BridgeResponse.success(pairInviteJson(result.value()));
        });
    }

    @JavascriptInterface
    public String acceptPairInvite(String payload) {
        return authenticated(session -> {
            String code = trimmedStringPayload(payload, "code");
            if (code.isEmpty()) {
                return BridgeResponse.error("invalid_invite_code", "Invite code is required");
            }

            ApiResult<CoupleState> result = pairing.acceptInvite(session, code);
            if (!result.isSuccess()) {
                return error(result.error());
            }

            return BridgeResponse.success(coupleStateJson(result.value()));
        });
    }

    @JavascriptInterface
    public String unlink() {
        return authenticated(session -> {
            ApiResult<Boolean> result = pairing.unlink(session);
            if (!result.isSuccess()) {
                return error(result.error());
            }

            return BridgeResponse.success(new JSONObject().put("unlinked", result.value()));
        });
    }

    @JavascriptInterface
    public String unlink(String payload) {
        return unlink();
    }

    @JavascriptInterface
    public String listAvatars() {
        return authenticated(session -> {
            ApiResult<List<AvatarOption>> result = avatar.listAvatars(session);
            if (!result.isSuccess()) {
                return error(result.error());
            }

            return BridgeResponse.success(new JSONObject().put("avatars", avatarArrayJson(result.value())));
        });
    }

    @JavascriptInterface
    public String selectAvatar(String payload) {
        return authenticated(session -> {
            String avatarId = trimmedStringPayload(payload, "avatarId");
            if (avatarId.isEmpty()) {
                return BridgeResponse.error("invalid_avatar_id", "Avatar id is required");
            }

            ApiResult<String> result = avatar.selectAvatar(session, avatarId);
            if (!result.isSuccess()) {
                return error(result.error());
            }

            return BridgeResponse.success(new JSONObject().put("avatarId", result.value()));
        });
    }

    @JavascriptInterface
    public String refreshPartnerStatus() {
        return uploadThenFetchPartnerStatus(false);
    }

    @JavascriptInterface
    public String uploadCurrentStatus() {
        return authenticated(session -> {
            ApiResult<String> result = uploadTrigger.uploadDetectedStatus(session);
            if (!result.isSuccess()) {
                return error(result.error());
            }

            return BridgeResponse.success(new JSONObject().put("code", result.value()));
        });
    }

    @JavascriptInterface
    public String refreshHomeState() {
        return uploadThenFetchPartnerStatus(true);
    }

    @JavascriptInterface
    public String pauseSharing() {
        return setSharingPaused(true);
    }

    @JavascriptInterface
    public String resumeSharing() {
        return setSharingPaused(false);
    }

    @JavascriptInterface
    public String getPrivacySettings() {
        return authenticated(session -> {
            ApiResult<PrivacySettings> result = privacy.getSettings(session);
            if (!result.isSuccess()) {
                return error(result.error());
            }

            return BridgeResponse.success(new JSONObject(result.value().rawJson()));
        });
    }

    @JavascriptInterface
    public String updatePrivacySettings(String payload) {
        return authenticated(session -> {
            List<String> allowedStatuses;
            try {
                allowedStatuses = PrivacyModels.sanitizeAllowedStatuses(stringArrayPayload(payload, "allowedStatuses"));
            } catch (IllegalArgumentException exception) {
                return BridgeResponse.error("invalid_status_code", "Status code is invalid");
            }

            ApiResult<PrivacySettings> result = privacy.updateSettings(session, allowedStatuses);
            if (!result.isSuccess()) {
                return error(result.error());
            }

            return BridgeResponse.success(new JSONObject(result.value().rawJson()));
        });
    }

    @JavascriptInterface
    public String sendHeart() {
        return authenticated(session -> {
            ApiResult<HeartInteraction> result = interaction.sendHeart(session);
            if (!result.isSuccess()) {
                return error(result.error());
            }

            return BridgeResponse.success(new JSONObject(result.value().rawJson()));
        });
    }

    @JavascriptInterface
    public String sendHeart(String payload) {
        return sendHeart();
    }

    @JavascriptInterface
    public String getLatestInteractions() {
        return authenticated(session -> {
            ApiResult<InteractionList> result = interaction.getLatestInteractions(session);
            if (!result.isSuccess()) {
                return error(result.error());
            }

            return BridgeResponse.success(new JSONObject(result.value().rawJson()));
        });
    }

    @JavascriptInterface
    public String markInteractionsSeen(String payload) {
        return authenticated(session -> {
            List<String> interactionIds = stringArrayPayload(payload, "interactionIds");
            if (interactionIds.isEmpty()) {
                return BridgeResponse.error("invalid_interaction_ids", "Interaction ids are required");
            }

            ApiResult<MarkSeenResult> result = interaction.markInteractionsSeen(session, interactionIds);
            if (!result.isSuccess()) {
                return error(result.error());
            }

            return BridgeResponse.success(new JSONObject(result.value().rawJson()));
        });
    }

    @JavascriptInterface
    public String deleteAccount(String payload) {
        if (!confirmedPayload(payload)) {
            return BridgeResponse.error("invalid_confirmation", "Deletion must be confirmed").toJson();
        }

        return authenticated(session -> {
            ApiResult<DeleteAccountResult> result = account.deleteAccount(session);
            if (!result.isSuccess()) {
                return error(result.error());
            }

            return BridgeResponse.success(new JSONObject(result.value().rawJson()));
        });
    }

    @JavascriptInterface
    public String recordAnalyticsEvent(String payload) {
        return authenticated(session -> {
            JSONObject source = objectPayload(payload);
            String eventName = trimmedString(source, "eventName");
            if (eventName.isEmpty()) {
                return BridgeResponse.error("invalid_event_name", "Event name is required");
            }

            JSONObject properties = source.optJSONObject("properties");
            ApiResult<AnalyticsEventResult> result = analytics.recordEvent(
                    session,
                    eventName,
                    properties == null ? new JSONObject() : properties);
            if (!result.isSuccess()) {
                return error(result.error());
            }

            return BridgeResponse.success(new JSONObject(result.value().rawJson()));
        });
    }

    private String uploadThenFetchPartnerStatus(boolean compactPartnerStatus) {
        return authenticated(session -> {
            ApiResult<String> uploadResult = uploadTrigger.uploadDetectedStatus(session);
            if (!uploadResult.isSuccess()) {
                return error(uploadResult.error());
            }

            ApiResult<PartnerStatus> statusResult = status.getPartnerStatus(session);
            if (!statusResult.isSuccess()) {
                return error(statusResult.error());
            }

            widgetStatusSink.cachePartnerStatus(statusResult.value());
            return BridgeResponse.success(uploadPartnerStatusJson(
                    uploadResult.value(),
                    statusResult.value(),
                    compactPartnerStatus));
        });
    }

    private String setSharingPaused(boolean paused) {
        return authenticated(session -> {
            ApiResult<SharingState> result = sharing.setSharingPaused(session, paused);
            if (!result.isSuccess()) {
                return error(result.error());
            }

            return BridgeResponse.success(new JSONObject(result.value().rawJson()));
        });
    }

    private String authenticated(AuthenticatedOperation operation) {
        try {
            ApiResult<AuthSession> sessionResult = auth.ensureSession();
            if (!sessionResult.isSuccess()) {
                return error(sessionResult.error()).toJson();
            }

            return operation.run(sessionResult.value()).toJson();
        } catch (JSONException | RuntimeException exception) {
            return BridgeResponse.error("bridge_error", "Bridge request failed").toJson();
        }
    }

    private static BridgeResponse error(ApiError error) {
        if (error == null) {
            return BridgeResponse.error("bridge_error", "Bridge request failed");
        }

        String code = normalized(error.code(), "bridge_error");
        String message = normalized(error.message(), "Request failed");
        return BridgeResponse.error(code, message);
    }

    private static JSONObject pairInviteJson(PairInvite invite) throws JSONException {
        return new JSONObject()
                .put("code", invite.code())
                .put("expiresAt", invite.expiresAt());
    }

    private static JSONObject coupleStateJson(CoupleState state) throws JSONException {
        JSONObject result = new JSONObject().put("paired", state.paired());
        if (!state.paired()) {
            return result;
        }

        return result
                .put("coupleId", state.coupleId())
                .put("partner", partnerProfileJson(state.partner()));
    }

    private SharingState initialSharingState(AuthSession session) {
        ApiResult<SharingState> result = sharing.getSharingState(session);
        if (!result.isSuccess()) {
            return defaultSharingState();
        }
        return result.value();
    }

    private static SharingState defaultSharingState() {
        return new SharingState(
                true,
                "online",
                "{\"sharing\":true,\"statusCode\":\"online\",\"statusUpdatedAt\":null,\"expiresAt\":null,\"isPaused\":false}");
    }

    private static JSONObject initialStateJson(CoupleState coupleState, SharingState sharingState) throws JSONException {
        boolean paired = coupleState.paired();
        String nickname = paired && coupleState.partner() != null
                ? normalized(coupleState.partner().nickname(), "")
                : "";
        boolean sharingEnabled = sharingState == null || sharingState.sharing();
        return initialStateJson(
                true,
                "Android WebView bridge connected",
                paired,
                nickname,
                paired ? "等待对方状态" : "未配对",
                sharingEnabled,
                sharingEnabled ? "你正在共享抽象状态" : "已暂停共享");
    }

    private static String safeInitialStateJson(String message) {
        try {
            return initialStateJson(false, message, false, "", "未配对", true, "你正在共享抽象状态").toString();
        } catch (JSONException exception) {
            return "{\"bridge\":{\"ready\":false,\"message\":\"Bridge 初始化失败，已切换浏览器预览\"},\"self\":{\"sharing\":true,\"statusText\":\"你正在共享抽象状态\"},\"pair\":{\"paired\":false,\"nickname\":\"\"},\"partner\":{\"statusCode\":\"offline\",\"updatedAt\":\"未配对\",\"areaLabel\":\"门口\"}}";
        }
    }

    private static String connectedOfflineInitialStateJson() {
        try {
            return initialStateJson(true, "Android WebView bridge connected", false, "", "未配对", true, "你正在共享抽象状态").toString();
        } catch (JSONException exception) {
            return safeInitialStateJson("Bridge 初始化失败，已切换浏览器预览");
        }
    }

    private static JSONObject initialStateJson(
            boolean bridgeReady,
            String bridgeMessage,
            boolean paired,
            String nickname,
            String updatedAt,
            boolean sharing,
            String statusText
    ) throws JSONException {
        return new JSONObject()
                .put("bridge", new JSONObject()
                        .put("ready", bridgeReady)
                .put("message", bridgeMessage))
                .put("self", new JSONObject()
                        .put("sharing", sharing)
                        .put("statusText", statusText))
                .put("pair", new JSONObject()
                        .put("paired", paired)
                        .put("nickname", nickname))
                .put("partner", new JSONObject()
                        .put("statusCode", "offline")
                        .put("updatedAt", updatedAt)
                        .put("areaLabel", "门口"));
    }

    private static JSONObject partnerProfileJson(PartnerProfile partner) throws JSONException {
        if (partner == null) {
            return new JSONObject();
        }

        return new JSONObject()
                .put("nickname", jsonValue(partner.nickname()))
                .put("avatarId", jsonValue(partner.avatarId()));
    }

    private static JSONArray avatarArrayJson(List<AvatarOption> avatars) throws JSONException {
        JSONArray result = new JSONArray();
        for (AvatarOption option : avatars) {
            result.put(new JSONObject()
                    .put("id", option.id())
                    .put("name", option.name())
                    .put("assetKey", option.assetKey()));
        }
        return result;
    }

    private static JSONObject partnerStatusJson(PartnerStatus partnerStatus) throws JSONException {
        String rawJson = partnerStatus.rawJson();
        if (rawJson != null && !rawJson.trim().isEmpty()) {
            return new JSONObject(rawJson);
        }

        JSONObject result = new JSONObject().put("paired", partnerStatus.isPaired());
        if (!partnerStatus.isPaired()) {
            return result;
        }

        JSONObject partner = new JSONObject();
        if (partnerStatus.partner() != null) {
            partner
                    .put("nickname", jsonValue(partnerStatus.partner().nickname()))
                    .put("avatarId", jsonValue(partnerStatus.partner().avatarId()));
        }

        return result
                .put("partner", partner)
                .put("statusCode", jsonValue(partnerStatus.statusCode()))
                .put("statusUpdatedAt", jsonValue(partnerStatus.statusUpdatedAt()))
                .put("expiresAt", jsonValue(partnerStatus.expiresAt()))
                .put("isPaused", partnerStatus.isPaused());
    }

    private static JSONObject compactPartnerStatusJson(PartnerStatus partnerStatus) throws JSONException {
        return new JSONObject().put("paired", partnerStatus.isPaired());
    }

    private static JSONObject uploadPartnerStatusJson(
            String uploadCode,
            PartnerStatus partnerStatus,
            boolean compactPartnerStatus
    ) throws JSONException {
        String rawJson = "{\"upload\":{\"code\":"
                + JSONObject.quote(uploadCode)
                + "},\"partnerStatus\":"
                + (compactPartnerStatus ? compactPartnerStatusJson(partnerStatus) : partnerStatusJson(partnerStatus))
                + "}";
        return new StableWireJsonObject(rawJson);
    }

    private static String trimmedStringPayload(String rawPayload, String key) {
        if (rawPayload == null || rawPayload.trim().isEmpty()) {
            return "";
        }

        try {
            JSONObject payload = new JSONObject(rawPayload);
            if (!payload.has(key) || payload.isNull(key) || !(payload.opt(key) instanceof String)) {
                return "";
            }

            return payload.getString(key).trim();
        } catch (JSONException exception) {
            return "";
        }
    }

    private static JSONObject objectPayload(String rawPayload) throws JSONException {
        if (rawPayload == null || rawPayload.trim().isEmpty()) {
            return new JSONObject();
        }
        return new JSONObject(rawPayload);
    }

    private static String trimmedString(JSONObject payload, String key) {
        if (!payload.has(key) || payload.isNull(key) || !(payload.opt(key) instanceof String)) {
            return "";
        }
        return payload.optString(key, "").trim();
    }

    private static List<String> stringArrayPayload(String rawPayload, String key) throws JSONException {
        JSONObject payload = objectPayload(rawPayload);
        JSONArray array = payload.optJSONArray(key);
        List<String> result = new ArrayList<>();
        if (array == null) {
            return result;
        }
        for (int i = 0; i < array.length(); i++) {
            Object value = array.opt(i);
            if (value instanceof String) {
                String normalized = ((String) value).trim();
                if (!normalized.isEmpty()) {
                    result.add(normalized);
                }
            }
        }
        return result;
    }

    private static boolean confirmedPayload(String rawPayload) {
        try {
            return objectPayload(rawPayload).optBoolean("confirmed", false);
        } catch (JSONException exception) {
            return false;
        }
    }

    private static Object jsonValue(Object value) {
        return value == null ? JSONObject.NULL : value;
    }

    private static String normalized(String value, String fallback) {
        if (value == null) {
            return fallback;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? fallback : normalized;
    }

    private static <T> ApiResult<T> unavailableResult() {
        return ApiResult.error(new ApiError("bridge_unavailable", "Bridge is unavailable"));
    }

    private static final class UnavailableSharingGateway implements SharingGateway {
        @Override
        public ApiResult<SharingState> getSharingState(AuthSession session) {
            return unavailableResult();
        }

        @Override
        public ApiResult<SharingState> setSharingPaused(AuthSession session, boolean paused) {
            return unavailableResult();
        }
    }

    private static final class UnavailablePrivacyGateway implements PrivacyGateway {
        @Override
        public ApiResult<PrivacySettings> getSettings(AuthSession session) {
            return unavailableResult();
        }

        @Override
        public ApiResult<PrivacySettings> updateSettings(AuthSession session, List<String> allowedStatuses) {
            return unavailableResult();
        }
    }

    private static final class UnavailableInteractionGateway implements InteractionGateway {
        @Override
        public ApiResult<HeartInteraction> sendHeart(AuthSession session) {
            return unavailableResult();
        }

        @Override
        public ApiResult<InteractionList> getLatestInteractions(AuthSession session) {
            return unavailableResult();
        }

        @Override
        public ApiResult<MarkSeenResult> markInteractionsSeen(AuthSession session, List<String> interactionIds) {
            return unavailableResult();
        }
    }

    private static final class UnavailableAccountGateway implements AccountGateway {
        @Override
        public ApiResult<DeleteAccountResult> deleteAccount(AuthSession session) {
            return unavailableResult();
        }
    }

    private static final class UnavailableAnalyticsGateway implements AnalyticsGateway {
        @Override
        public ApiResult<AnalyticsEventResult> recordEvent(AuthSession session, String eventName, JSONObject properties) {
            return unavailableResult();
        }
    }

    private interface AuthenticatedOperation {
        BridgeResponse run(AuthSession session) throws JSONException;
    }

    private static final class StableWireJsonObject extends JSONObject {
        private final String rawJson;

        private StableWireJsonObject(String rawJson) {
            this.rawJson = rawJson;
        }

        @Override
        public String toString() {
            return rawJson;
        }
    }
}
