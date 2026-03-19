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

import com.qualimetry.sonar.apex.analyzer.checks.CheckList;
import com.qualimetry.sonar.apex.analyzer.visitor.BaseCheck;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Text document service: on open/change/save, run analyzer and publish diagnostics.
 */
public class ApexTextDocumentService implements TextDocumentService {

    private final ConcurrentHashMap<String, String> documents = new ConcurrentHashMap<>();
    private LanguageClient client;
    private final List<BaseCheck> checks = new ArrayList<>();

    public ApexTextDocumentService() {
        try {
            for (Class<? extends BaseCheck> clazz : CheckList.getAllChecks()) {
                checks.add(clazz.getDeclaredConstructor().newInstance());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not instantiate checks", e);
        }
    }

    public void setClient(LanguageClient client) {
        this.client = client;
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        String text = params.getTextDocument().getText();
        documents.put(uri, text);
        analyzeAndPublish(uri, text);
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        String fullText = params.getContentChanges().isEmpty()
                ? documents.get(uri)
                : params.getContentChanges().get(0).getText();
        if (fullText != null) {
            documents.put(uri, fullText);
            analyzeAndPublish(uri, fullText);
        }
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        String text = documents.get(uri);
        if (text != null) {
            analyzeAndPublish(uri, text);
        }
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        documents.remove(uri);
        if (client != null) {
            client.publishDiagnostics(new PublishDiagnosticsParams(uri, List.of()));
        }
    }

    private void analyzeAndPublish(String uri, String content) {
        if (client == null) return;

        try {
            List<org.eclipse.lsp4j.Diagnostic> diagnostics = new ArrayList<>();
            for (BaseCheck check : checks) {
                check.clearIssues();
                check.scan(null, content != null ? content : "");
                for (BaseCheck.Issue issue : check.getIssues()) {
                    org.eclipse.lsp4j.Diagnostic d = DiagnosticMapper.toDiagnostic(issue);
                    if (d != null) diagnostics.add(d);
                }
            }
            client.publishDiagnostics(new PublishDiagnosticsParams(uri, diagnostics));
        } catch (Exception e) {
            client.publishDiagnostics(new PublishDiagnosticsParams(uri, List.of()));
        }
    }
}
