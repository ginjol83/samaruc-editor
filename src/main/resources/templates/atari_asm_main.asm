; Samaruc - plantilla de ensamblador para Atari 8 bits (6502 / CC65)
; Compila desde Samaruc o con:
;   cl65 -t none -C <cc65>/cfg/atari-asm-xex.cfg -I <cc65>/asminc src/main.asm -o out/main.xex

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
