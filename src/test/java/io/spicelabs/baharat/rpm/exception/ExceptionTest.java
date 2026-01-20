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
package io.spicelabs.baharat.rpm.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionTest {

    @Test
    void rpmExceptionWithMessage() {
        FormatException ex = new FormatException("Test message");

        assertThat(ex.getMessage()).isEqualTo("Test message");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void rpmExceptionWithMessageAndCause() {
        RuntimeException cause = new RuntimeException("Cause");
        FormatException ex = new FormatException("Test message", cause);

        assertThat(ex.getMessage()).isEqualTo("Test message");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void invalidRpmExceptionWithMessage() {
        InvalidFormatException ex = new InvalidFormatException("Invalid format");

        assertThat(ex.getMessage()).isEqualTo("Invalid format");
        assertThat(ex.getCause()).isNull();
        assertThat(ex).isInstanceOf(FormatException.class);
    }

    @Test
    void invalidRpmExceptionWithMessageAndCause() {
        NumberFormatException cause = new NumberFormatException("Bad number");
        InvalidFormatException ex = new InvalidFormatException("Invalid header", cause);

        assertThat(ex.getMessage()).isEqualTo("Invalid header");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void unsupportedRpmExceptionWithMessage() {
        UnsupportedFormatException ex = new UnsupportedFormatException("Unknown compression");

        assertThat(ex.getMessage()).isEqualTo("Unknown compression");
        assertThat(ex.getCause()).isNull();
        assertThat(ex).isInstanceOf(FormatException.class);
    }

    @Test
    void unsupportedRpmExceptionWithMessageAndCause() {
        IllegalArgumentException cause = new IllegalArgumentException("Bad arg");
        UnsupportedFormatException ex = new UnsupportedFormatException("Not supported", cause);

        assertThat(ex.getMessage()).isEqualTo("Not supported");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void exceptionsAreThrowable() {
        assertThat(new FormatException("msg")).isInstanceOf(Exception.class);
        assertThat(new InvalidFormatException("msg")).isInstanceOf(Exception.class);
        assertThat(new UnsupportedFormatException("msg")).isInstanceOf(Exception.class);
    }
}
