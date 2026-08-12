package com.ftd.fraud_transaction_detector.aml.training.infrastructure;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Comparator;

@Component
public class FileChecksumService {

    public String sha256(Path path) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        try (InputStream inputStream = Files.newInputStream(path)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = inputStream.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public String sha256Bundle(Path path) throws IOException {
        if (Files.isRegularFile(path)) return sha256(path);
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("Artifact path must be a file or directory: " + path);
        }
        StringBuilder manifest = new StringBuilder();
        try (var files = Files.walk(path)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(candidate -> path.relativize(candidate).toString()))
                    .toList()) {
                manifest.append(path.relativize(file).toString().replace('\\', '/'))
                        .append(':').append(Files.size(file))
                        .append(':').append(sha256(file)).append('\n');
            }
        }
        if (manifest.isEmpty()) throw new IllegalArgumentException("Artifact directory is empty: " + path);
        return sha256(manifest.toString());
    }

    public long bundleSize(Path path) throws IOException {
        if (Files.isRegularFile(path)) return Files.size(path);
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("Artifact path must be a file or directory: " + path);
        }
        try (var files = Files.walk(path)) {
            return files.filter(Files::isRegularFile).mapToLong(file -> {
                try {
                    return Files.size(file);
                } catch (IOException exception) {
                    throw new IllegalStateException("Unable to inspect artifact file " + file, exception);
                }
            }).sum();
        }
    }
}
