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

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link Dependency}.
 */
class DependencyTest {

    // Factory method tests

    @Test
    void createSimpleDependency() {
        Dependency dep = Dependency.of(Dependency.Type.REQUIRES, "openssl");

        assertThat(dep.type()).isEqualTo(Dependency.Type.REQUIRES);
        assertThat(dep.name()).isEqualTo("openssl");
        assertThat(dep.version()).isEmpty();
        assertThat(dep.operator()).isEqualTo(Dependency.Operator.ANY);
    }

    @Test
    void createVersionedDependency() {
        Dependency dep = Dependency.of(Dependency.Type.REQUIRES, "openssl",
                Dependency.Operator.GREATER_THAN_OR_EQUAL, "1.1.0");

        assertThat(dep.type()).isEqualTo(Dependency.Type.REQUIRES);
        assertThat(dep.name()).isEqualTo("openssl");
        assertThat(dep.version()).contains("1.1.0");
        assertThat(dep.operator()).isEqualTo(Dependency.Operator.GREATER_THAN_OR_EQUAL);
    }

    // Type tests

    @Test
    void allDependencyTypes() {
        assertThat(Dependency.Type.values()).containsExactlyInAnyOrder(
                Dependency.Type.REQUIRES,
                Dependency.Type.PROVIDES,
                Dependency.Type.CONFLICTS,
                Dependency.Type.OBSOLETES,
                Dependency.Type.RECOMMENDS,
                Dependency.Type.SUGGESTS,
                Dependency.Type.SUPPLEMENTS,
                Dependency.Type.ENHANCES,
                Dependency.Type.BUILD_DEPENDS,
                Dependency.Type.PRE_DEPENDS
        );
    }

    @Test
    void createProvidesDependency() {
        Dependency dep = Dependency.of(Dependency.Type.PROVIDES, "httpd");

        assertThat(dep.type()).isEqualTo(Dependency.Type.PROVIDES);
        assertThat(dep.name()).isEqualTo("httpd");
    }

    @Test
    void createConflictsDependency() {
        Dependency dep = Dependency.of(Dependency.Type.CONFLICTS, "old-package");

        assertThat(dep.type()).isEqualTo(Dependency.Type.CONFLICTS);
        assertThat(dep.name()).isEqualTo("old-package");
    }

    @Test
    void createObsoletesDependency() {
        Dependency dep = Dependency.of(Dependency.Type.OBSOLETES, "deprecated-package",
                Dependency.Operator.LESS_THAN, "2.0");

        assertThat(dep.type()).isEqualTo(Dependency.Type.OBSOLETES);
        assertThat(dep.name()).isEqualTo("deprecated-package");
        assertThat(dep.operator()).isEqualTo(Dependency.Operator.LESS_THAN);
    }

    // Operator tests

    @Test
    void allOperators() {
        assertThat(Dependency.Operator.values()).containsExactlyInAnyOrder(
                Dependency.Operator.ANY,
                Dependency.Operator.EQUAL,
                Dependency.Operator.LESS_THAN,
                Dependency.Operator.LESS_THAN_OR_EQUAL,
                Dependency.Operator.GREATER_THAN,
                Dependency.Operator.GREATER_THAN_OR_EQUAL,
                Dependency.Operator.NOT_EQUAL
        );
    }

    @Test
    void operatorSymbols() {
        assertThat(Dependency.Operator.ANY.symbol()).isEmpty();
        assertThat(Dependency.Operator.EQUAL.symbol()).isEqualTo("=");
        assertThat(Dependency.Operator.LESS_THAN.symbol()).isEqualTo("<");
        assertThat(Dependency.Operator.LESS_THAN_OR_EQUAL.symbol()).isEqualTo("<=");
        assertThat(Dependency.Operator.GREATER_THAN.symbol()).isEqualTo(">");
        assertThat(Dependency.Operator.GREATER_THAN_OR_EQUAL.symbol()).isEqualTo(">=");
        assertThat(Dependency.Operator.NOT_EQUAL.symbol()).isEqualTo("!=");
    }

    @Test
    void operatorFromSymbol() {
        assertThat(Dependency.Operator.fromSymbol("=")).isEqualTo(Dependency.Operator.EQUAL);
        assertThat(Dependency.Operator.fromSymbol("==")).isEqualTo(Dependency.Operator.EQUAL);
        assertThat(Dependency.Operator.fromSymbol("<")).isEqualTo(Dependency.Operator.LESS_THAN);
        assertThat(Dependency.Operator.fromSymbol("<=")).isEqualTo(Dependency.Operator.LESS_THAN_OR_EQUAL);
        assertThat(Dependency.Operator.fromSymbol(">")).isEqualTo(Dependency.Operator.GREATER_THAN);
        assertThat(Dependency.Operator.fromSymbol(">=")).isEqualTo(Dependency.Operator.GREATER_THAN_OR_EQUAL);
        assertThat(Dependency.Operator.fromSymbol("!=")).isEqualTo(Dependency.Operator.NOT_EQUAL);
        assertThat(Dependency.Operator.fromSymbol("<>")).isEqualTo(Dependency.Operator.NOT_EQUAL);
    }

    @Test
    void operatorFromSymbolUnknown() {
        assertThat(Dependency.Operator.fromSymbol("")).isEqualTo(Dependency.Operator.ANY);
        assertThat(Dependency.Operator.fromSymbol("~")).isEqualTo(Dependency.Operator.ANY);
        assertThat(Dependency.Operator.fromSymbol("unknown")).isEqualTo(Dependency.Operator.ANY);
    }

    @Test
    void operatorFromSymbolTrimsWhitespace() {
        assertThat(Dependency.Operator.fromSymbol("  >=  ")).isEqualTo(Dependency.Operator.GREATER_THAN_OR_EQUAL);
    }

    // toVersionedString tests

    @Test
    void toVersionedStringWithoutVersion() {
        Dependency dep = Dependency.of(Dependency.Type.REQUIRES, "openssl");

        assertThat(dep.toVersionedString()).isEqualTo("openssl");
    }

    @Test
    void toVersionedStringWithVersion() {
        Dependency dep = Dependency.of(Dependency.Type.REQUIRES, "openssl",
                Dependency.Operator.GREATER_THAN_OR_EQUAL, "1.1.0");

        assertThat(dep.toVersionedString()).isEqualTo("openssl >= 1.1.0");
    }

    @Test
    void toVersionedStringWithExactVersion() {
        Dependency dep = Dependency.of(Dependency.Type.REQUIRES, "openssl",
                Dependency.Operator.EQUAL, "3.0.0");

        assertThat(dep.toVersionedString()).isEqualTo("openssl = 3.0.0");
    }

    @Test
    void toVersionedStringWithAnyOperator() {
        Dependency dep = new Dependency(Dependency.Type.REQUIRES, "openssl",
                Optional.of("1.0"), Dependency.Operator.ANY);

        // Even with version present, ANY operator means no constraint shown
        assertThat(dep.toVersionedString()).isEqualTo("openssl");
    }

    @Test
    void toVersionedStringWithEmptyVersion() {
        Dependency dep = new Dependency(Dependency.Type.REQUIRES, "openssl",
                Optional.of(""), Dependency.Operator.GREATER_THAN_OR_EQUAL);

        assertThat(dep.toVersionedString()).isEqualTo("openssl");
    }

    // hasVersionConstraint tests

    @Test
    void hasVersionConstraintTrue() {
        Dependency dep = Dependency.of(Dependency.Type.REQUIRES, "openssl",
                Dependency.Operator.GREATER_THAN_OR_EQUAL, "1.1.0");

        assertThat(dep.hasVersionConstraint()).isTrue();
    }

    @Test
    void hasVersionConstraintFalseNoVersion() {
        Dependency dep = Dependency.of(Dependency.Type.REQUIRES, "openssl");

        assertThat(dep.hasVersionConstraint()).isFalse();
    }

    @Test
    void hasVersionConstraintFalseAnyOperator() {
        Dependency dep = new Dependency(Dependency.Type.REQUIRES, "openssl",
                Optional.of("1.0"), Dependency.Operator.ANY);

        assertThat(dep.hasVersionConstraint()).isFalse();
    }

    @Test
    void hasVersionConstraintFalseEmptyVersion() {
        Dependency dep = new Dependency(Dependency.Type.REQUIRES, "openssl",
                Optional.of(""), Dependency.Operator.GREATER_THAN_OR_EQUAL);

        assertThat(dep.hasVersionConstraint()).isFalse();
    }

    // Record equality tests

    @Test
    void dependencyEquality() {
        Dependency dep1 = Dependency.of(Dependency.Type.REQUIRES, "openssl",
                Dependency.Operator.GREATER_THAN_OR_EQUAL, "1.1.0");
        Dependency dep2 = Dependency.of(Dependency.Type.REQUIRES, "openssl",
                Dependency.Operator.GREATER_THAN_OR_EQUAL, "1.1.0");

        assertThat(dep1).isEqualTo(dep2);
        assertThat(dep1.hashCode()).isEqualTo(dep2.hashCode());
    }

    @Test
    void dependencyInequality() {
        Dependency dep1 = Dependency.of(Dependency.Type.REQUIRES, "openssl");
        Dependency dep2 = Dependency.of(Dependency.Type.PROVIDES, "openssl");

        assertThat(dep1).isNotEqualTo(dep2);
    }

    // Edge cases

    @Test
    void dependencyWithSpecialCharactersInName() {
        Dependency dep = Dependency.of(Dependency.Type.PROVIDES, "lib:c.99.0");

        assertThat(dep.name()).isEqualTo("lib:c.99.0");
        assertThat(dep.toVersionedString()).isEqualTo("lib:c.99.0");
    }

    @Test
    void dependencyWithEpochInVersion() {
        Dependency dep = Dependency.of(Dependency.Type.REQUIRES, "zlib",
                Dependency.Operator.GREATER_THAN_OR_EQUAL, "1:1.2.11");

        assertThat(dep.version()).contains("1:1.2.11");
        assertThat(dep.toVersionedString()).isEqualTo("zlib >= 1:1.2.11");
    }

    @Test
    void dependencyToString() {
        Dependency dep = Dependency.of(Dependency.Type.REQUIRES, "openssl",
                Dependency.Operator.GREATER_THAN_OR_EQUAL, "1.1.0");

        // Records have automatic toString
        assertThat(dep.toString()).contains("openssl");
        assertThat(dep.toString()).contains("REQUIRES");
        assertThat(dep.toString()).contains("1.1.0");
    }
}
