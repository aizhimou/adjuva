package com.adjuva.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.adjuva.backend.model.entity.Project;

import java.util.List;
import java.util.Optional;

public interface ProjectMapper extends BaseMapper<Project> {

    List<Project> selectActiveProjects();

    Optional<Project> selectActiveBySlug(String slug);
}
