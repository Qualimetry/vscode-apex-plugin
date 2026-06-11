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

import com.qualimetry.sonar.apex.analyzer.visitor.BaseCheck;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticMapperTest {

    @Test
    void lineMapsTo0Based() {
        BaseCheck.Issue issue = new BaseCheck.Issue(5, "Avoid this pattern", "qa-apex-syntax");

        Diagnostic diag = DiagnosticMapper.toDiagnostic(issue);

        assertThat(diag.getRange().getStart().getLine()).isEqualTo(4);
        assertThat(diag.getRange().getStart().getCharacter()).isZero();
    }

    @Test
    void lineZeroOrNegativeClampsToZero() {
        BaseCheck.Issue issue = new BaseCheck.Issue(0, "File-level issue", "qa-apex-syntax");

        Diagnostic diag = DiagnosticMapper.toDiagnostic(issue);

        assertThat(diag.getRange().getStart().getLine()).isZero();
    }

    @Test
    void endColumnSpansFullLine() {
        BaseCheck.Issue issue = new BaseCheck.Issue(3, "msg", "qa-convention-abstract-naming");

        Diagnostic diag = DiagnosticMapper.toDiagnostic(issue);

        assertThat(diag.getRange().getEnd().getLine()).isEqualTo(2);
        assertThat(diag.getRange().getEnd().getCharacter()).isEqualTo(256);
    }

    @Test
    void messageIsPreserved() {
        BaseCheck.Issue issue = new BaseCheck.Issue(1, "Variable should be final", "qa-convention-no-final-local-variable");

        Diagnostic diag = DiagnosticMapper.toDiagnostic(issue);

        assertThat(diag.getMessage()).isEqualTo("Variable should be final");
    }

    @Test
    void codeIsRuleKey() {
        BaseCheck.Issue issue = new BaseCheck.Issue(1, "msg", "qa-security-no-system-debug");

        Diagnostic diag = DiagnosticMapper.toDiagnostic(issue);

        assertThat(diag.getCode().getLeft()).isEqualTo("qa-security-no-system-debug");
    }

    @Test
    void sourceIsQualimetryApex() {
        BaseCheck.Issue issue = new BaseCheck.Issue(1, "msg", "qa-apex-syntax");

        Diagnostic diag = DiagnosticMapper.toDiagnostic(issue);

        assertThat(diag.getSource()).isEqualTo("qualimetry-apex");
    }

    @Test
    void severityDefaultsToWarning() {
        BaseCheck.Issue issue = new BaseCheck.Issue(1, "msg", "qa-apex-syntax");

        Diagnostic diag = DiagnosticMapper.toDiagnostic(issue);

        assertThat(diag.getSeverity()).isEqualTo(DiagnosticSeverity.Warning);
    }
}
