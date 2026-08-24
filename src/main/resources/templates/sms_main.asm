; Plantilla Ensamblador Z80 para Sega Master System / Game Gear
    .org 0000h
    di
    im 1
    ld sp, 0DFFFh

main_loop:
    halt
    jr main_loop
