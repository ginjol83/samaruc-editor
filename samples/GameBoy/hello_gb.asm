; Samaruc - ejemplo minimo de ensamblador para Game Boy (SM83 / GBDK-2020)
; Compilar desde Samaruc (perfil Game Boy) o con:
;   libs\devkits\gbdk\bin\lcc.exe -o out\hello_gb.gb samples\GameBoy\hello_gb.asm

    .module hello_gb
    .area _CODE

_LCDC = 0xFF40
_BGP  = 0xFF47
_SCY  = 0xFF42
_SCX  = 0xFF43

    .globl _main
_main::
    di
    xor a
    ldh (_LCDC), a
    ld a, #0xE4
    ldh (_BGP), a
    xor a
    ldh (_SCY), a
    ldh (_SCX), a

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

    ld a, #0x91
    ldh (_LCDC), a

loop:
    halt
    nop
    jr loop

tile_data:
    .db 0x3C, 0x3C, 0x7E, 0x7E, 0xFF, 0xFF, 0xFF, 0xFF
    .db 0xFF, 0xFF, 0xFF, 0xFF, 0x7E, 0x7E, 0x3C, 0x3C
