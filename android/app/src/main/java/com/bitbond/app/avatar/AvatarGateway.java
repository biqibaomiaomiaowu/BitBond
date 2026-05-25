package com.bitbond.app.avatar;

import com.bitbond.app.api.ApiResult;
import com.bitbond.app.auth.AuthSession;
import com.bitbond.app.avatar.AvatarModels.AvatarOption;

import java.util.List;

public interface AvatarGateway {
    ApiResult<List<AvatarOption>> listAvatars(AuthSession session);

    ApiResult<String> selectAvatar(AuthSession session, String avatarId);
}
