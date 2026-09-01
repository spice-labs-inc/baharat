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
package io.spicelabs.baharat.testdata;

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
 * Utility class for managing test package files for various package formats.
 */
public final class PackageTestFiles {

    private PackageTestFiles() {
    }

    /**
     * Returns the path to a test file in the given resource directory.
     *
     * @param resourceDir the resource directory (e.g., "debs", "apks")
     * @param filename the filename
     * @return the path to the file
     * @throws IllegalArgumentException if the file is not found
     */
    public static Path getPath(String resourceDir, String filename) {
        URL url = PackageTestFiles.class.getClassLoader()
                .getResource(resourceDir + "/" + filename);
        if (url == null) {
            throw new IllegalArgumentException("Test file not found: " + resourceDir + "/" + filename);
        }
        try {
            return Paths.get(url.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns an input stream for a test file.
     *
     * @param resourceDir the resource directory
     * @param filename the filename
     * @return the input stream
     * @throws IllegalArgumentException if the file is not found
     */
    public static InputStream getStream(String resourceDir, String filename) {
        InputStream stream = PackageTestFiles.class.getClassLoader()
                .getResourceAsStream(resourceDir + "/" + filename);
        if (stream == null) {
            throw new IllegalArgumentException("Test file not found: " + resourceDir + "/" + filename);
        }
        return stream;
    }

    /**
     * Checks if a test file exists.
     *
     * @param resourceDir the resource directory
     * @param filename the filename
     * @return true if the file exists
     */
    public static boolean exists(String resourceDir, String filename) {
        return PackageTestFiles.class.getClassLoader()
                .getResource(resourceDir + "/" + filename) != null;
    }

    /**
     * Checks if a resource directory exists and has files.
     *
     * @param resourceDir the resource directory
     * @return true if the directory exists and contains files
     */
    public static boolean hasFiles(String resourceDir) {
        URL url = PackageTestFiles.class.getClassLoader().getResource(resourceDir);
        if (url == null) {
            return false;
        }
        try {
            Path base = Paths.get(url.toURI());
            try (Stream<Path> walk = Files.list(base)) {
                return walk.anyMatch(Files::isRegularFile);
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns all files in a resource directory with the given extension.
     *
     * @param resourceDir the resource directory
     * @param extension the file extension (e.g., ".deb", ".apk")
     * @return a list of paths to all matching files
     * @throws IOException if an I/O error occurs
     */
    public static List<Path> getAllFiles(String resourceDir, String extension) throws IOException {
        URL url = PackageTestFiles.class.getClassLoader().getResource(resourceDir);
        if (url == null) {
            return List.of();
        }

        try {
            Path base = Paths.get(url.toURI());
            List<Path> files = new ArrayList<>();

            try (Stream<Path> walk = Files.walk(base)) {
                walk.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(extension))
                        .forEach(files::add);
            }

            return files;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the first N files from a resource directory with the given extension.
     *
     * @param resourceDir the resource directory
     * @param extension the file extension
     * @param limit the maximum number of files to return
     * @return a list of paths to matching files
     * @throws IOException if an I/O error occurs
     */
    public static List<Path> getFiles(String resourceDir, String extension, int limit) throws IOException {
        URL url = PackageTestFiles.class.getClassLoader().getResource(resourceDir);
        if (url == null) {
            return List.of();
        }

        try {
            Path base = Paths.get(url.toURI());
            List<Path> files = new ArrayList<>();

            try (Stream<Path> walk = Files.walk(base)) {
                walk.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(extension))
                        .limit(limit)
                        .forEach(files::add);
            }

            return files;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    // Convenience constants for resource directories
    public static final String RPMS = "rpms";
    public static final String DEBS = "debs";
    public static final String APKS = "apks";
    public static final String PACMAN = "pacman";
    public static final String OPENBSD = "openbsd";
    public static final String FREEBSD = "freebsd";
}
