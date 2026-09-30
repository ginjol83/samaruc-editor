/*
 * Plantilla basica para NES (cc65 -t nes).
 *
 * La copia integrada de cc65 solo expone la API clasica (waitvsync) y el
 * registro del PPU mediante la estructura __ppu de <nes.h>: esta version no
 * trae las funciones ppu_* ni pal_* de ports mas modernos. Por eso la VRAM se
 * escribe directamente en $2006/$2007, que es el metodo del propio port.
 *
 * Los datos que produce el conversor PNG a C (tiles 2bpp con los planos
 * intercalados, tabla de atributos y paleta de cuatro subpaletas) se vuelcan
 * con estas mismas tres funciones.
 */
#include <nes.h>

#define VRAM_PATTERNS  0x0000
#define VRAM_ATTRIBUTES 0x2000
#define PPU_PALETTE     0x3F00

/* Paleta del NES: 32 bytes, cuatro subpaletas de cuatro colores (0x0F = negro). */
const unsigned char nes_palette[32] = {
    0x0F, 0x01, 0x21, 0x31,   0x0F, 0x06, 0x16, 0x26,
    0x0F, 0x09, 0x19, 0x29,   0x0F, 0x00, 0x10, 0x20,
    0x0F, 0x02, 0x12, 0x22,   0x0F, 0x04, 0x14, 0x24,
    0x0F, 0x05, 0x15, 0x25,   0x0F, 0x08, 0x18, 0x28
};

/* Un tile 2bpp de 8x8: ocho bytes de plano 0 seguidos de ocho de plano 1. */
const unsigned char nes_logo_tile[16] = {
    0x00, 0x00, 0x00, 0x00, 0x3C, 0x42, 0x81, 0x81,
    0x81, 0x81, 0x42, 0x3C, 0x00, 0x00, 0x00, 0x00
};

/** Espera a que el PPU entre en vblank: el bit 7 de $2002 se pone a uno. */
static void waitVBlank(void) {
    while (!(PPU.status & 0x80)) {
    }
}

/** Prepara $2006 para escribir en la direccion indicada de la VRAM. */
static void vramSetAddress(unsigned int address) {
    PPU.vram.address = (unsigned char)(address >> 8);
    PPU.vram.address = (unsigned char)(address & 0xFF);
}

/** Escribe un bloque de bytes en $2007 con el incremento de VRAM a uno. */
static void vramWrite(const unsigned char* data, unsigned int length) {
    unsigned int i;

    for (i = 0; i < length; i++) {
        PPU.vram.data = data[i];
    }
}

void main(void) {
    unsigned char i;

    waitVBlank();

    /* Render apagado mientras se escribe la VRAM. */
    PPU.control = 0x00;
    PPU.mask = 0x00;

    /* Pattern table en $0000: los tiles 2bpp van seguidos, sin separacion. */
    vramSetAddress(VRAM_PATTERNS);
    vramWrite(nes_logo_tile, sizeof(nes_logo_tile));

    /* Tabla de atributos: 64 entradas de 8x8, dos bits por cada cuadrante. */
    vramSetAddress(VRAM_ATTRIBUTES);
    for (i = 0; i < 64; i++) {
        PPU.vram.data = 0x00;
    }

    /* Paleta de fondo y de sprites. */
    vramSetAddress(PPU_PALETTE);
    vramWrite(nes_palette, sizeof(nes_palette));

    /* Fondo en $0000, sprites en $1000, NMI desactivado. */
    PPU.control = 0x08;
    PPU.mask = 0x1E;

    while (1) {
        waitvsync();
    }
}