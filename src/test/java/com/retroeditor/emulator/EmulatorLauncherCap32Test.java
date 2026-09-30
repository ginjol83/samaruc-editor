package com.retroeditor.emulator;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class EmulatorLauncherCap32Test {

    @Test
    void placesOptionsBeforeTheSlotFile() {
        File exe = new File("libs/emulators/cap32/cap32.exe");
        File dsk = new File("build/main.dsk");

        List<String> cmd = EmulatorLauncher.buildCap32Command(
            exe, dsk, List.of("--autocmd=run\"")
        );

        Assertions.assertEquals(
            Arrays.asList(exe.getAbsolutePath(), "--autocmd=run\"", dsk.getAbsolutePath()),
            cmd
        );
    }

    @Test
    void supportsLaunchingWithoutMedia() {
        File exe = new File("libs/emulators/cap32/cap32.exe");

        List<String> cmd = EmulatorLauncher.buildCap32Command(exe, null, null);

        Assertions.assertEquals(List.of(exe.getAbsolutePath()), cmd);
    }

    @Test
    void keepsOptionsWhenThereIsNoMedia() {
        File exe = new File("libs/emulators/cap32/cap32.exe");

        List<String> cmd = EmulatorLauncher.buildCap32Command(exe, null, List.of("-v"));

        Assertions.assertEquals(Arrays.asList(exe.getAbsolutePath(), "-v"), cmd);
    }

    @Test
    void dropsBlankAndNullOptions() {
        File exe = new File("libs/emulators/cap32/cap32.exe");
        File sna = new File("build/main.sna");

        List<String> options = new ArrayList<>();
        options.add("--autocmd=call 4660");
        options.add("   ");
        options.add(null);

        List<String> cmd = EmulatorLauncher.buildCap32Command(exe, sna, options);

        Assertions.assertEquals(
            Arrays.asList(exe.getAbsolutePath(), "--autocmd=call 4660", sna.getAbsolutePath()),
            cmd
        );
    }

    @Test
    void resolvesCap32ExecutableFromItsOwnSubdirectory() throws Exception {
        Path dir = Files.createTempDirectory("samaruc-cap32");
        try {
            String binaryName = System.getProperty("os.name", "").toLowerCase().contains("win")
                ? "cap32.exe"
                : "cap32";
            Path expected = dir.resolve(binaryName);
            Files.write(expected, new byte[] {0x4D, 0x5A});

            Assertions.assertEquals(expected.toFile(), EmulatorLauncher.resolveBundledCap32Executable(dir.toFile()));
        } finally {
            Files.deleteIfExists(dir.resolve(System.getProperty("os.name", "").toLowerCase().contains("win") ? "cap32.exe" : "cap32"));
            Files.deleteIfExists(dir);
        }
    }

    @Test
    void returnsNullWhenCap32IsMissing() {
        Assertions.assertNull(EmulatorLauncher.resolveBundledCap32Executable(new File("no-existe/cap32")));
        Assertions.assertNull(EmulatorLauncher.resolveBundledCap32Executable(null));
    }
}
