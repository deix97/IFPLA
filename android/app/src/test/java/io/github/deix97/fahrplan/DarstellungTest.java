package io.github.deix97.fahrplan;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DarstellungTest {
    @Test
    public void textZoomFolgtDerSchriftgroesseDesGeraets() {
        assertEquals(100, Darstellung.textZoom(1f));
        assertEquals(115, Darstellung.textZoom(1.15f));
        assertEquals(130, Darstellung.textZoom(1.3f));
        assertEquals(85, Darstellung.textZoom(0.85f));
    }

    @Test
    public void textZoomBleibtInDenGrenzen() {
        assertEquals(130, Darstellung.textZoom(2f));
        assertEquals(85, Darstellung.textZoom(0.5f));
        assertEquals(130, Darstellung.textZoom(Float.POSITIVE_INFINITY));
    }

    @Test
    public void textZoomOhneGueltigeAngabeIstHundert() {
        assertEquals(100, Darstellung.textZoom(0f));
        assertEquals(100, Darstellung.textZoom(-1f));
        assertEquals(100, Darstellung.textZoom(Float.NaN));
    }

    @Test
    public void grundWieImStilteilDerAnzeige() {
        assertEquals(0xFFF2F2F7, Darstellung.grund(false));
        assertEquals(0xFF000000, Darstellung.grund(true));
    }
}
