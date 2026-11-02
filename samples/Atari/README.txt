S A M A R U C - Atari 8-bit / XE/XL

Ejemplos para Atari 8-bit compilados con CC65 (incluido en libs/devkits/cc65).

- hello_atari.c: minimo con conio (pantalla de texto) de CC65.
  Ciclo printf/puts clasico de cc65: clrscr, cputs/cprintf, cgetc.
- hello_atari.asm: minimo en ensamblador 6502 (ca65).
  Usa atari.inc, CIOV y el comando PUTCHR; exporta el simbolo start.

Compilar dentro de Samaruc (perfil Atari / CC65):
  1. Compilar (F5) -> genera out/<nombre>.xex.
     - .c: cl65 -t atari.
     - .asm/.s: cl65 -t none con atari-asm-xex.cfg (ca65 + ld65).
  2. Ejecutar (F6)  -> Altirra carga el .xex (libs/emulators/Altirra).

Desde consola (sin Samaruc):
  libs\devkits\cc65\bin\cl65.exe -t atari samples\Atari\hello_atari.c -o out\hello_atari.xex
  libs\devkits\cc65\bin\cl65.exe -t none -C libs\devkits\cc65\cfg\atari-asm-xex.cfg -I libs\devkits\cc65\asminc samples\Atari\hello_atari.asm -o out\hello_atari.xex
  libs\emulators\Altirra\Altirra64.exe out\hello_atari.xex