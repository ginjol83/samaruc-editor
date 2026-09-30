/*
 * Plantilla basica para Sega Game Gear (Z88DK).
 *
 * El Game Gear comparte VDP y toolchain con el Master System: mismo codigo y
 * distinto target de compilacion (+sms -subtype=gamegear), que define
 * __GAMEGEAR__ y fuerza la salida a .gg.
 *
 * El Game Gear tiene ademas paleta de 12 bits (3 bits por canal, una linea de
 * paleta de 16 colores) mediante load_palette_gamegear; la CRAM de 6 bits de
 * abajo es la misma que la del Master System.
 */
#include <sms.h>

/* CRAM de 6 bits: %00BBGGRR, dos bits por canal (0 / 85 / 170 / 255). */
unsigned char gg_palette[16] = {
    0x00, 0x00, 0x08, 0x0C, 0x04, 0x30, 0x01, 0x3C,
    0x02, 0x03, 0x06, 0x3E, 0x04, 0x33, 0x15, 0x3F
};

void main(void) {
    clear_vram();
    load_palette(gg_palette, 0, 16);

    /* Sprite 0 en (48, 64) usando el primer tile de la VRAM. */
    set_sprite(0, 48, 64, 0);

    while (1) {
        wait_vblank_noint();
    }
}