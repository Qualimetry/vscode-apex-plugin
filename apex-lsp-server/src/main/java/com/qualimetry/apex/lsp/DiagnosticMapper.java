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
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

/**
 * Maps analysis issues to LSP Diagnostics.
 */
public final class DiagnosticMapper {

    private DiagnosticMapper() {
    }

    /**
     * Converts a {@link BaseCheck.Issue} to an LSP {@link Diagnostic}.
     */
    public static Diagnostic toDiagnostic(BaseCheck.Issue issue) {
        Diagnostic d = new Diagnostic();
        d.setMessage(issue.message());
        d.setCode(issue.ruleKey());
        d.setSource("qualimetry-apex");
        d.setSeverity(SeverityMap.getSeverity(issue.ruleKey()));

        int line0 = Math.max(0, issue.line() - 1);
        d.setRange(new Range(
                new Position(line0, 0),
                new Position(line0, 256)
        ));
        return d;
    }
}
