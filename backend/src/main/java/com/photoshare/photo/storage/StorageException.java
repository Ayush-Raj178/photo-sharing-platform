package com.photoshare.photo.storage;

public class StorageException extends RuntimeException {
    private final boolean cleanupAllowed;

    public StorageException(String message, Throwable cause) {
        this(message, cause, true);
    }

    public StorageException(String message, Throwable cause, boolean cleanupAllowed) {
        super(message, cause);
        this.cleanupAllowed = cleanupAllowed;
    }

    public boolean cleanupAllowed() {
        return cleanupAllowed;
    }

    public String rootCauseType() {
        Throwable root = this;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName();
    }
}
