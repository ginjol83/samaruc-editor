package com.retroeditor.service.syntax;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Diccionario de autocompletado por plataforma. Las palabras de cada set se han comprobado
 * contra las cabeceras que trae el toolchain empaquetado en {@code libs/devkits}, no contra la
 * documentacion de otra distribucion: sugerir un simbolo que el compilador no declara
 * desorienta mas que no sugerirlo.
 */
public class CompletionDictionaryService {

    public static final int MAX_SUGGESTIONS = 50;
    private static final int MIN_WORD_LENGTH = 2;

    private static final Pattern WORD_PATTERN = Pattern.compile("[A-Za-z0-9_]+");

    private static final String COMPILER_GBDK = "gbdk";
    private static final String COMPILER_Z88DK = "z88dk";
    private static final String COMPILER_CC65 = "cc65";

    private static final String PROFILE_SPECTRUM = "spectrum";
    private static final String PROFILE_CPC = "cpc";
    private static final String PROFILE_SMS = "sms";
    private static final String PROFILE_GAME_GEAR = "gg";
    private static final String PROFILE_MSX = "msx";
    private static final String TARGET_NES = "nes";

    /** C estandar. Vale para cualquier toolchain, asi que se ofrece siempre en un fuente C. */
    private static final List<String> C_KEYWORDS = List.of(
        "printf", "scanf", "sprintf", "snprintf", "malloc", "calloc", "free",
        "strlen", "strcpy", "strncpy", "strcmp", "strncmp", "strcat", "strchr", "strstr",
        "memcpy", "memset", "memcmp", "atoi", "atof", "abs", "labs",
        "fopen", "fclose", "fread", "fwrite", "fgets", "fputs", "fseek", "ftell",
        "rand", "srand", "exit", "qsort"
    );

    /** Game Boy con GBDK (<gbdk.h>). */
    private static final List<String> GBDK_KEYWORDS = List.of(
        "set_sprite_data", "set_sprite_tile", "set_sprite_tiles", "move_sprite", "scroll_sprite",
        "set_bkg_data", "set_bkg_tiles", "move_bkg", "scroll_bkg",
        "set_win_data", "set_win_tiles", "move_win", "scroll_win",
        "set_bkg_tile_xy", "set_win_tile_xy", "set_sprite_tile_xy",
        "set_bkg_submap", "set_win_submap", "set_bkg_attribute", "set_sprite_attribute",
        "set_bkg_palette", "set_sprite_palette", "set_bkg_palette_entry", "set_sprite_palette_entry",
        "display_on", "display_off", "wait_vbl_done", "joypad_init", "joypad", "waitpad", "waitpadup",
        "enable_interrupts", "disable_interrupts", "halt", "reset", "delay", "time", "rand", "initrand",
        "add_TIM", "add_VBL", "remove_TIM", "remove_VBL", "set_interrupts", "sprinit",
        "BGP", "OBP0", "OBP1", "NR50", "NR51", "NR52", "SCX", "SCY", "WY", "WX", "LCDC", "STAT",
        "UINT8", "INT8", "UINT16", "INT16", "INT32", "UINT32", "BANKREF", "TO_FAR_PTR",
        "BOOLEAN", "TRUE", "FALSE", "JOY_UP", "JOY_DOWN", "JOY_LEFT", "JOY_RIGHT", "JOY_A", "JOY_B", "JOY_START", "JOY_SELECT"
    );

    /**
     * ZX Spectrum con Z88DK. Mantiene los nombres que ya se ofrecian (incluidos los de la
     * libreria delSpectrum como {@code inkey} o {@code plot}, que no estan en conio.h).
     */
    private static final List<String> ZX_KEYWORDS = List.of(
        "clrscr", "cputs", "cprintf", "gotoxy", "textattr", "bordercolor", "textcolor",
        "textbackground", "wherex", "wherey", "kbhit", "getch", "putch", "random",
        "inkey", "zx_border", "plot", "draw", "delay", "cls", "pause"
    );

    /** Master System con Z88DK (<sms.h>): funciones, macros y registros del VDP. */
    private static final List<String> SMS_KEYWORDS = List.of(
        "clear_vram", "load_palette", "load_tiles", "set_bkg_map", "scroll_bkg", "set_sprite",
        "get_vcount", "wait_vblank_noint", "read_joypad1", "read_joypad2",
        "vdp_set_reg", "set_vdp_reg", "add_pause_int", "pause_flag", "standard_font",
        "set_sound_freq", "set_sound_volume", "gotoxy_sms",
        "VDP_REG_FLAGS0", "VDP_REG_FLAGS1", "VDP_REG_HINT_COUNTER",
        "__SMS_JOY_UP", "__SMS_JOY_DOWN", "__SMS_JOY_LEFT", "__SMS_JOY_RIGHT",
        "__SMS_JOY_FIREA", "__SMS_JOY_FIREB",
        "JOY_UP", "JOY_DOWN", "JOY_LEFT", "JOY_RIGHT", "JOY_FIREA", "JOY_FIREB"
    );

    /** Game Gear: lo mismo que SMS pero con la paleta de 12 bits. */
    private static final List<String> GAME_GEAR_KEYWORDS = concat(SMS_KEYWORDS, List.of("load_palette_gamegear"));

    /** MSX con Z88DK (<msx.h> y <psg.h>), mas lo basico de conio que si existe en MSX. */
    private static final List<String> MSX_KEYWORDS = List.of(
        "msx_blank", "msx_noblank", "msx_screenmode", "msx_set_mode", "msx_set_mangled_mode",
        "msx_set_vdp", "msx_get_vdp", "msx_vmerge", "msx_set_border",
        "msx_set_char", "msx_set_char_attr", "msx_set_char_color", "msx_set_char_form",
        "msx_set_sprite_mode", "msx_set_sprite_8", "msx_set_sprite_16",
        "msx_put_sprite_8", "msx_put_sprite_16",
        "msx_fill", "msx_fill_v", "msx_color", "msx_vram", "msx_vpoke", "msx_vpeek",
        "msx_vread", "msx_vwrite", "msx_vwrite_direct", "msx_blit_vram_ram", "msx_blit_ram_vram",
        "lpt_ready", "tape_save", "msxtape_save_block", "msxtape_load_block", "msxtape_save_header",
        "psg_init", "psg_tone_channels", "psg_noise_channels", "psg_noise", "psgT", "set_psg", "sound",
        "clrscr", "gotoxy", "textcolor", "cputs", "cprintf"
    );

    /** CPC con Z88DK (<conio.h>). Sin los nombres que solo existen en el Spectrum. */
    private static final List<String> CPC_KEYWORDS = List.of(
        "clrscr", "clreol", "cprintf", "cputs", "cgets", "cscanf", "cgetc", "cputc",
        "gotoxy", "screensize", "wherex", "wherey", "random",
        "textcolor", "textcolour", "textbackground", "textattr", "settextcolor",
        "highvideo", "lowvideo", "normvideo", "bordercolor", "bordercolour",
        "putch", "fputc_cons", "getch", "getche", "kbhit"
    );

    /**
     * NES con cc65 (<nes.h>). El cc65 empaquetado no trae {@code ppu_*}, {@code pal_*} ni
     * {@code oam_*}: el PPU y la APU se manejan con las macros {@code PPU} y {@code APU}, que
     * apuntan a unas structs en {@code $2000} y {@code $4000}.
     */
    private static final List<String> NES_KEYWORDS = List.of(
        "waitvsync", "PPU", "APU", "SPRITE_DMA", "JOYPAD",
        "JOY_START", "JOY_SELECT", "JOY_BTN_A", "JOY_BTN_B",
        "PPU.control", "PPU.mask", "PPU.status", "PPU.scroll",
        "PPU.sprite.address", "PPU.sprite.data", "PPU.vram.address", "PPU.vram.data",
        "APU.status", "APU.fcontrol", "APU.pulse", "APU.triangle", "APU.noise",
        "APU.delta_mod", "APU.sprite.dma",
        "CH_ENTER", "CH_ESC", "CH_DEL", "CH_CROSS", "CH_RTEE", "CH_LTEE", "CH_TTEE", "CH_BTEE",
        "CH_HLINE", "CH_VLINE", "CH_ULCORNER", "CH_URCORNER", "CH_LLCORNER", "CH_LRCORNER",
        "COLOR_BLACK", "COLOR_WHITE", "COLOR_RED", "COLOR_CYAN", "COLOR_PURPLE",
        "COLOR_GREEN", "COLOR_BLUE", "COLOR_YELLOW", "COLOR_ORANGE", "COLOR_BROWN",
        "COLOR_LIGHTRED", "COLOR_GRAY1", "COLOR_GRAY2", "COLOR_LIGHTGREEN",
        "COLOR_LIGHTBLUE", "COLOR_GRAY3"
    );

    private static List<String> concat(List<String> first, List<String> second) {
        List<String> all = new ArrayList<>(first);
        all.addAll(second);
        return List.copyOf(all);
    }

    /**
     * Devuelve las sugerencias para el prefijo actual, mezclando el diccionario de la
     * plataforma con las palabras que ya aparecen en el documento (estas ultimas mandan,
     * porque son las que el usuario esta escribiendo).
     *
     * @param text         texto completo del fichero abierto
     * @param prefix       palabra a la izquierda del cursor
     * @param compiler     compilador seleccionado ({@code GBDK}, {@code z88dk}, {@code cc65}...)
     * @param z88dkProfile perfil de Z88DK ({@code spectrum}, {@code cpc}, {@code sms}, {@code gg}, {@code msx})
     * @param cc65Target   target de cc65 ({@code atari}, {@code c64}, {@code nes})
     * @param fileName     nombre del fichero abierto, para no ofrecer C en un ensamblador
     */
    public List<String> suggest(String text, String prefix, String compiler,
                                String z88dkProfile, String cc65Target, String fileName) {
        Set<String> pool = new LinkedHashSet<>(dictionaryFor(compiler, z88dkProfile, cc65Target, fileName));

        Map<String, Integer> frequencies = new HashMap<>();
        Matcher matcher = WORD_PATTERN.matcher(text == null ? "" : text);
        while (matcher.find()) {
            String word = matcher.group();
            if (word.length() >= MIN_WORD_LENGTH) {
                frequencies.merge(word, 1, Integer::sum);
                pool.add(word);
            }
        }

        String needle = prefix == null ? "" : prefix;
        List<String> candidates = new ArrayList<>();
        for (String word : pool) {
            if (needle.isEmpty()) {
                candidates.add(word);
            } else if (word.length() >= needle.length()
                && word.regionMatches(true, 0, needle, 0, needle.length())) {
                candidates.add(word);
            }
        }

        candidates.sort((a, b) -> {
            int byFrequency = Integer.compare(frequencies.getOrDefault(b, 0), frequencies.getOrDefault(a, 0));
            if (byFrequency != 0) return byFrequency;
            int byLength = Integer.compare(a.length(), b.length());
            if (byLength != 0) return byLength;
            return a.compareTo(b);
        });

        return candidates.stream().limit(MAX_SUGGESTIONS).collect(Collectors.toList());
    }

    /**
     * Diccionario activo para la combinacion de compilador, perfil y target. Si la
     * combinacion no se reconoce se devuelven los tres diccionarios historicos, para no
     * perder sugerencias en proyectos sin perfil configurado.
     */
    public Set<String> dictionaryFor(String compiler, String z88dkProfile, String cc65Target, String fileName) {
        Set<String> dictionary = new LinkedHashSet<>();

        // Un ensamblador no usa la API de C: ahi solo tiene sentido lo que ya este en el fichero.
        if (isAssemblyFileName(fileName)) {
            return dictionary;
        }

        dictionary.addAll(C_KEYWORDS);
        dictionary.addAll(platformKeywords(compiler, z88dkProfile, cc65Target));
        return dictionary;
    }

    private List<String> platformKeywords(String compiler, String z88dkProfile, String cc65Target) {
        String selected = compiler == null ? "" : compiler.trim().toLowerCase(Locale.ROOT);

        if (COMPILER_GBDK.equals(selected)) {
            return GBDK_KEYWORDS;
        }
        if (COMPILER_Z88DK.equals(selected)) {
            String profile = z88dkProfile == null ? "" : z88dkProfile.trim().toLowerCase(Locale.ROOT);
            return switch (profile) {
                case PROFILE_CPC       -> CPC_KEYWORDS;
                case PROFILE_SMS       -> SMS_KEYWORDS;
                case PROFILE_GAME_GEAR -> GAME_GEAR_KEYWORDS;
                case PROFILE_MSX       -> MSX_KEYWORDS;
                case PROFILE_SPECTRUM  -> ZX_KEYWORDS;
                default                -> defaultKeywords();
            };
        }
        if (COMPILER_CC65.equals(selected)) {
            String target = cc65Target == null ? "" : cc65Target.trim().toLowerCase(Locale.ROOT);
            if (TARGET_NES.equals(target)) {
                return NES_KEYWORDS;
            }
            return List.of();
        }
        return defaultKeywords();
    }

    /** Sin compilador reconocido se mantiene el comportamiento de siempre. */
    private List<String> defaultKeywords() {
        return concat(concat(GBDK_KEYWORDS, ZX_KEYWORDS), CPC_KEYWORDS);
    }

    private boolean isAssemblyFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) return false;
        String name = fileName.toLowerCase(Locale.ROOT);
        return name.endsWith(".asm") || name.endsWith(".s") || name.endsWith(".inc") || name.endsWith(".z80");
    }
}