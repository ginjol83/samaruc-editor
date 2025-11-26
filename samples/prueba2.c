// matamarcianos_v3.c - versión con vidas, sonido y game over
#include <gb/gb.h>
#include <stdio.h>
#include <stdlib.h>

// ==== SPRITES ====
// Nave del jugador
const unsigned char ship_tile[] = {
    0x18,0x18,
    0x3C,0x3C,
    0x7E,0x7E,
    0xFF,0xFF,
    0xDB,0xDB,
    0x18,0x18,
    0x24,0x24,
    0x42,0x42
};

// Enemigo
const unsigned char enemy_tile[] = {
    0x3C,0x3C,
    0x7E,0x7E,
    0xDB,0xDB,
    0xFF,0xFF,
    0x66,0x66,
    0x7E,0x7E,
    0x24,0x24,
    0x66,0x66
};

// Disparo
const unsigned char bullet_tile[] = {
    0x18,0x18,
    0x18,0x18,
    0x18,0x18,
    0x18,0x18,
    0x00,0x00,
    0x00,0x00,
    0x00,0x00,
    0x00,0x00
};

// ==== VARIABLES ====
UINT8 ship_x = 80, ship_y = 130;
UINT8 bullet_x, bullet_y;
UINT8 bullet_active = 0;
UINT16 score = 0;
INT8 lives = 3;

#define NUM_ENEMIES 5
UINT8 enemy_x[NUM_ENEMIES];
UINT8 enemy_y[NUM_ENEMIES];
INT8 enemy_dir[NUM_ENEMIES];
UINT8 enemy_speed_counter = 0;
UINT8 game_over = 0;

// ==== FUNCIONES DE SONIDO ====
void play_shoot_sound() {
    NR10_REG = 0x16;
    NR11_REG = 0x40;
    NR12_REG = 0x73;
    NR13_REG = 0x00;
    NR14_REG = 0xC3;
}

void play_explosion_sound() {
    NR41_REG = 0x1F;
    NR42_REG = 0xF1;
    NR43_REG = 0x30;
    NR44_REG = 0xC0;
}

// ==== FUNCIONES DE JUEGO ====
void reset_enemy(UINT8 i){
    enemy_x[i] = (rand() % 150) + 10;
    enemy_y[i] = 16;
    enemy_dir[i] = (rand() % 2) ? 1 : -1;
}

void init_game() {
    // Inicializa sprites
    set_sprite_data(0, 3, ship_tile);
    set_sprite_data(0, 1, ship_tile);
    set_sprite_data(1, 1, enemy_tile);
    set_sprite_data(2, 1, bullet_tile);

    set_sprite_tile(0, 0);
    move_sprite(0, ship_x, ship_y);

    // Enemigos iniciales
    for(UINT8 i=0; i<NUM_ENEMIES; i++){
        set_sprite_tile(i+1, 1);
        enemy_x[i] = 20 + i*25;
        enemy_y[i] = 20 + (i%2)*10;
        enemy_dir[i] = (i%2==0) ? 1 : -1;
        move_sprite(i+1, enemy_x[i], enemy_y[i]);
    }

    // Bala
    set_sprite_tile(NUM_ENEMIES+1, 2);
    move_sprite(NUM_ENEMIES+1, 0, 0);
}

void update_ship(UINT8 keys){
    if(keys & J_LEFT)  ship_x -= 2;
    if(keys & J_RIGHT) ship_x += 2;

    if(ship_x < 8) ship_x = 8;
    if(ship_x > 160) ship_x = 160;

    move_sprite(0, ship_x, ship_y);
}

void shoot(){
    if(!bullet_active){
        bullet_active = 1;
        bullet_x = ship_x;
        bullet_y = ship_y - 8;
        move_sprite(NUM_ENEMIES+1, bullet_x, bullet_y);
        play_shoot_sound();
    }
}

void update_bullet(){
    if(bullet_active){
        if(bullet_y < 8){
            bullet_active = 0;
            move_sprite(NUM_ENEMIES+1, 0, 0);
        } else {
            bullet_y -= 4;
            move_sprite(NUM_ENEMIES+1, bullet_x, bullet_y);
        }
    }
}

void update_enemies(){
    enemy_speed_counter++;
    if(enemy_speed_counter < 2) return;
    enemy_speed_counter = 0;

    for(UINT8 i=0; i<NUM_ENEMIES; i++){
        enemy_x[i] += enemy_dir[i];
        if(enemy_x[i] < 10 || enemy_x[i] > 150) enemy_dir[i] = -enemy_dir[i];
        enemy_y[i]++;

        if(enemy_y[i] > 140){
            lives--;
            reset_enemy(i);
            if(lives <= 0) game_over = 1;
        }

        move_sprite(i+1, enemy_x[i], enemy_y[i]);
    }
}

void check_collisions(){
    if(!bullet_active) return;

    for(UINT8 i=0; i<NUM_ENEMIES; i++){
        if(enemy_x[i] + 8 > bullet_x && bullet_x + 4 > enemy_x[i] &&
           enemy_y[i] + 8 > bullet_y && bullet_y + 4 > enemy_y[i]) {
            reset_enemy(i);
            bullet_active = 0;
            move_sprite(NUM_ENEMIES+1, 0, 0);
            score += 10;
            play_explosion_sound();
        }
    }
}

void show_status() {
    printf("\033[0;0H"); // Secuencia escape: mueve cursor al inicio (si la consola lo soporta)
    printf("SCORE:%u  LIVES:%d  ", score, lives);
}

void show_game_over(){
    HIDE_SPRITES;
    printf("\n\n\n\n\n\n");
    printf("     GAME OVER!\n\n");
    printf("   FINAL SCORE: %u\n", score);
    while(1){
        wait_vbl_done();
    }
}

// ==== LOOP PRINCIPAL ====
void main(){
    DISPLAY_OFF;
    init_game();
    SHOW_SPRITES;
    DISPLAY_ON;

    printf("\nMATAMARCIANOS v3\n\n");

    while(1){
        if(game_over){
            show_game_over();
        }

        UINT8 keys = joypad();
        update_ship(keys);
        update_enemies();
        update_bullet();
        check_collisions();
        show_status();

        if(keys & J_A) shoot();

        wait_vbl_done();
    }
}
