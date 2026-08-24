; Plantilla de ensamblador (z80 / Game Boy).
; Los comentarios empiezan con ';' y las etiquetas terminan en ':'.

    .org 0x0000

start:
    ld a, 0
    ld hl, 0xC000

loop:
    ld (hl), a
    inc hl
    jr loop
