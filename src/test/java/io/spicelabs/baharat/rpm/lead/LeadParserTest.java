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
package io.spicelabs.baharat.rpm.lead;

import io.spicelabs.baharat.rpm.exception.InvalidFormatException;
import io.spicelabs.baharat.rpm.io.BinaryReader;
import io.spicelabs.baharat.rpm.testdata.TestFiles;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeadParserTest {

    @Test
    void parsesValidLead() throws Exception {
        byte[] leadBytes = TestFiles.createTestLead();
        BinaryReader reader = new BinaryReader(new ByteArrayInputStream(leadBytes));

        Lead lead = LeadParser.parse(reader);

        assertThat(lead.majorVersion()).isEqualTo(3);
        assertThat(lead.minorVersion()).isEqualTo(0);
        assertThat(lead.type()).isEqualTo(Lead.TYPE_BINARY);
        assertThat(lead.name()).isEqualTo("test-package");
        assertThat(lead.isBinary()).isTrue();
        assertThat(lead.isSource()).isFalse();
        assertThat(lead.version()).isEqualTo("3.0");
    }

    @Test
    void rejectsInvalidMagic() {
        byte[] leadBytes = TestFiles.createTestLead();
        leadBytes[0] = 0x00;  // Corrupt magic

        BinaryReader reader = new BinaryReader(new ByteArrayInputStream(leadBytes));

        assertThatThrownBy(() -> LeadParser.parse(reader))
                .isInstanceOf(InvalidFormatException.class)
                .hasMessageContaining("Invalid RPM magic");
    }

    @Test
    void rejectsUnsupportedVersion() {
        byte[] leadBytes = TestFiles.createTestLead();
        leadBytes[4] = 2;  // Version 2.x (not supported)

        BinaryReader reader = new BinaryReader(new ByteArrayInputStream(leadBytes));

        assertThatThrownBy(() -> LeadParser.parse(reader))
                .isInstanceOf(InvalidFormatException.class)
                .hasMessageContaining("Unsupported RPM version");
    }

    @Test
    void rejectsInvalidType() {
        byte[] leadBytes = TestFiles.createTestLead();
        leadBytes[6] = 0;
        leadBytes[7] = 5;  // Invalid type

        BinaryReader reader = new BinaryReader(new ByteArrayInputStream(leadBytes));

        assertThatThrownBy(() -> LeadParser.parse(reader))
                .isInstanceOf(InvalidFormatException.class)
                .hasMessageContaining("Invalid RPM type");
    }

    @Test
    void parsesSourcePackageType() throws Exception {
        byte[] leadBytes = TestFiles.createTestLead();
        leadBytes[6] = 0;
        leadBytes[7] = 1;  // Source type

        BinaryReader reader = new BinaryReader(new ByteArrayInputStream(leadBytes));

        Lead lead = LeadParser.parse(reader);

        assertThat(lead.type()).isEqualTo(Lead.TYPE_SOURCE);
        assertThat(lead.isSource()).isTrue();
        assertThat(lead.isBinary()).isFalse();
    }

    @Test
    void parsesVersion4() throws Exception {
        byte[] leadBytes = TestFiles.createTestLead();
        leadBytes[4] = 4;  // Version 4.x

        BinaryReader reader = new BinaryReader(new ByteArrayInputStream(leadBytes));

        Lead lead = LeadParser.parse(reader);

        assertThat(lead.majorVersion()).isEqualTo(4);
        assertThat(lead.version()).isEqualTo("4.0");
    }

    @Test
    void leadSizeIs96Bytes() {
        assertThat(Lead.SIZE).isEqualTo(96);
    }
}
