package com.photoapp.exception;

/** Thrown when a requested photo does not exist. */
public class PhotoNotFoundException extends RuntimeException {
    public PhotoNotFoundException(String photoId) {
        super("Photo not found: " + photoId);
    }
}
