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

import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ApexTextDocumentServiceTest {

    private static final String DOC_URI = "file:///workspace/MyClass.cls";
    private static final String SIMPLE_APEX = "public class MyClass { }";

    private ApexTextDocumentService service;
    private CapturingLanguageClient client;

    @BeforeEach
    void setUp() {
        service = new ApexTextDocumentService();
        client = new CapturingLanguageClient();
        service.setClient(client);
    }

    @Test
    void didOpenPublishesDiagnostics() {
        service.didOpen(openParams(DOC_URI, SIMPLE_APEX));

        assertThat(client.published).hasSize(1);
        assertThat(client.published.get(0).getUri()).isEqualTo(DOC_URI);
    }

    @Test
    void didOpenStoresDocumentForLaterDidSave() {
        service.didOpen(openParams(DOC_URI, SIMPLE_APEX));
        client.published.clear();

        service.didSave(saveParams(DOC_URI));

        assertThat(client.published).hasSize(1);
        assertThat(client.published.get(0).getUri()).isEqualTo(DOC_URI);
    }

    @Test
    void didChangeWithContentPublishesDiagnostics() {
        service.didOpen(openParams(DOC_URI, SIMPLE_APEX));
        client.published.clear();

        String updated = "public class MyClass { public void run() { } }";
        service.didChange(changeParams(DOC_URI, updated));

        assertThat(client.published).hasSize(1);
        assertThat(client.published.get(0).getUri()).isEqualTo(DOC_URI);
    }

    @Test
    void didChangeWithEmptyContentChangesFallsBackToStoredDocument() {
        service.didOpen(openParams(DOC_URI, SIMPLE_APEX));
        client.published.clear();

        service.didChange(changeParamsEmpty(DOC_URI));

        assertThat(client.published).hasSize(1);
        assertThat(client.published.get(0).getUri()).isEqualTo(DOC_URI);
    }

    @Test
    void didSaveReanalyzesStoredContent() {
        service.didOpen(openParams(DOC_URI, SIMPLE_APEX));
        client.published.clear();

        service.didSave(saveParams(DOC_URI));

        assertThat(client.published).hasSize(1);
    }

    @Test
    void didSaveWithNoStoredDocumentDoesNotPublish() {
        service.didSave(saveParams("file:///unknown/Doc.cls"));

        assertThat(client.published).isEmpty();
    }

    @Test
    void didClosePublishesEmptyDiagnostics() {
        service.didOpen(openParams(DOC_URI, SIMPLE_APEX));
        client.published.clear();

        service.didClose(closeParams(DOC_URI));

        assertThat(client.published).hasSize(1);
        assertThat(client.published.get(0).getUri()).isEqualTo(DOC_URI);
        assertThat(client.published.get(0).getDiagnostics()).isEmpty();
    }

    @Test
    void didCloseRemovesDocumentSoDidSaveDoesNothing() {
        service.didOpen(openParams(DOC_URI, SIMPLE_APEX));
        service.didClose(closeParams(DOC_URI));
        client.published.clear();

        service.didSave(saveParams(DOC_URI));

        assertThat(client.published).isEmpty();
    }

    @Test
    void didOpenWithNullClientDoesNotThrow() {
        ApexTextDocumentService noClientService = new ApexTextDocumentService();

        assertThatCode(() -> noClientService.didOpen(openParams(DOC_URI, SIMPLE_APEX)))
                .doesNotThrowAnyException();
    }

    @Test
    void didChangeWithNullClientDoesNotThrow() {
        ApexTextDocumentService noClientService = new ApexTextDocumentService();
        noClientService.didOpen(openParams(DOC_URI, SIMPLE_APEX));

        assertThatCode(() -> noClientService.didChange(changeParams(DOC_URI, "updated")))
                .doesNotThrowAnyException();
    }

    @Test
    void didSaveWithNullClientDoesNotThrow() {
        ApexTextDocumentService noClientService = new ApexTextDocumentService();
        noClientService.didOpen(openParams(DOC_URI, SIMPLE_APEX));

        assertThatCode(() -> noClientService.didSave(saveParams(DOC_URI)))
                .doesNotThrowAnyException();
    }

    @Test
    void didCloseWithNullClientDoesNotThrow() {
        ApexTextDocumentService noClientService = new ApexTextDocumentService();

        assertThatCode(() -> noClientService.didClose(closeParams(DOC_URI)))
                .doesNotThrowAnyException();
    }

    @Test
    void multipleDocumentsTrackedIndependently() {
        String uri1 = "file:///workspace/A.cls";
        String uri2 = "file:///workspace/B.cls";

        service.didOpen(openParams(uri1, "public class A { }"));
        service.didOpen(openParams(uri2, "public class B { }"));
        client.published.clear();

        service.didClose(closeParams(uri1));

        assertThat(client.published).hasSize(1);
        assertThat(client.published.get(0).getUri()).isEqualTo(uri1);
        assertThat(client.published.get(0).getDiagnostics()).isEmpty();

        client.published.clear();
        service.didSave(saveParams(uri2));
        assertThat(client.published).hasSize(1);
        assertThat(client.published.get(0).getUri()).isEqualTo(uri2);
    }

    @Test
    void diagnosticsListIsNeverNull() {
        service.didOpen(openParams(DOC_URI, SIMPLE_APEX));

        assertThat(client.published.get(0).getDiagnostics()).isNotNull();
    }

    // --- helpers ---

    private static DidOpenTextDocumentParams openParams(String uri, String text) {
        DidOpenTextDocumentParams params = new DidOpenTextDocumentParams();
        params.setTextDocument(new TextDocumentItem(uri, "apex", 1, text));
        return params;
    }

    private static DidChangeTextDocumentParams changeParams(String uri, String newText) {
        DidChangeTextDocumentParams params = new DidChangeTextDocumentParams();
        params.setTextDocument(new VersionedTextDocumentIdentifier(uri, 2));
        TextDocumentContentChangeEvent change = new TextDocumentContentChangeEvent(newText);
        params.setContentChanges(List.of(change));
        return params;
    }

    private static DidChangeTextDocumentParams changeParamsEmpty(String uri) {
        DidChangeTextDocumentParams params = new DidChangeTextDocumentParams();
        params.setTextDocument(new VersionedTextDocumentIdentifier(uri, 2));
        params.setContentChanges(Collections.emptyList());
        return params;
    }

    private static DidSaveTextDocumentParams saveParams(String uri) {
        DidSaveTextDocumentParams params = new DidSaveTextDocumentParams();
        params.setTextDocument(new TextDocumentIdentifier(uri));
        return params;
    }

    private static DidCloseTextDocumentParams closeParams(String uri) {
        DidCloseTextDocumentParams params = new DidCloseTextDocumentParams();
        params.setTextDocument(new TextDocumentIdentifier(uri));
        return params;
    }

    private static class CapturingLanguageClient implements LanguageClient {
        final List<PublishDiagnosticsParams> published = new ArrayList<>();

        @Override
        public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
            published.add(diagnostics);
        }

        @Override
        public void telemetryEvent(Object object) {}

        @Override
        public void showMessage(MessageParams messageParams) {}

        @Override
        public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void logMessage(MessageParams message) {}
    }
}
