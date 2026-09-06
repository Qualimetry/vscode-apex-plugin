/*
 * Copyright 2026 SHAZAM Analytics Ltd
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
package com.qualimetry.apex.lsp;

import org.eclipse.lsp4j.DiagnosticSeverity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeverityMapTest {

    @Test
    void anyRuleDefaultsToWarning() {
        assertThat(SeverityMap.getSeverity("qa-apex-syntax")).isEqualTo(DiagnosticSeverity.Warning);
        assertThat(SeverityMap.getSeverity("qa-convention-abstract-naming")).isEqualTo(DiagnosticSeverity.Warning);
        assertThat(SeverityMap.getSeverity("qa-security-no-system-debug")).isEqualTo(DiagnosticSeverity.Warning);
    }

    @Test
    void unknownRuleDefaultsToWarning() {
        assertThat(SeverityMap.getSeverity("non-existent-rule")).isEqualTo(DiagnosticSeverity.Warning);
        assertThat(SeverityMap.getSeverity("")).isEqualTo(DiagnosticSeverity.Warning);
    }

    @Test
    void blockerAndCriticalMapToError() {
        assertThat(SeverityMap.sonarToDiagnosticSeverity("blocker")).isEqualTo(DiagnosticSeverity.Error);
        assertThat(SeverityMap.sonarToDiagnosticSeverity("critical")).isEqualTo(DiagnosticSeverity.Error);
    }

    @Test
    void majorMapsToWarning() {
        assertThat(SeverityMap.sonarToDiagnosticSeverity("major")).isEqualTo(DiagnosticSeverity.Warning);
    }

    @Test
    void minorMapsToInformation() {
        assertThat(SeverityMap.sonarToDiagnosticSeverity("minor")).isEqualTo(DiagnosticSeverity.Information);
    }

    @Test
    void infoMapsToHint() {
        assertThat(SeverityMap.sonarToDiagnosticSeverity("info")).isEqualTo(DiagnosticSeverity.Hint);
    }

    @Test
    void unknownSonarSeverityReturnsNull() {
        assertThat(SeverityMap.sonarToDiagnosticSeverity("unknown")).isNull();
    }

    @Test
    void nullAndBlankSonarSeverityReturnNull() {
        assertThat(SeverityMap.sonarToDiagnosticSeverity(null)).isNull();
        assertThat(SeverityMap.sonarToDiagnosticSeverity("")).isNull();
        assertThat(SeverityMap.sonarToDiagnosticSeverity("   ")).isNull();
    }

    @Test
    void sonarSeverityIsCaseInsensitive() {
        assertThat(SeverityMap.sonarToDiagnosticSeverity("BLOCKER")).isEqualTo(DiagnosticSeverity.Error);
        assertThat(SeverityMap.sonarToDiagnosticSeverity("Critical")).isEqualTo(DiagnosticSeverity.Error);
        assertThat(SeverityMap.sonarToDiagnosticSeverity("MAJOR")).isEqualTo(DiagnosticSeverity.Warning);
        assertThat(SeverityMap.sonarToDiagnosticSeverity("Minor")).isEqualTo(DiagnosticSeverity.Information);
        assertThat(SeverityMap.sonarToDiagnosticSeverity("INFO")).isEqualTo(DiagnosticSeverity.Hint);
    }
}
