; Samaruc - ejemplo minimo de ensamblador para Atari 8 bits (6502 / CC65)
; Compilar desde Samaruc (perfil Atari/CC65) o con:
;   libs\devkits\cc65\bin\cl65.exe -t none -C libs\devkits\cc65\cfg\atari-asm-xex.cfg -I libs\devkits\cc65\asminc samples\Atari\hello_atari.asm -o out\hello_atari.xex

    .include "atari.inc"

    .export start
    .segment "CODE"

start:
    lda #<message
    sta ICBAL
    lda #>message
    sta ICBAH
    lda #(message_end - message)
    sta ICBLL
    lda #0
    sta ICBLH
    lda #PUTCHR
    sta ICCOM
    lda #0
    sta ICAX1
    sta ICAX2
    jsr CIOV

loop:
    jmp loop

    .segment "RODATA"

message:
    .byte "ATARI 8-BIT - SAMARUC", $9B
message_end:
