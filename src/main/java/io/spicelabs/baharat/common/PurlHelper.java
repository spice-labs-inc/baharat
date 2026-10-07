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
package io.spicelabs.baharat.common;

import io.spicelabs.coordinates.Purl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class PurlHelper {

    private PurlHelper() {}

    @NotNull
    public static Map<String, String> newQualifiers() {
        return new LinkedHashMap<>();
    }

    /**
     * The single construction path: normalize and validate through the coordinates library.
     * A type that requires a namespace but has none (e.g. an RPM whose distribution cannot
     * be inferred) receives {@link Purl#UNKNOWN_NAMESPACE}, which readers can recognize with
     * {@link Purl#isNamespaceUnknown()}. Any other violation surfaces as a
     * {@link Purl.PurlException}.
     */
    @NotNull
    public static Purl build(
            @NotNull String type,
            @Nullable String namespace,
            @NotNull String name,
            @Nullable String version,
            @NotNull Map<String, String> qualifiers) {
        return Purl.normalize(new Purl(type, namespace, name, version, qualifiers, null),
                Purl.MissingNamespace.UNKNOWN);
    }

    @NotNull
    public static String normalizeNamespace(@Nullable String namespace) {
        if (namespace == null || namespace.isEmpty()) {
            return "";
        }
        return namespace.toLowerCase(Locale.ROOT).replaceAll("\\s+", "-");
    }

    public static boolean isArchitectureIndependent(@Nullable String arch) {
        if (arch == null || arch.isEmpty()) {
            return true;
        }
        return switch (arch.toLowerCase(Locale.ROOT)) {
            case "noarch", "all", "any", "*" -> true;
            default -> false;
        };
    }
}
