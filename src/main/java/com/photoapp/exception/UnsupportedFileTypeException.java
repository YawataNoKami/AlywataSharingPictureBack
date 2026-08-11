package com.photoapp.exception;

/** Thrown when an uploaded file's MIME type is not one of the supported formats. */
public class UnsupportedFileTypeException extends RuntimeException {
    public UnsupportedFileTypeException(String message) {
        super(message);
    }
}
