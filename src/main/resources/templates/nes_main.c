#include <nes.h>

void main(void) {
    ppu_waitNmi();
    ppu_off();
    pal_clear();
    pal_col(0, 0x0F);
    pal_col(1, 0x21);
    ppu_on_all();

    while(1) {
        ppu_waitNmi();
    }
}