package io.github.deix97.fahrplan;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Benutzername und Passwort für EIRE, verschlüsselt mit einem Schlüssel aus dem
 * Android-Schlüsselspeicher. Der Schlüssel verlässt das Gerät nicht; ohne ihn sind
 * die abgelegten Daten wertlos, auch in einer Sicherung.
 */
final class Tresor implements Eire.Zugangsquelle {
    private static final String SCHLUESSEL = "fahrplan-eire-zugang";
    private static final String SPEICHER = "AndroidKeyStore";
    private final SharedPreferences ablage;

    Tresor(Context kontext) {
        ablage = kontext.getSharedPreferences("eire-zugang", Context.MODE_PRIVATE);
    }

    synchronized void lege(String benutzer, String passwort) throws GeneralSecurityException {
        byte[] klar;
        try {
            klar = new JSONObject().put("b", benutzer).put("p", passwort).toString().getBytes(StandardCharsets.UTF_8);
        } catch (JSONException e) {
            throw new GeneralSecurityException(e);
        }
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, schluessel());
        byte[] geheim = c.doFinal(klar);
        ablage.edit()
                .putString("iv", Base64.getEncoder().encodeToString(c.getIV()))
                .putString("daten", Base64.getEncoder().encodeToString(geheim))
                .putString("benutzer", benutzer)
                .apply();
    }

    @Override
    public synchronized String[] lies() {
        String iv = ablage.getString("iv", null), daten = ablage.getString("daten", null);
        if (iv == null || daten == null) return null;
        try {
            KeyStore ks = KeyStore.getInstance(SPEICHER);
            ks.load(null);
            if (!ks.containsAlias(SCHLUESSEL)) return null;
            SecretKey k = ((KeyStore.SecretKeyEntry) ks.getEntry(SCHLUESSEL, null)).getSecretKey();
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, k, new GCMParameterSpec(128, Base64.getDecoder().decode(iv)));
            JSONObject o = new JSONObject(new String(c.doFinal(Base64.getDecoder().decode(daten)), StandardCharsets.UTF_8));
            return new String[]{o.optString("b", ""), o.optString("p", "")};
        } catch (Exception e) {
            return null;
        }
    }

    /** Nur der Name, zum Anzeigen; das Passwort bleibt verschlüsselt. */
    synchronized String benutzer() {
        return ablage.getString("daten", null) == null ? null : ablage.getString("benutzer", null);
    }

    synchronized void loesche() {
        ablage.edit().clear().apply();
        try {
            KeyStore ks = KeyStore.getInstance(SPEICHER);
            ks.load(null);
            if (ks.containsAlias(SCHLUESSEL)) ks.deleteEntry(SCHLUESSEL);
        } catch (Exception ignoriert) {
            /* ohne Daten ist der Schlüssel ohnehin nutzlos */
        }
    }

    private SecretKey schluessel() throws GeneralSecurityException {
        KeyStore ks = KeyStore.getInstance(SPEICHER);
        try {
            ks.load(null);
        } catch (java.io.IOException e) {
            throw new GeneralSecurityException(e);
        }
        if (ks.containsAlias(SCHLUESSEL)) {
            return ((KeyStore.SecretKeyEntry) ks.getEntry(SCHLUESSEL, null)).getSecretKey();
        }
        KeyGenerator g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, SPEICHER);
        g.init(new KeyGenParameterSpec.Builder(SCHLUESSEL, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return g.generateKey();
    }
}
