package com.chromawave;

import javafx.application.Application;

/**
 * Punto de entrada de la aplicación.
 *
 * <p>Cuando los módulos de JavaFX llegan por el classpath —como ocurre en el
 * paquete que genera {@code installDist}— la máquina virtual se niega a arrancar
 * una clase principal que extienda {@link Application}. Delegar desde una clase
 * intermedia evita ese rechazo y hace que el ejecutable distribuible funcione
 * igual que {@code gradle run}.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Application.launch(ChromaWaveApp.class, args);
    }
}
