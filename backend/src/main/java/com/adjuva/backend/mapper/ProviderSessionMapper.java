package com.adjuva.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.adjuva.backend.model.entity.ProviderSession;

import java.util.Optional;

public interface ProviderSessionMapper extends BaseMapper<ProviderSession> {

    Optional<ProviderSession> selectByConversationProviderModel(String conversationId, String provider, String model);
}
