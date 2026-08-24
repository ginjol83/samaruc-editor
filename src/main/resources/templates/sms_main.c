#include <sms/sms.h>

void main(void) {
    SMS_displayOff();
    SMS_setBGPaletteColor(0, RGB15(3, 3, 3));
    SMS_displayOn();

    while(1) {
        SMS_waitForVBlank();
    }
}
