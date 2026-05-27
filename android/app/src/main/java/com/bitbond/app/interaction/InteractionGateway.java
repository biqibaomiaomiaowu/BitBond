package com.bitbond.app.interaction;

import com.bitbond.app.api.ApiResult;
import com.bitbond.app.auth.AuthSession;
import com.bitbond.app.interaction.InteractionModels.HeartInteraction;
import com.bitbond.app.interaction.InteractionModels.InteractionList;
import com.bitbond.app.interaction.InteractionModels.MarkSeenResult;

import java.util.List;

public interface InteractionGateway {
    ApiResult<HeartInteraction> sendHeart(AuthSession session);

    ApiResult<InteractionList> getLatestInteractions(AuthSession session);

    ApiResult<MarkSeenResult> markInteractionsSeen(AuthSession session, List<String> interactionIds);
}
