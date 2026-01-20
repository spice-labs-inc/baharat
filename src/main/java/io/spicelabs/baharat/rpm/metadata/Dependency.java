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
package io.spicelabs.baharat.rpm.metadata;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Represents a dependency in an RPM package.
 * Dependencies include requires, provides, conflicts, obsoletes, and weak dependencies.
 *
 * @param type the type of dependency
 * @param name the capability or package name
 * @param version the version constraint (may be empty)
 * @param flags the dependency flags
 */
public record Dependency(
        @NotNull DependencyType type,
        @NotNull String name,
        @NotNull Optional<String> version,
        int flags
) {
    // Dependency flag constants
    public static final int RPMSENSE_LESS = (1 << 1);
    public static final int RPMSENSE_GREATER = (1 << 2);
    public static final int RPMSENSE_EQUAL = (1 << 3);
    public static final int RPMSENSE_PREREQ = (1 << 6);
    public static final int RPMSENSE_INTERP = (1 << 8);
    public static final int RPMSENSE_SCRIPT_PRE = (1 << 9);
    public static final int RPMSENSE_SCRIPT_POST = (1 << 10);
    public static final int RPMSENSE_SCRIPT_PREUN = (1 << 11);
    public static final int RPMSENSE_SCRIPT_POSTUN = (1 << 12);
    public static final int RPMSENSE_SCRIPT_VERIFY = (1 << 13);
    public static final int RPMSENSE_FIND_REQUIRES = (1 << 14);
    public static final int RPMSENSE_FIND_PROVIDES = (1 << 15);
    public static final int RPMSENSE_TRIGGERIN = (1 << 16);
    public static final int RPMSENSE_TRIGGERUN = (1 << 17);
    public static final int RPMSENSE_TRIGGERPOSTUN = (1 << 18);
    public static final int RPMSENSE_MISSINGOK = (1 << 19);
    public static final int RPMSENSE_RPMLIB = (1 << 24);
    public static final int RPMSENSE_TRIGGERPREIN = (1 << 25);
    public static final int RPMSENSE_KEYRING = (1 << 26);
    public static final int RPMSENSE_CONFIG = (1 << 28);
    public static final int RPMSENSE_PRETRANS = (1 << 7);
    public static final int RPMSENSE_POSTTRANS = (1 << 5);
    public static final int RPMSENSE_PREUNTRANS = (1 << 31);
    public static final int RPMSENSE_POSTUNTRANS = (1 << 30);

    /**
     * Returns true if this dependency requires the version to be less than the specified version.
     *
     * @return true if less-than comparison
     */
    public boolean isLessThan() {
        return (flags & RPMSENSE_LESS) != 0;
    }

    /**
     * Returns true if this dependency requires the version to be greater than the specified version.
     *
     * @return true if greater-than comparison
     */
    public boolean isGreaterThan() {
        return (flags & RPMSENSE_GREATER) != 0;
    }

    /**
     * Returns true if this dependency requires an exact version match.
     *
     * @return true if equal comparison
     */
    public boolean isEqual() {
        return (flags & RPMSENSE_EQUAL) != 0;
    }

    /**
     * Returns true if this is a pre-requisite dependency.
     *
     * @return true if pre-requisite
     */
    public boolean isPrereq() {
        return (flags & RPMSENSE_PREREQ) != 0;
    }

    /**
     * Returns true if this dependency is on an rpmlib feature.
     *
     * @return true if rpmlib dependency
     */
    public boolean isRpmlib() {
        return (flags & RPMSENSE_RPMLIB) != 0;
    }

    /**
     * Returns the version comparison operator as a string.
     *
     * @return the operator string (e.g., "<", ">", "=", "<=", ">=")
     */
    public @NotNull String operator() {
        boolean less = isLessThan();
        boolean greater = isGreaterThan();
        boolean equal = isEqual();

        if (less && equal) return "<=";
        if (greater && equal) return ">=";
        if (less) return "<";
        if (greater) return ">";
        if (equal) return "=";
        return "";
    }

    /**
     * Returns a formatted string representation of this dependency.
     *
     * @return the formatted dependency string
     */
    public @NotNull String toVersionedString() {
        if (version.isPresent() && !version.get().isEmpty()) {
            String op = operator();
            if (!op.isEmpty()) {
                return name + " " + op + " " + version.get();
            }
        }
        return name;
    }
}
