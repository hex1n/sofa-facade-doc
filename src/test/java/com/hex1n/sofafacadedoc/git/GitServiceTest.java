package com.hex1n.sofafacadedoc.git;

import com.hex1n.sofafacadedoc.config.AppConfigLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitServiceTest {
    @TempDir
    Path temp;

    @Test
    void resolvesFixedAndWildcardBranchesWithExclude() throws Exception {
        Path repo = temp.resolve("repo");
        Files.createDirectories(repo);
        git(repo, "init", "-b", "main");
        Files.write(repo.resolve("README.md"), "main".getBytes(StandardCharsets.UTF_8));
        git(repo, "add", ".");
        git(repo, "-c", "user.name=test", "-c", "user.email=test@example.com", "commit", "-m", "main");
        git(repo, "checkout", "-b", "feature/apply-flow");
        Files.write(repo.resolve("feature.txt"), "feature".getBytes(StandardCharsets.UTF_8));
        git(repo, "add", ".");
        git(repo, "-c", "user.name=test", "-c", "user.email=test@example.com", "commit", "-m", "feature");
        git(repo, "checkout", "-b", "feature/wip-skip");
        Files.write(repo.resolve("wip.txt"), "wip".getBytes(StandardCharsets.UTF_8));
        git(repo, "add", ".");
        git(repo, "-c", "user.name=test", "-c", "user.email=test@example.com", "commit", "-m", "wip");

        Path config = temp.resolve("config.yml");
        Files.write(config, (
                "server:\n" +
                "  dataDir: \"" + temp.resolve("data").toString().replace("\\", "\\\\") + "\"\n" +
                "auth:\n" +
                "  adminTokens: [\"admin\"]\n" +
                "projects:\n" +
                "  loan:\n" +
                "    repo: \"" + repo.toString().replace("\\", "\\\\") + "\"\n" +
                "    baselineBranch: \"main\"\n" +
                "    tokens: [\"project\"]\n" +
                "    branches:\n" +
                "      include: [\"main\", \"feature/*\"]\n" +
                "      exclude: [\"feature/wip-*\"]\n" +
                "      maxMatched: 20\n"
        ).getBytes(StandardCharsets.UTF_8));

        AppConfigLoader loader = new AppConfigLoader(config.toString());
        GitService service = new GitService(loader);
        List<String> branches = service.resolveBranches("loan", loader.current().projects.get("loan"));

        assertEquals(2, branches.size());
        assertEquals("main", branches.get(0));
        assertEquals("feature/apply-flow", branches.get(1));

        GitService.Worktree mainFirst = service.ensureWorktree("loan", repo.toString(), "main");
        GitService.Worktree mainSecond = service.ensureWorktree("loan", repo.toString(), "main");
        GitService.Worktree featureSecond = service.ensureWorktree("loan", repo.toString(), "feature/apply-flow");

        assertEquals(mainFirst.commit, mainSecond.commit);
        assertTrue(Files.exists(mainSecond.path.resolve("README.md")));
        assertTrue(Files.exists(featureSecond.path.resolve("feature.txt")));
    }

    private void git(Path dir, String... args) throws Exception {
        String[] command = new String[args.length + 1];
        command[0] = "git";
        System.arraycopy(args, 0, command, 1, args.length);
        Process p = new ProcessBuilder(command).directory(dir.toFile()).start();
        int code = p.waitFor();
        if (code != 0) {
            throw new IllegalStateException(new String(readAll(p.getErrorStream()), StandardCharsets.UTF_8));
        }
    }

    private byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
