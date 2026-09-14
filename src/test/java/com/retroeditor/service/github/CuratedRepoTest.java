package com.retroeditor.service.github;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias de {@link CuratedRepo}.
 */
class CuratedRepoTest {

    @Test
    void fromJsonCreatesValidCuratedRepo() {
        CuratedRepo cr = CuratedRepo.fromJson("gbdk-2020", "gbdk-2020", "gbdk",
            "SDK oficial", "Official SDK", 600);

        Assertions.assertEquals("gbdk-2020", cr.repo().owner());
        Assertions.assertEquals("gbdk-2020", cr.repo().name());
        Assertions.assertEquals("gbdk", cr.category());
        Assertions.assertEquals("SDK oficial", cr.descriptionEs());
        Assertions.assertEquals("Official SDK", cr.descriptionEn());
    }

    @Test
    void getDescriptionReturnsEsForLangEs() {
        CuratedRepo cr = CuratedRepo.fromJson("a", "b", "cat", "Descripción ES", "Description EN", 10);
        Assertions.assertEquals("Descripción ES", cr.getDescription("es"));
    }

    @Test
    void getDescriptionReturnsEnForLangEn() {
        CuratedRepo cr = CuratedRepo.fromJson("a", "b", "cat", "Descripción ES", "Description EN", 10);
        Assertions.assertEquals("Description EN", cr.getDescription("en"));
    }

    @Test
    void getDescriptionFallsBackToEnForUnknownLang() {
        CuratedRepo cr = CuratedRepo.fromJson("a", "b", "cat", "", "Fallback", 10);
        Assertions.assertEquals("Fallback", cr.getDescription("de"));
    }

    @Test
    void constructorRejectsNullRepo() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new CuratedRepo(null, "cat", "", ""));
    }

    @Test
    void constructorSetsDefaultCategory() {
        GitHubRepo ghRepo = new GitHubRepo(1, "r", "o", null, null, 0, null, null, null, null, null);
        CuratedRepo cr = new CuratedRepo(ghRepo, null, "", "");
        Assertions.assertEquals("general", cr.category());
    }
}
