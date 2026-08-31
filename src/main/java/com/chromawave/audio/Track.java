package com.chromawave.audio;

import java.io.File;

/** Una pista de la lista de reproducción. */
public record Track(File file) {

    public String title() {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    @Override
    public String toString() {
        return title();
    }
}
