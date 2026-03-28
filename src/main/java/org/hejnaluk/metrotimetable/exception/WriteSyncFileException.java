package org.hejnaluk.metrotimetable.exception;

import java.io.IOException;

/**
 * Thrown when the synchronization marker file ({@code synchronized.txt}) cannot be read or written.
 */
public class WriteSyncFileException extends RuntimeException {

    /**
     * @param e the underlying I/O failure
     */
    public WriteSyncFileException(IOException e) {
        super("Error writing synchronized file", e);
    }
}
