; Samaruc - plantilla de ensamblador para Game Boy (SM83 / GBDK-2020)
; Compila desde Samaruc con el perfil Game Boy o con:
;   lcc -o out/main.gb src/main.asm
;
; GBDK enlaza su CRT y llama a _main; no uses .org en esta plantilla.

    .module gb_asm_main
    .area _CODE

_LCDC = 0xFF40
_BGP  = 0xFF47
_SCY  = 0xFF42
_SCX  = 0xFF43

    .globl _main
_main::
    di

    ; LCD apagada para poder escribir en VRAM.
    xor a
    ldh (_LCDC), a

    ; Paleta BGP: indices 0..3 -> blanco, gris claro, gris oscuro, negro.
    ld a, #0xE4
    ldh (_BGP), a

    xor a
    ldh (_SCY), a
    ldh (_SCX), a

    ; Copia el tile 0 (16 bytes, 2 bits por pixel) a 0x8000.
    ld hl, #0x8000
    ld de, #tile_data
    ld bc, #16
copy_tile:
    ld a, (de)
    ld (hl), a
    inc hl
    inc de
    dec bc
    ld a, b
    or c
    jr nz, copy_tile

    ; Rellena el mapa de fondo (0x9800-0x9BFF) con el tile 0.
    ld hl, #0x9800
    ld bc, #0x400
fill_map:
    xor a
    ld (hl), a
    inc hl
    dec bc
    ld a, b
    or c
    jr nz, fill_map

    ; LCD on, BG on, tile data en 0x8000, BG map en 0x9800.
    ld a, #0x91
    ldh (_LCDC), a

main_loop:
    halt
    nop
    jr main_loop

tile_data:
    .db 0x3C, 0x3C, 0x7E, 0x7E, 0xFF, 0xFF, 0xFF, 0xFF
    .db 0xFF, 0xFF, 0xFF, 0xFF, 0x7E, 0x7E, 0x3C, 0x3C
