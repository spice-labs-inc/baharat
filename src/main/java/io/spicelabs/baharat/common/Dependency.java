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

import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Represents a package dependency in a format-agnostic way.
 *
 * <p>This record provides a common model for dependencies across all package formats.
 * Each format has its own way of expressing dependencies, but they all share core concepts:
 * <ul>
 *   <li>Type - what kind of dependency (requires, provides, conflicts, etc.)</li>
 *   <li>Name - the package or capability name</li>
 *   <li>Version constraint - optional version requirement</li>
 *   <li>Comparison operator - how to compare versions (less than, greater than, etc.)</li>
 * </ul>
 *
 * <h2>Format Mapping</h2>
 * <table>
 *   <tr><th>Concept</th><th>RPM</th><th>DEB</th><th>Pacman</th><th>APK</th><th>FreeBSD</th></tr>
 *   <tr><td>Requires</td><td>Requires:</td><td>Depends:</td><td>depend</td><td>depend</td><td>deps</td></tr>
 *   <tr><td>Provides</td><td>Provides:</td><td>Provides:</td><td>provides</td><td>provides</td><td>provides</td></tr>
 *   <tr><td>Conflicts</td><td>Conflicts:</td><td>Conflicts:</td><td>conflict</td><td>-</td><td>conflicts</td></tr>
 *   <tr><td>Replaces</td><td>Obsoletes:</td><td>Replaces:</td><td>replaces</td><td>replaces</td><td>-</td></tr>
 * </table>
 *
 * @param type the type of dependency
 * @param name the package or capability name
 * @param version the version constraint (may be empty)
 * @param operator the comparison operator
 */
public record Dependency(
        @NotNull Type type,
        @NotNull String name,
        @NotNull Optional<String> version,
        @NotNull Operator operator
) {
    /**
     * Creates a dependency with only a name (no version constraint).
     *
     * @param type the dependency type
     * @param name the package name
     * @return the dependency
     */
    public static @NotNull Dependency of(@NotNull Type type, @NotNull String name) {
        return new Dependency(type, name, Optional.empty(), Operator.ANY);
    }

    /**
     * Creates a dependency with a version constraint.
     *
     * @param type the dependency type
     * @param name the package name
     * @param operator the comparison operator
     * @param version the version string
     * @return the dependency
     */
    public static @NotNull Dependency of(@NotNull Type type, @NotNull String name,
                                         @NotNull Operator operator, @NotNull String version) {
        return new Dependency(type, name, Optional.of(version), operator);
    }

    /**
     * Returns a formatted string representation of this dependency.
     *
     * @return the formatted dependency string (e.g., "openssl >= 1.1.0")
     */
    public @NotNull String toVersionedString() {
        if (version.isPresent() && !version.get().isEmpty() && operator != Operator.ANY) {
            return name + " " + operator.symbol() + " " + version.get();
        }
        return name;
    }

    /**
     * Returns true if this dependency has a version constraint.
     *
     * @return true if version constrained
     */
    public boolean hasVersionConstraint() {
        return version.isPresent() && !version.get().isEmpty() && operator != Operator.ANY;
    }

    /**
     * Dependency type classification.
     */
    public enum Type {
        /** Package requires this dependency to function */
        REQUIRES,
        /** Package provides this capability */
        PROVIDES,
        /** Package conflicts with this dependency */
        CONFLICTS,
        /** Package obsoletes/replaces this dependency */
        OBSOLETES,
        /** Package recommends (weak) this dependency */
        RECOMMENDS,
        /** Package suggests (weak) this dependency */
        SUGGESTS,
        /** Package supplements this dependency */
        SUPPLEMENTS,
        /** Package enhances this dependency */
        ENHANCES,
        /** Build-time dependency */
        BUILD_DEPENDS,
        /** Pre-installation dependency */
        PRE_DEPENDS
    }

    /**
     * Version comparison operators.
     */
    public enum Operator {
        /** Any version is acceptable */
        ANY(""),
        /** Exactly equal to */
        EQUAL("="),
        /** Less than */
        LESS_THAN("<"),
        /** Less than or equal to */
        LESS_THAN_OR_EQUAL("<="),
        /** Greater than */
        GREATER_THAN(">"),
        /** Greater than or equal to */
        GREATER_THAN_OR_EQUAL(">="),
        /** Not equal to */
        NOT_EQUAL("!=");

        private final String symbol;

        Operator(String symbol) {
            this.symbol = symbol;
        }

        /**
         * Returns the operator symbol.
         *
         * @return the symbol (e.g., ">=", "<")
         */
        public @NotNull String symbol() {
            return symbol;
        }

        /**
         * Parses an operator from its symbol.
         *
         * @param symbol the operator symbol
         * @return the operator, or ANY if not recognized
         */
        public static @NotNull Operator fromSymbol(@NotNull String symbol) {
            return switch (symbol.trim()) {
                case "=" -> EQUAL;
                case "==" -> EQUAL;
                case "<" -> LESS_THAN;
                case "<=" -> LESS_THAN_OR_EQUAL;
                case ">" -> GREATER_THAN;
                case ">=" -> GREATER_THAN_OR_EQUAL;
                case "!=" -> NOT_EQUAL;
                case "<>" -> NOT_EQUAL;
                default -> ANY;
            };
        }
    }
}
