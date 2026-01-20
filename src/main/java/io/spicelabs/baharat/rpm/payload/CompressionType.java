/*
 * Copyright 2026 Spice Labs, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.spicelabs.baharat.rpm.payload;

import com.github.luben.zstd.ZstdInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tukaani.xz.LZMAInputStream;
import org.tukaani.xz.XZInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

/**
 * Enumeration of compression types supported by RPM payloads.
 *
 * <p>RPM packages store their file content in a compressed CPIO archive (the "payload").
 * Different RPM versions and distributions use different compression algorithms:
 *
 * <table border="1">
 *   <caption>Compression Type History</caption>
 *   <tr><th>Type</th><th>Introduced</th><th>Notes</th></tr>
 *   <tr><td>gzip</td><td>Original</td><td>Legacy default, widely compatible</td></tr>
 *   <tr><td>bzip2</td><td>RPM 4.1</td><td>Better ratio than gzip, slower</td></tr>
 *   <tr><td>lzma</td><td>RPM 4.4.6</td><td>Predecessor to XZ</td></tr>
 *   <tr><td>xz</td><td>Fedora 12</td><td>Excellent ratio, reasonable speed</td></tr>
 *   <tr><td>zstd</td><td>Fedora 31</td><td>Best balance of ratio and speed</td></tr>
 * </table>
 *
 * <p>The compression type is stored in the PAYLOADCOMPRESSOR header tag and can also
 * be detected from the magic bytes at the start of the payload.
 *
 * @see PayloadReader
 */
public enum CompressionType {

    /**
     * Gzip compression (legacy default).
     */
    GZIP("gzip", new byte[]{0x1F, (byte) 0x8B}),

    /**
     * XZ compression (Fedora 12+ default).
     */
    XZ("xz", new byte[]{(byte) 0xFD, 0x37, 0x7A, 0x58, 0x5A, 0x00}),

    /**
     * Zstandard compression (Fedora 31+ default).
     */
    ZSTD("zstd", new byte[]{0x28, (byte) 0xB5, 0x2F, (byte) 0xFD}),

    /**
     * Bzip2 compression.
     */
    BZIP2("bzip2", new byte[]{0x42, 0x5A}),

    /**
     * LZMA compression (predecessor to XZ).
     */
    LZMA("lzma", new byte[]{0x5D, 0x00, 0x00});

    private static final Logger log = LoggerFactory.getLogger(CompressionType.class);

    private final String name;
    private final byte[] magic;

    CompressionType(String name, byte[] magic) {
        this.name = name;
        this.magic = magic;
    }

    /**
     * Returns the compression name as used in RPM headers.
     *
     * @return the compression name
     */
    public @NotNull String compressionName() {
        return name;
    }

    /**
     * Returns the magic bytes that identify this compression format.
     *
     * @return the magic bytes
     */
    public byte @NotNull [] magic() {
        return magic.clone();
    }

    /**
     * Wraps the given input stream with a decompression stream.
     *
     * @param input the compressed input stream
     * @return the decompression stream
     * @throws IOException if an I/O error occurs
     */
    public @NotNull InputStream decompress(@NotNull InputStream input) throws IOException {
        log.debug("Creating {} decompression stream", name);
        return switch (this) {
            case GZIP -> new GZIPInputStream(input);
            case XZ -> new XZInputStream(input);
            case ZSTD -> new ZstdInputStream(input);
            case BZIP2 -> new BZip2CompressorInputStream(input);
            case LZMA -> new LZMAInputStream(input);
        };
    }

    /**
     * Detects the compression type from the given magic bytes.
     *
     * @param data the first bytes of the compressed stream
     * @return an Optional containing the compression type, or empty if unknown
     */
    public static @NotNull Optional<CompressionType> detect(byte @NotNull [] data) {
        for (CompressionType type : values()) {
            if (startsWith(data, type.magic)) {
                log.trace("Detected compression type: {}", type.name);
                return Optional.of(type);
            }
        }
        log.trace("Unknown compression type (first bytes: {:02X} {:02X})",
                data.length > 0 ? data[0] & 0xFF : 0,
                data.length > 1 ? data[1] & 0xFF : 0);
        return Optional.empty();
    }

    /**
     * Looks up a compression type by its RPM header name.
     *
     * @param name the compression name (e.g., "gzip", "xz", "zstd")
     * @return an Optional containing the compression type, or empty if unknown
     */
    public static @NotNull Optional<CompressionType> fromName(@NotNull String name) {
        String lowerName = name.toLowerCase();
        for (CompressionType type : values()) {
            if (type.name.equals(lowerName)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
