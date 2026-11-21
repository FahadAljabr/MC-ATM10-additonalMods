package com.fahad.updater;

/** This class is loaded ONLY in the isolated packwiz/installer subprocess. */
public final class EngineRunner {
    public static void main(String[] args) {
        new link.infra.packwiz.installer.Main(args);
    }
}
