package dev.adjuva.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.adjuva.model.entity.ProviderSession;

import java.util.Optional;

public interface ProviderSessionMapper extends BaseMapper<ProviderSession> {

    Optional<ProviderSession> selectByConversationProviderModel(String conversationId, String provider, String model);
}
