/*
 * Plantilla basica para ZX Spectrum (Z88DK).
 */
#include <stdio.h>
#include <arch/zx.h>

int main(void) {
    zx_cls(INK_WHITE | PAPER_BLACK);
    printf("Hello ZX Spectrum!\n");
    return 0;
}
