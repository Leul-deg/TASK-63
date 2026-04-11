package com.reslife.api.encryption;

/**
 * Thrown when AES-256-GCM encryption or decryption fails.
 * Wraps the underlying JCE exception so callers don't need to
 * handle checked crypto exceptions.
 */
public class EncryptionException extends RuntimeException {
    public EncryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
