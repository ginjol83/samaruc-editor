/*
 * Plantilla de consola en C normal (GCC): mini calculadora.
 */
#include <stdio.h>
#include <stdlib.h>

int main(void) {
    double a = 0.0, b = 0.0;
    char op = 0;

    printf("Mini calculadora\n");
    printf("Uso: <numero> <operador + - * /> <numero>  (Ctrl+Z para salir)\n");

    while (scanf("%lf %c %lf", &a, &op, &b) == 3) {
        switch (op) {
            case '+': printf("= %.2f\n", a + b); break;
            case '-': printf("= %.2f\n", a - b); break;
            case '*': printf("= %.2f\n", a * b); break;
            case '/':
                if (b != 0.0) {
                    printf("= %.2f\n", a / b);
                } else {
                    printf("Error: division entre cero\n");
                }
                break;
            default: printf("Operador no valido: %c\n", op); break;
        }
    }

    return EXIT_SUCCESS;
}
