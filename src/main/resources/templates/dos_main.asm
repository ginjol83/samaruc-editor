; Programa MS-DOS COM (NASM / TASM)
org 100h

section .text
start:
    mov dx, msg
    mov ah, 09h
    int 21h

    mov ah, 00h
    int 16h

    mov ax, 4C00h
    int 21h

section .data
msg db 'Hola desde MS-DOS Assembly!', 13, 10, '$'
