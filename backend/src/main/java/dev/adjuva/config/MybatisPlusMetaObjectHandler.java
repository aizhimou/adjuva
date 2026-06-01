package dev.adjuva.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import java.time.Clock;
import java.time.Instant;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

@Component
public class MybatisPlusMetaObjectHandler implements MetaObjectHandler {

    private final Clock clock;

    public MybatisPlusMetaObjectHandler(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        Instant now = clock.instant();
        this.strictInsertFill(metaObject, "createdAt", Instant.class, now);
        this.strictInsertFill(metaObject, "updatedAt", Instant.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.setFieldValByName("updatedAt", clock.instant(), metaObject);
    }
}
