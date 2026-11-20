package co.coretech.portal.compartido.aplicacion;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cifra secretos que se guardan en la base (el permiso de Gmail, D-51) con AES-256-GCM. La clave sale de
 * {@code coretech.cifrado.clave} (variable CORETECH_CIFRADO_CLAVE, mínimo 32 caracteres; sin ella la API no arranca).
 * Quien lea la base sin la clave no puede usar el permiso.
 */
@Component
public class CifradoSecretos {

    private static final int BYTES_IV = 12;
    private static final int BITS_ETIQUETA = 128;

    private final SecretKeySpec clave;
    private final SecureRandom azar = new SecureRandom();

    CifradoSecretos(@Value("${coretech.cifrado.clave:}") String clave) {
        if (clave == null || clave.length() < 32) {
            throw new IllegalStateException(
                    "coretech.cifrado.clave debe tener al menos 32 caracteres (variable de entorno CORETECH_CIFRADO_CLAVE)");
        }
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(clave.getBytes(StandardCharsets.UTF_8));
            this.clave = new SecretKeySpec(bytes, "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public String cifrar(String texto) {
        try {
            byte[] iv = new byte[BYTES_IV];
            azar.nextBytes(iv);
            Cipher cifrador = Cipher.getInstance("AES/GCM/NoPadding");
            cifrador.init(Cipher.ENCRYPT_MODE, clave, new GCMParameterSpec(BITS_ETIQUETA, iv));
            byte[] cifrado = cifrador.doFinal(texto.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar", e);
        }
    }

    /** Falla si el texto se alteró o se cifró con otra clave (GCM lo detecta). */
    public String descifrar(String guardado) {
        try {
            byte[] todo = Base64.getDecoder().decode(guardado);
            Cipher cifrador = Cipher.getInstance("AES/GCM/NoPadding");
            cifrador.init(Cipher.DECRYPT_MODE, clave, new GCMParameterSpec(BITS_ETIQUETA, todo, 0, BYTES_IV));
            return new String(cifrador.doFinal(todo, BYTES_IV, todo.length - BYTES_IV), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("No se pudo descifrar: ¿cambió CORETECH_CIFRADO_CLAVE?", e);
        }
    }
}
