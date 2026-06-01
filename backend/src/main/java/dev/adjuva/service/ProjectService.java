package dev.adjuva.service;

import dev.adjuva.mapper.ProjectMapper;
import dev.adjuva.model.request.CreateProjectRequest;
import dev.adjuva.model.entity.Project;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ProjectService {

    private static final String ACTIVE = "active";

    private final ProjectMapper projectMapper;

    public ProjectService(ProjectMapper projectMapper) {
        this.projectMapper = projectMapper;
    }

    public List<Project> listActive() {
        return projectMapper.selectActiveProjects();
    }

    public Project getActiveBySlug(String slug) {
        return projectMapper.selectActiveBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Project not found"));
    }

    public Project getActive(String id) {
        Project project = projectMapper.selectById(id);
        if (project == null || !ACTIVE.equals(project.getStatus())) {
            throw new ResponseStatusException(NOT_FOUND, "Project not found");
        }
        return project;
    }

    @Transactional
    public Project create(CreateProjectRequest request) {
        validate(request);

        Project project = new Project();
        project.setName(request.name().trim());
        project.setSlug(request.slug().trim());
        project.setDescription(trimToNull(request.description()));
        project.setStatus(ACTIVE);
        project.setWorkspacePath(request.workspacePath().trim());
        project.setDefaultProvider(trimToNull(request.defaultProvider()));
        project.setDefaultModel(trimToNull(request.defaultModel()));

        projectMapper.insert(project);
        return project;
    }

    private void validate(CreateProjectRequest request) {
        if (request == null
                || !StringUtils.hasText(request.name())
                || !StringUtils.hasText(request.slug())
                || !StringUtils.hasText(request.workspacePath())) {
            throw new ResponseStatusException(BAD_REQUEST, "name, slug, and workspacePath are required");
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
