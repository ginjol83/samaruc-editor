/*
 * Plantilla basica para Sega Master System (Z88DK).
 *
 * La API de VDP y sprites es la de <sms.h> del propio Z88DK: clear_vram,
 * load_palette, load_tiles, set_sprite y wait_vblank_noint. SMSlib (SMS_*,
 * RGB15) NO viene incluido con este toolchain, asi que no la uses.
 *
 * Para Game Gear el codigo es el mismo y solo cambia el target de compilacion
 * (+sms -subtype=gamegear); en ese caso load_palette_gamegear admite la paleta
 * de 12 bits del Game Gear.
 */
#include <sms.h>

/* CRAM de 6 bits: %00BBGGRR, dos bits por canal (0 / 85 / 170 / 255). */
unsigned char sms_palette[16] = {
    0x00, 0x00, 0x08, 0x0C, 0x04, 0x30, 0x01, 0x3C,
    0x02, 0x03, 0x06, 0x3E, 0x04, 0x33, 0x15, 0x3F
};

void main(void) {
    clear_vram();
    load_palette(sms_palette, 0, 16);

    /* Sprite 0 en (32, 32) usando el primer tile de la VRAM. */
    set_sprite(0, 32, 32, 0);

    while (1) {
        wait_vblank_noint();
    }
}