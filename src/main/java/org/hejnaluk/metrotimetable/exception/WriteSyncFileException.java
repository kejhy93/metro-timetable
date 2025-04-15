package org.hejnaluk.metrotimetable.exception;

import java.io.IOException;

public class WriteSyncFileException extends RuntimeException {
    public WriteSyncFileException(IOException e) {
        super("Error writing synchronized file", e);
    }
}
