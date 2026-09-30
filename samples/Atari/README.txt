S A M A R U C - Atari 8-bit / XE/XL

Ejemplos para Atari 8-bit compilados con CC65 (incluido en libs/devkits/cc65).

- hello_atari.c: minimo con conio (pantalla de texto) de CC65.
  Ciclo printf/puts clasico de cc65: clrscr, cputs/cprintf, cgetc.

Compilar dentro de Samaruc (perfil CC65):
  1. Compilar (F5) -> genera out/<nombre>.xex con cl65 -t atari.
  2. Ejecutar (F6)  -> Altirra carga el .xex (libs/emulators/Altirra).

Desde consola (sin Samaruc):
  libs\devkits\cc65\bin\cl65.exe -t atari samples\Atari\hello_atari.c -o out\hello_atari.xex
  libs\emulators\Altirra\Altirra64.exe out\hello_atari.xex