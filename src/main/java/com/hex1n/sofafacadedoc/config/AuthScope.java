package com.hex1n.sofafacadedoc.config;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class AuthScope {
    public boolean admin;
    public Map<String, Boolean> projects = new LinkedHashMap<>();
    public Set<String> teams = new LinkedHashSet<>();

    public boolean canProject(String project) {
        return admin || Boolean.TRUE.equals(projects.get(project));
    }

    public boolean canTeam(String team) {
        return admin || (team != null && teams.contains(team));
    }

    public boolean canManageConfig() {
        return admin || !teams.isEmpty();
    }
}
