package com.retroeditor.service.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Pruebas unitarias de {@link GitHubApiService}.
 * Solo tests de parsing (sin llamadas HTTP reales).
 */
class GitHubApiServiceTest {

    private final GitHubApiService service = new GitHubApiService();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parseSearchResultsReturnsRepos() throws Exception {
        String json = """
            {
              "total_count": 2,
              "items": [
                {
                  "id": 1,
                  "name": "test-repo",
                  "full_name": "user/test-repo",
                  "description": "A test repo",
                  "stargazers_count": 100,
                  "language": "C",
                  "html_url": "https://github.com/user/test-repo",
                  "default_branch": "main",
                  "owner": { "login": "user" },
                  "updated_at": "2025-01-15T10:30:00Z"
                },
                {
                  "id": 2,
                  "name": "another-repo",
                  "full_name": "user2/another-repo",
                  "description": "Another repo",
                  "stargazers_count": 50,
                  "language": "Java",
                  "html_url": "https://github.com/user2/another-repo",
                  "default_branch": "master",
                  "owner": { "login": "user2" },
                  "updated_at": "2024-12-01T08:00:00Z"
                }
              ]
            }
            """;

        List<GitHubRepo> repos = service.parseSearchResults(json);
        Assertions.assertEquals(2, repos.size());
        Assertions.assertEquals("test-repo", repos.get(0).name());
        Assertions.assertEquals("user", repos.get(0).owner());
        Assertions.assertEquals(100, repos.get(0).stars());
        Assertions.assertEquals("C", repos.get(0).language());
        Assertions.assertEquals("master", repos.get(1).defaultBranch());
    }

    @Test
    void parseSearchResultsHandlesEmptyItems() throws Exception {
        String json = "{\"total_count\": 0, \"items\": []}";
        List<GitHubRepo> repos = service.parseSearchResults(json);
        Assertions.assertTrue(repos.isEmpty());
    }

    @Test
    void parseSearchResultsHandlesNullItems() throws Exception {
        String json = "{\"total_count\": 0}";
        List<GitHubRepo> repos = service.parseSearchResults(json);
        Assertions.assertTrue(repos.isEmpty());
    }

    @Test
    void parseSingleRepoReturnsValidRepo() throws Exception {
        String json = """
            {
              "id": 42,
              "name": "my-project",
              "full_name": "dev/my-project",
              "description": "My project",
              "stargazers_count": 200,
              "language": "Z80 Assembly",
              "html_url": "https://github.com/dev/my-project",
              "default_branch": "develop",
              "owner": { "login": "dev" },
              "updated_at": "2025-06-15T12:00:00Z"
            }
            """;

        GitHubRepo repo = service.parseSingleRepo(json);
        Assertions.assertEquals(42, repo.id());
        Assertions.assertEquals("my-project", repo.name());
        Assertions.assertEquals("dev", repo.owner());
        Assertions.assertEquals("develop", repo.defaultBranch());
        Assertions.assertEquals(200, repo.stars());
    }

    @Test
    void parseRepoNodeHandlesMissingOptionalFields() throws Exception {
        String json = """
            {
              "id": 1,
              "name": "minimal",
              "owner": { "login": "user" }
            }
            """;

        JsonNode node = mapper.readTree(json);
        GitHubRepo repo = GitHubApiService.parseRepoNode(node);
        Assertions.assertEquals("minimal", repo.name());
        Assertions.assertEquals("user", repo.owner());
        Assertions.assertEquals(0, repo.stars());
        Assertions.assertEquals("", repo.language());
        Assertions.assertEquals("main", repo.defaultBranch());
    }

    @Test
    void parseSearchResultsHandlesRepoWithNullOwner() throws Exception {
        String json = """
            {
              "items": [
                {
                  "id": 1,
                  "name": "orphan",
                  "full_name": "orphan-repo",
                  "owner": {},
                  "stargazers_count": 5
                }
              ]
            }
            """;

        List<GitHubRepo> repos = service.parseSearchResults(json);
        Assertions.assertEquals(1, repos.size());
        Assertions.assertEquals("", repos.get(0).owner());
    }
}
