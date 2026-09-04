package com.carddemo.migration;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the COBOL data sets shipped under {@code Cobol_Code/.../app/data}.
 *
 * <p>Three physical forms are supported:</p>
 * <ul>
 *   <li>the ASCII line-oriented fixtures in {@code app/data/ASCII}, one record per line;</li>
 *   <li>the EBCDIC fixed-length data sets in {@code app/data/EBCDIC}, decoded with code page
 *       {@code IBM037} and split on the declared record length; and</li>
 *   <li>data sets with no fixed record length, such as an IMS database unload, which are handed
 *       to the caller as raw bytes.</li>
 * </ul>
 *
 * <p>DATA-005: the codec is always explicit. The process default encoding never decides a record
 * format.</p>
 */
public class CobolRecordSource {

    /** IBM code page 037, the EBCDIC variant the supplied data sets use. */
    public static final Charset EBCDIC = Charset.forName("IBM037");

    private final Path filesystemRoot;

    public CobolRecordSource(String filesystemRoot) {
        this.filesystemRoot = (filesystemRoot == null || filesystemRoot.isBlank())
                ? null : Path.of(filesystemRoot);
    }

    /**
     * Resolves a data file, preferring the configured original {@code Cobol_Code} directory and
     * falling back to the copies bundled on the classpath.
     */
    public Resource resolve(String relativePath) {
        if (filesystemRoot != null) {
            Path candidate = filesystemRoot.resolve(relativePath);
            FileSystemResource resource = new FileSystemResource(candidate);
            if (resource.exists()) {
                return resource;
            }
        }
        return new ClassPathResource("cobol-data/" + relativePath);
    }

    /** Reads an ASCII fixture as one record per line, dropping trailing carriage returns. */
    public List<String> readAsciiLines(String relativePath) throws IOException {
        Resource resource = resolve(relativePath);
        List<String> records = new ArrayList<>();
        try (InputStream in = resource.getInputStream()) {
            String content = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
            for (String line : content.split("\n", -1)) {
                String record = line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
                if (!record.isBlank()) {
                    records.add(record);
                }
            }
        }
        return records;
    }

    /** Reads a fixed-length EBCDIC data set and returns the records decoded to text. */
    public List<String> readEbcdicRecords(String relativePath, int recordLength) throws IOException {
        Resource resource = resolve(relativePath);
        List<String> records = new ArrayList<>();
        try (InputStream in = resource.getInputStream()) {
            byte[] all = in.readAllBytes();
            for (int offset = 0; offset + recordLength <= all.length; offset += recordLength) {
                records.add(new String(all, offset, recordLength, EBCDIC));
            }
        }
        return records;
    }

    /**
     * Reads a data set whole, undecoded. Records that are neither line delimited nor of one fixed
     * length, such as the variable length segment occurrences of an IMS unload, carry binary
     * lengths and packed decimal fields that a character decode would destroy, so the caller works
     * on the bytes themselves.
     */
    public byte[] readBytes(String relativePath) throws IOException {
        Resource resource = resolve(relativePath);
        try (InputStream in = resource.getInputStream()) {
            return in.readAllBytes();
        }
    }

    /** True when the file can be resolved from either the filesystem or the classpath. */
    public boolean exists(String relativePath) {
        return resolve(relativePath).exists();
    }
}
