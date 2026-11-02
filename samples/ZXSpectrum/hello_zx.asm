; Samaruc - ejemplo minimo de ensamblador para ZX Spectrum (Z80 / z88dk)
; Compilar desde Samaruc (perfil Spectrum/z88dk) o con:
;   libs\devkits\z88dk\bin\zcc.exe +zx -vn -startup=0 -o out\hello_zx samples\ZXSpectrum\hello_zx.asm -create-app

SECTION code_user

PUBLIC _main

_main:
    ld hl, message
print_loop:
    ld a, (hl)
    or a
    jr z, wait
    rst 16
    inc hl
    jr print_loop

wait:
    jr wait

message:
    defb "ZX SPECTRUM - SAMARUC", 13, 0
