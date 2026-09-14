/*
 * Plantilla basica para Game Boy (GBDK-2020).
 * GBDK se incluye integrado con Samaruc en libs/gbdk.
 */
#include <gb/gb.h>

void main(void) {
    DISPLAY_ON;

    while (1) {
        wait_vbl_done();
    }
}
