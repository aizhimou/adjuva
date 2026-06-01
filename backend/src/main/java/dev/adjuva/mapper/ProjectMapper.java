package dev.adjuva.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.adjuva.model.entity.Project;

import java.util.List;
import java.util.Optional;

public interface ProjectMapper extends BaseMapper<Project> {

    List<Project> selectActiveProjects();

    Optional<Project> selectActiveBySlug(String slug);
}
