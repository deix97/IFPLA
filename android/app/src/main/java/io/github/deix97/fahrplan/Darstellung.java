package io.github.deix97.fahrplan;

/**
 * Schriftgröße und Grundfarbe der Anzeige aus den Einstellungen des Geräts.
 * Ohne Android-Abhängigkeit, damit es sich ohne Gerät prüfen lässt.
 */
final class Darstellung {
    /** Kleinste und größte Schriftskala; darüber würden die Spalten im geteilten Bildschirm zu eng. */
    static final float SKALA_MIN = 0.85f;
    static final float SKALA_MAX = 1.3f;
    /** Grund der Seite wie --grund in index.html. */
    static final int GRUND_TAG = 0xFFF2F2F7;
    static final int GRUND_NACHT = 0xFF000000;

    private Darstellung() {
    }

    /**
     * Textzoom der WebView in Prozent aus der Schriftskala des Geräts (Configuration.fontScale),
     * wie Dynamic Type. Ohne gültige Angabe 100 %.
     */
    static int textZoom(float schriftskala) {
        float skala = schriftskala > 0 ? schriftskala : 1f;
        return Math.round(Math.max(SKALA_MIN, Math.min(SKALA_MAX, skala)) * 100);
    }

    /** Grundfarbe für Tag oder Nacht. */
    static int grund(boolean nacht) {
        return nacht ? GRUND_NACHT : GRUND_TAG;
    }
}
