package com.hex1n.sofafacadedoc.git;

import com.hex1n.sofafacadedoc.config.AppConfig;
import com.hex1n.sofafacadedoc.config.AppConfigLoader;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class GitService {
    private final AppConfigLoader loader;

    public GitService(AppConfigLoader loader) {
        this.loader = loader;
    }

    public Worktree ensureWorktree(String project, String repo, String branch) throws Exception {
        git(null, "--version");
        Path dataDir = Paths.get(loader.current().server.dataDir);
        Path repoDir = dataDir.resolve("repos").resolve(safe(project));
        Path wt = dataDir.resolve("worktrees").resolve(safe(project)).resolve(safe(branch));
        ensureRepo(repoDir, repo);
        Files.createDirectories(wt.getParent());
        if (!Files.exists(wt.resolve(".git"))) {
            git(repoDir.toFile(), "worktree", "add", "--force", "--detach", wt.toString(), "origin/" + branch);
        } else {
            git(wt.toFile(), "fetch", "--prune", "origin");
            git(wt.toFile(), "checkout", "--force", "--detach", "origin/" + branch);
            git(wt.toFile(), "reset", "--hard", "origin/" + branch);
        }
        Worktree out = new Worktree();
        out.path = wt;
        out.commit = git(wt.toFile(), "rev-parse", "HEAD").trim();
        return out;
    }

    public List<String> resolveBranches(String project, AppConfig.ProjectConfig cfg) throws Exception {
        Path dataDir = Paths.get(loader.current().server.dataDir);
        Path repoDir = dataDir.resolve("repos").resolve(safe(project));
        ensureRepo(repoDir, cfg.repo);
        String refs = git(repoDir.toFile(), "for-each-ref", "--sort=-committerdate", "--format=%(refname:short)", "refs/remotes/origin");
        List<String> remoteBranches = new ArrayList<>();
        for (String line : refs.split("\n")) {
            line = line.trim();
            if (line.equals("origin/HEAD") || !line.startsWith("origin/")) continue;
            remoteBranches.add(line.substring("origin/".length()));
        }
        Set<String> out = new LinkedHashSet<>();
        for (String include : cfg.branches.include) {
            if (!isGlob(include) && remoteBranches.contains(include) && !excluded(include, cfg.branches.exclude)) {
                out.add(include);
            }
        }
        int wildcardCount = 0;
        int max = cfg.branches.maxMatched <= 0 ? 20 : cfg.branches.maxMatched;
        for (String branch : remoteBranches) {
            if (out.contains(branch) || excluded(branch, cfg.branches.exclude)) continue;
            for (String include : cfg.branches.include) {
                if (isGlob(include) && glob(include).matcher(branch).matches()) {
                    if (wildcardCount < max) {
                        out.add(branch);
                        wildcardCount++;
                    }
                    break;
                }
            }
        }
        return new ArrayList<>(out);
    }

    private void ensureRepo(Path repoDir, String repo) throws Exception {
        if (!Files.exists(repoDir.resolve(".git"))) {
            Files.createDirectories(repoDir.getParent());
            git(null, "clone", repo, repoDir.toString());
        }
        git(repoDir.toFile(), "fetch", "--prune", "origin");
    }

    public List<String> changedFiles(Path worktree, String from, String to) throws Exception {
        if (from == null || from.trim().isEmpty() || from.equals(to)) return Collections.emptyList();
        String out = git(worktree.toFile(), "diff", "--name-only", from + ".." + to);
        List<String> files = new ArrayList<>();
        for (String line : out.split("\n")) {
            line = line.trim();
            if (!line.isEmpty()) files.add(line.replace(File.separatorChar, '/'));
        }
        return files;
    }

    private String git(File dir, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        AppConfig.GitConfig git = loader.current().git;
        if (git.tokenEnv != null && !git.tokenEnv.trim().isEmpty()) {
            String token = System.getenv(git.tokenEnv);
            if (token != null && !token.trim().isEmpty()) {
                command.add("-c");
                command.add("http.extraHeader=Authorization: Bearer " + token);
            }
        }
        Collections.addAll(command, args);
        ProcessBuilder pb = new ProcessBuilder(command);
        if (dir != null) pb.directory(dir);
        if (git.sshKeyPath != null && !git.sshKeyPath.trim().isEmpty()) {
            pb.environment().put("GIT_SSH_COMMAND", "ssh -i " + git.sshKeyPath + " -o StrictHostKeyChecking=accept-new");
        }
        Process p = pb.start();
        byte[] out = readAll(p.getInputStream());
        byte[] err = readAll(p.getErrorStream());
        int code = p.waitFor();
        if (code != 0) throw new IllegalStateException("git " + String.join(" ", args) + ": " + new String(err, "UTF-8"));
        return new String(out, "UTF-8");
    }

    private byte[] readAll(java.io.InputStream in) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private String safe(String s) {
        return s.replace("/", "_").replace("\\", "_").replace("..", "_");
    }

    private boolean isGlob(String pattern) {
        return pattern.contains("*") || pattern.contains("?");
    }

    private boolean excluded(String branch, List<String> excludes) {
        for (String ex : excludes) {
            if (isGlob(ex) ? glob(ex).matcher(branch).matches() : ex.equals(branch)) return true;
        }
        return false;
    }

    private Pattern glob(String pattern) {
        StringBuilder re = new StringBuilder("^");
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '*') re.append(".*");
            else if (c == '?') re.append('.');
            else if ("\\.[]{}()+-^$|".indexOf(c) >= 0) re.append('\\').append(c);
            else re.append(c);
        }
        re.append('$');
        return Pattern.compile(re.toString());
    }

    public static class Worktree {
        public Path path;
        public String commit;
    }
}
