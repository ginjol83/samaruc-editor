#include <conio.h>

/* 
jemplo basico de implementacion de software desarrollado en C para Z88DK
*/
void main() {
    unsigned char key;
    unsigned char border_color = 1;
    
    // Configurar pantalla inicial
    textcolor(7);        // Blanco
    textbackground(0);   // Negro
    bordercolor(1);      // Azul
    clrscr();
    
    // Posicionar cursor y mostrar texto
    gotoxy(5, 5);
    cputs("*** ZX SPECTRUM ***");
    
    gotoxy(6, 7);
    cputs("Z88DK EJEMPLO");
    
    gotoxy(2, 10);
    cputs("CONTROLES:");
    
    gotoxy(2, 11);
    cputs("ESPACIO - Cambiar borde");
    
    gotoxy(2, 12);
    cputs("ENTER   - Salir");
    
    // Bucle principal
    while(1) {
        if(kbhit()) {
            key = getch();
            
            // SPACE cambia borde
            if(key == ' ') {
                border_color = (border_color + 1) % 8;
                bordercolor(border_color);
            }
            
            // ENTER sale
            if(key == 13) {
                break;
            }
        }
    }
    
    // Mensaje final
    clrscr();
    textcolor(7);
    textbackground(2);  // Rojo
    
    gotoxy(8, 10);
    cputs("ADIOS!");
    
    getch();
}
