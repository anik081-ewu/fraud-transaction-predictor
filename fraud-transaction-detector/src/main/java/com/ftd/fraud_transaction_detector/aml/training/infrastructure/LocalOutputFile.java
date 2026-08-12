package com.ftd.fraud_transaction_detector.aml.training.infrastructure;

import org.apache.parquet.io.OutputFile;
import org.apache.parquet.io.PositionOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

final class LocalOutputFile implements OutputFile {

    private final Path path;

    LocalOutputFile(Path path) {
        this.path = path;
    }

    @Override
    public PositionOutputStream create(long blockSizeHint) throws IOException {
        return stream(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    @Override
    public PositionOutputStream createOrOverwrite(long blockSizeHint) throws IOException {
        return stream(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    @Override
    public boolean supportsBlockSize() {
        return false;
    }

    @Override
    public long defaultBlockSize() {
        return 0;
    }

    private PositionOutputStream stream(StandardOpenOption... options) throws IOException {
        Files.createDirectories(path.getParent());
        OutputStream outputStream = Files.newOutputStream(path, options);
        return new PositionOutputStream() {
            private long position;

            @Override
            public long getPos() {
                return position;
            }

            @Override
            public void write(int value) throws IOException {
                outputStream.write(value);
                position++;
            }

            @Override
            public void write(byte[] buffer, int offset, int length) throws IOException {
                outputStream.write(buffer, offset, length);
                position += length;
            }

            @Override
            public void flush() throws IOException {
                outputStream.flush();
            }

            @Override
            public void close() throws IOException {
                outputStream.close();
            }
        };
    }
}
