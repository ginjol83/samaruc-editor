; Samaruc - plantilla de ensamblador para ZX Spectrum (Z80 / z88dk)
; Compila desde Samaruc con el perfil Spectrum o con:
;   zcc +zx -vn -startup=0 -o out/main src/main.asm -create-app
;
; z88dk enlaza contra su CRT y llama a _main; no uses .org con esta plantilla.

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
