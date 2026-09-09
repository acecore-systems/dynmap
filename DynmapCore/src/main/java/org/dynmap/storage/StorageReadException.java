package org.dynmap.storage;

/** A failed read is not an absent tile: callers must preserve and retry the work. */
public class StorageReadException extends RuntimeException {
    public StorageReadException(Throwable cause) { super(cause); }
}
