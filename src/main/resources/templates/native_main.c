/*
 * Plantilla basica de C normal (GCC) para escritorio.
 */
#include <stdio.h>
#include <stdlib.h>

int main(int argc, char *argv[]) {
    if (argc > 1) {
        for (int i = 1; i < argc; i++) {
            printf("Argumento %d: %s\n", i, argv[i]);
        }
    } else {
        printf("Hola, mundo!\n");
    }
    return EXIT_SUCCESS;
}
