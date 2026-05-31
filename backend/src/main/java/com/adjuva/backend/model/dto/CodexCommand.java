package com.adjuva.backend.model.dto;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record CodexCommand(
        String bin,
        List<String> args,
        Path cwd,
        Map<String, String> env,
        String displayCommand
) {
}
