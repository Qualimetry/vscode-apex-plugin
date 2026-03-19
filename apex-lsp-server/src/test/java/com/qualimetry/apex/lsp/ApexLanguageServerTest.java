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

import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

class ApexLanguageServerTest {

    @Test
    void initializeReturnsFullSync() throws ExecutionException, InterruptedException {
        ApexLanguageServer server = new ApexLanguageServer();

        InitializeResult result = server.initialize(new InitializeParams()).get();

        assertThat(result.getCapabilities().getTextDocumentSync().getLeft())
                .isEqualTo(TextDocumentSyncKind.Full);
    }

    @Test
    void initializeReturnsServerInfo() throws ExecutionException, InterruptedException {
        ApexLanguageServer server = new ApexLanguageServer();

        InitializeResult result = server.initialize(new InitializeParams()).get();

        assertThat(result.getServerInfo().getName()).isEqualTo("Apex Analyzer");
    }

    @Test
    void shutdownReturnsNonNullFuture() throws ExecutionException, InterruptedException {
        ApexLanguageServer server = new ApexLanguageServer();

        Object result = server.shutdown().get();

        assertThat(result).isNull();
    }

    @Test
    void connectSetsClient() {
        ApexLanguageServer server = new ApexLanguageServer();
        LanguageClient client = new StubLanguageClient();

        server.connect(client);

        assertThat(server.getClient()).isSameAs(client);
    }

    @Test
    void getTextDocumentServiceReturnsNonNull() {
        ApexLanguageServer server = new ApexLanguageServer();
        assertThat(server.getTextDocumentService()).isNotNull();
    }

    @Test
    void getWorkspaceServiceReturnsNonNull() {
        ApexLanguageServer server = new ApexLanguageServer();
        assertThat(server.getWorkspaceService()).isNotNull();
    }

    private static class StubLanguageClient implements LanguageClient {
        @Override
        public void telemetryEvent(Object object) {}

        @Override
        public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {}

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
