#include <gb/gb.h>

// Datos del sprite: un cuadrado 8x8 simple
unsigned char pixel_sprite[16] = {
    0xFF, 0xFF,  // Row 0
    0xFF, 0x81,  // Row 1
    0xFF, 0xA5,  // Row 2
    0xFF, 0x81,  // Row 3
    0xFF, 0x81,  // Row 4
    0xFF, 0xBD,  // Row 5
    0xFF, 0x81,  // Row 6
    0xFF, 0xFF  // Row 7
};



void main() {
    // Variables de posición del sprite
    unsigned char sprite_x = 80;
    unsigned char sprite_y = 72;
    unsigned char joypad_state;
    
    // Cargar los datos del sprite en memoria
    set_sprite_data(0, 1, pixel_sprite);
    
    // Asignar sprite 0 al tile 0
    set_sprite_tile(0, 0);
    
    // Posicionar sprite inicial
    move_sprite(0, sprite_x, sprite_y);
    
    // Hacer visibles los sprites
    SHOW_SPRITES;
    
    // Bucle principal del juego
    while(1) {
        // Leer estado del joypad
        joypad_state = joypad();
        
        // Mover sprite según input
        if(joypad_state & J_LEFT && sprite_x > 8) {
            sprite_x--;
        }
        if(joypad_state & J_RIGHT && sprite_x < 160) {
            sprite_x++;
        }
        if(joypad_state & J_UP && sprite_y > 16) {
            sprite_y--;
        }
        if(joypad_state & J_DOWN && sprite_y < 152) {
            sprite_y++;
        }
        
        // Actualizar posición del sprite
        move_sprite(0, sprite_x, sprite_y);
        
        // Salir si se presiona START
        if(joypad_state & J_START) {
            break;
        }
        
        // Esperar al siguiente frame (60 FPS)
        wait_vbl_done();
    }
}
