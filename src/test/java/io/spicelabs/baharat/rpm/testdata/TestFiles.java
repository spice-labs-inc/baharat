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
package io.spicelabs.baharat.rpm.testdata;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Utility class for managing test RPM files.
 */
public final class TestFiles {

    private static final String TEST_RESOURCES = "rpms";

    private TestFiles() {
    }

    /**
     * Returns the path to a test RPM file.
     *
     * @param relativePath the path relative to the rpms directory
     * @return the path to the file
     * @throws IllegalArgumentException if the file is not found
     */
    public static Path getPath(String relativePath) {
        URL url = TestFiles.class.getClassLoader()
                .getResource(TEST_RESOURCES + "/" + relativePath);
        if (url == null) {
            throw new IllegalArgumentException("Test file not found: " + relativePath);
        }
        try {
            return Paths.get(url.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns an input stream for a test RPM file.
     *
     * @param relativePath the path relative to the rpms directory
     * @return the input stream
     * @throws IllegalArgumentException if the file is not found
     */
    public static InputStream getStream(String relativePath) {
        InputStream stream = TestFiles.class.getClassLoader()
                .getResourceAsStream(TEST_RESOURCES + "/" + relativePath);
        if (stream == null) {
            throw new IllegalArgumentException("Test file not found: " + relativePath);
        }
        return stream;
    }

    /**
     * Checks if a test file exists.
     *
     * @param relativePath the path relative to the rpms directory
     * @return true if the file exists
     */
    public static boolean exists(String relativePath) {
        return TestFiles.class.getClassLoader()
                .getResource(TEST_RESOURCES + "/" + relativePath) != null;
    }

    /**
     * Returns all RPM files in the test resources.
     *
     * @return a list of paths to all test RPM files
     * @throws IOException if an I/O error occurs
     */
    public static List<Path> getAllRpmFiles() throws IOException {
        URL url = TestFiles.class.getClassLoader().getResource(TEST_RESOURCES);
        if (url == null) {
            return List.of();
        }

        try {
            Path base = Paths.get(url.toURI());
            List<Path> files = new ArrayList<>();

            try (Stream<Path> walk = Files.walk(base)) {
                walk.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".rpm"))
                        .forEach(files::add);
            }

            return files;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns RPM files from a specific subdirectory.
     *
     * @param subdir the subdirectory path
     * @return a list of paths to RPM files in that directory
     * @throws IOException if an I/O error occurs
     */
    public static List<Path> getRpmFilesIn(String subdir) throws IOException {
        URL url = TestFiles.class.getClassLoader().getResource(TEST_RESOURCES + "/" + subdir);
        if (url == null) {
            return List.of();
        }

        try {
            Path base = Paths.get(url.toURI());
            List<Path> files = new ArrayList<>();

            try (Stream<Path> walk = Files.walk(base)) {
                walk.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".rpm"))
                        .forEach(files::add);
            }

            return files;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates a minimal valid RPM lead section for testing.
     *
     * @return a 96-byte array containing a valid lead
     */
    public static byte[] createTestLead() {
        byte[] lead = new byte[96];

        // Magic number: 0xEDABEEDB
        lead[0] = (byte) 0xED;
        lead[1] = (byte) 0xAB;
        lead[2] = (byte) 0xEE;
        lead[3] = (byte) 0xDB;

        // Version: 3.0
        lead[4] = 3;  // major
        lead[5] = 0;  // minor

        // Type: binary (0)
        lead[6] = 0;
        lead[7] = 0;

        // Architecture: 1 (i386)
        lead[8] = 0;
        lead[9] = 1;

        // Name: "test-package"
        String name = "test-package";
        byte[] nameBytes = name.getBytes();
        System.arraycopy(nameBytes, 0, lead, 10, nameBytes.length);

        // OS: 1 (Linux)
        lead[76] = 0;
        lead[77] = 1;

        // Signature type: 5
        lead[78] = 0;
        lead[79] = 5;

        return lead;
    }
}
