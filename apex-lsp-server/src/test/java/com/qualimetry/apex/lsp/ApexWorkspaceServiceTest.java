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

import com.google.gson.JsonObject;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ApexWorkspaceServiceTest {

    private ApexWorkspaceService service;

    @BeforeEach
    void setUp() {
        service = new ApexWorkspaceService();
    }

    @Test
    void didChangeConfigurationDoesNotThrow() {
        DidChangeConfigurationParams params = new DidChangeConfigurationParams();
        params.setSettings(new JsonObject());

        assertThatCode(() -> service.didChangeConfiguration(params))
                .doesNotThrowAnyException();
    }

    @Test
    void didChangeConfigurationWithNullSettingsDoesNotThrow() {
        DidChangeConfigurationParams params = new DidChangeConfigurationParams();

        assertThatCode(() -> service.didChangeConfiguration(params))
                .doesNotThrowAnyException();
    }

    @Test
    void didChangeWatchedFilesDoesNotThrow() {
        FileEvent event = new FileEvent("file:///workspace/MyClass.cls", FileChangeType.Changed);
        DidChangeWatchedFilesParams params = new DidChangeWatchedFilesParams(List.of(event));

        assertThatCode(() -> service.didChangeWatchedFiles(params))
                .doesNotThrowAnyException();
    }

    @Test
    void didChangeWatchedFilesWithEmptyListDoesNotThrow() {
        DidChangeWatchedFilesParams params = new DidChangeWatchedFilesParams(Collections.emptyList());

        assertThatCode(() -> service.didChangeWatchedFiles(params))
                .doesNotThrowAnyException();
    }

    @Test
    void didChangeWatchedFilesWithMultipleEventsDoesNotThrow() {
        List<FileEvent> events = List.of(
                new FileEvent("file:///workspace/A.cls", FileChangeType.Created),
                new FileEvent("file:///workspace/B.cls", FileChangeType.Deleted),
                new FileEvent("file:///workspace/C.cls", FileChangeType.Changed));
        DidChangeWatchedFilesParams params = new DidChangeWatchedFilesParams(events);

        assertThatCode(() -> service.didChangeWatchedFiles(params))
                .doesNotThrowAnyException();
    }

    @Test
    void implementsWorkspaceService() {
        assertThat(service).isInstanceOf(org.eclipse.lsp4j.services.WorkspaceService.class);
    }
}
