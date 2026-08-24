/*
 * Plantilla basica para Game Boy (GBDK-2020).
 * Asegurate de tener configurada la ruta del bin de GBDK.
 */
#include <gb/gb.h>

void main(void) {
    DISPLAY_ON;

    while (1) {
        wait_vbl_done();
    }
}
