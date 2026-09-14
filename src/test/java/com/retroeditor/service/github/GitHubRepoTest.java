package com.retroeditor.service.github;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

/**
 * Pruebas unitarias de {@link GitHubRepo}.
 */
class GitHubRepoTest {

    @Test
    void recordCreationWithAllFields() {
        LocalDateTime now = LocalDateTime.now();
        GitHubRepo repo = new GitHubRepo(12345, "gbdk-2020", "gbdk-2020",
            "gbdk-2020/gbdk-2020", "SDK for Game Boy", 600, "C",
            "https://github.com/gbdk-2020/gbdk-2020", null, "main", now);

        Assertions.assertEquals(12345, repo.id());
        Assertions.assertEquals("gbdk-2020", repo.name());
        Assertions.assertEquals("gbdk-2020", repo.owner());
        Assertions.assertEquals("gbdk-2020/gbdk-2020", repo.fullName());
        Assertions.assertEquals(600, repo.stars());
        Assertions.assertEquals("C", repo.language());
        Assertions.assertEquals("main", repo.defaultBranch());
        Assertions.assertEquals(now, repo.updatedAt());
    }

    @Test
    void constructorSetsDefaultsForNullFields() {
        GitHubRepo repo = new GitHubRepo(1, "test", "owner", null, null, 0, null, null, null, null, null);

        Assertions.assertEquals("owner/test", repo.fullName());
        Assertions.assertEquals("https://github.com/owner/test", repo.url());
        Assertions.assertEquals("main", repo.defaultBranch());
    }

    @Test
    void getZipUrlUsesDefaultBranch() {
        GitHubRepo repo = new GitHubRepo(1, "myrepo", "myuser", null, null, 0, null, null, null, "develop", null);
        String zipUrl = repo.getZipUrl();
        Assertions.assertTrue(zipUrl.contains("develop"));
        Assertions.assertTrue(zipUrl.endsWith(".zip"));
    }

    @Test
    void toStringShowsFullNameAndStars() {
        GitHubRepo repo = new GitHubRepo(1, "repo", "user", null, null, 42, null, null, null, null, null);
        String str = repo.toString();
        Assertions.assertTrue(str.contains("user/repo"));
        Assertions.assertTrue(str.contains("42"));
    }

    @Test
    void constructorRejectsNullName() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new GitHubRepo(1, null, "owner", null, null, 0, null, null, null, null, null));
    }

    @Test
    void constructorRejectsNullOwner() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new GitHubRepo(1, "name", null, null, null, 0, null, null, null, null, null));
    }
}
