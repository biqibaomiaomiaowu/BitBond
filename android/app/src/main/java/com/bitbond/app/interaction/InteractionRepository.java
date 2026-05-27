package com.bitbond.app.interaction;

import com.bitbond.app.api.ApiError;
import com.bitbond.app.api.ApiResult;
import com.bitbond.app.api.SupabaseRpcClient;
import com.bitbond.app.auth.AuthSession;
import com.bitbond.app.interaction.InteractionModels.HeartInteraction;
import com.bitbond.app.interaction.InteractionModels.InteractionList;
import com.bitbond.app.interaction.InteractionModels.MarkSeenResult;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class InteractionRepository implements InteractionGateway {
    private static final String SEND_HEART_RPC = "send_heart_interaction";
    private static final String GET_LATEST_INTERACTIONS_RPC = "get_latest_interactions";
    private static final String MARK_INTERACTIONS_SEEN_RPC = "mark_interactions_seen";

    private final SupabaseRpcClient rpcClient;

    public InteractionRepository(SupabaseRpcClient rpcClient) {
        this.rpcClient = Objects.requireNonNull(rpcClient, "rpcClient");
    }

    @Override
    public ApiResult<HeartInteraction> sendHeart(AuthSession session) {
        Objects.requireNonNull(session, "session");

        try {
            ApiResult<JSONObject> result = rpcClient.rpc(
                    session.accessToken(),
                    SEND_HEART_RPC,
                    new JSONObject());
            if (!result.isSuccess()) {
                return ApiResult.error(result.error());
            }

            return ApiResult.success(parseSendHeartResponse(result.value()));
        } catch (JSONException | IllegalArgumentException exception) {
            return ApiResult.error(new ApiError("interaction_json_error", "Interaction response could not be parsed"));
        }
    }

    @Override
    public ApiResult<InteractionList> getLatestInteractions(AuthSession session) {
        Objects.requireNonNull(session, "session");

        try {
            ApiResult<JSONObject> result = rpcClient.rpc(
                    session.accessToken(),
                    GET_LATEST_INTERACTIONS_RPC,
                    new JSONObject());
            if (!result.isSuccess()) {
                return ApiResult.error(result.error());
            }

            return ApiResult.success(parseInteractionList(result.value()));
        } catch (JSONException | IllegalArgumentException exception) {
            return ApiResult.error(new ApiError("interaction_json_error", "Interaction response could not be parsed"));
        }
    }

    @Override
    public ApiResult<MarkSeenResult> markInteractionsSeen(AuthSession session, List<String> interactionIds) {
        Objects.requireNonNull(session, "session");

        List<String> normalizedIds = normalizeIds(interactionIds);
        if (normalizedIds.isEmpty()) {
            return ApiResult.error(new ApiError("invalid_interaction_ids", "Interaction ids are required"));
        }

        try {
            ApiResult<JSONObject> result = rpcClient.rpc(
                    session.accessToken(),
                    MARK_INTERACTIONS_SEEN_RPC,
                    new JSONObject().put("next_interaction_ids", stringArray(normalizedIds)));
            if (!result.isSuccess()) {
                return ApiResult.error(result.error());
            }

            int markedCount = result.value().getInt("markedCount");
            return ApiResult.success(new MarkSeenResult(
                    markedCount,
                    new JSONObject().put("markedCount", markedCount).toString()));
        } catch (JSONException | IllegalArgumentException exception) {
            return ApiResult.error(new ApiError("interaction_json_error", "Interaction response could not be parsed"));
        }
    }

    private static InteractionList parseInteractionList(JSONObject source) throws JSONException {
        JSONArray entries = source.getJSONArray("interactions");
        List<HeartInteraction> interactions = new ArrayList<>();
        JSONArray publicEntries = new JSONArray();
        for (int i = 0; i < entries.length(); i++) {
            HeartInteraction interaction = parseBackendInteraction(entries.getJSONObject(i));
            interactions.add(interaction);
            publicEntries.put(new JSONObject(interaction.rawJson()));
        }

        return new InteractionList(
                interactions,
                new JSONObject().put("interactions", publicEntries).toString());
    }

    private static HeartInteraction parseSendHeartResponse(JSONObject source) throws JSONException {
        return parseBackendInteraction(source.getJSONObject("interaction"));
    }

    private static HeartInteraction parseBackendInteraction(JSONObject source) throws JSONException {
        String interactionId = source.getString("id").trim();
        String type = source.getString("type").trim();
        String createdAt = source.getString("createdAt").trim();
        boolean seen = false;
        JSONObject publicJson = new JSONObject()
                .put("interactionId", interactionId)
                .put("type", type)
                .put("createdAt", createdAt)
                .put("seen", seen);
        return new HeartInteraction(interactionId, type, createdAt, seen, publicJson.toString());
    }

    private static List<String> normalizeIds(List<String> ids) {
        Objects.requireNonNull(ids, "ids");

        Set<String> result = new LinkedHashSet<>();
        for (String id : ids) {
            if (id == null) {
                continue;
            }
            String normalized = id.trim();
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }
        return new ArrayList<>(result);
    }

    private static JSONArray stringArray(List<String> values) {
        JSONArray result = new JSONArray();
        for (String value : values) {
            result.put(value);
        }
        return result;
    }
}
