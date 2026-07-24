package dev.spruceworks.bounty.storage;

/** Unchecked wrapper around checked storage failures (JDBC, I/O). */
public final class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
