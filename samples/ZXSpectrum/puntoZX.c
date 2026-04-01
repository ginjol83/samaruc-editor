#include <graphics.h>
#include <input.h>

// Función para dibujar/borrar un cuadrado de 8x8 usando líneas
void dibujar_cuadrado(int x, int y, int borrar) {
    int i;
    for (i = 0; i < 8; i++) {
        if (borrar == 1) {
            // undraw borra una línea de (x1, y1) a (x2, y2)
            undraw(x, y + i, x + 7, y + i);
        } else {
            // draw pinta una línea de (x1, y1) a (x2, y2)
            draw(x, y + i, x + 7, y + i);
        }
    }
}

void main()
{
    int x = 120;
    int y = 80;

    clg(); // Limpiamos pantalla

    // Dibujamos el cuadrado inicial
    dibujar_cuadrado(x, y, 0);

    while(1) {
        char tecla = in_Inkey();

        if (tecla == 'p' && x < 247) { // Derecha
            dibujar_cuadrado(x, y, 1);
            x += 4; // Movemos de 4 en 4 para que sea más rápido
            dibujar_cuadrado(x, y, 0);
        }
        if (tecla == 'o' && x > 0) {   // Izquierda
            dibujar_cuadrado(x, y, 1);
            x -= 4;
            dibujar_cuadrado(x, y, 0);
        }
        if (tecla == 'q' && y < 168) { // Arriba
            dibujar_cuadrado(x, y, 1);
            y += 4;
            dibujar_cuadrado(x, y, 0);
        }
        if (tecla == 'a' && y > 0) {   // Abajo
            dibujar_cuadrado(x, y, 1);
            y -= 4;
            dibujar_cuadrado(x, y, 0);
        }

        // Retardo para que el Spectrum respire
        for(int i = 0; i < 300; i++); 
    }
}