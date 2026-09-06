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

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.services.WorkspaceService;

/**
 * Workspace service: reacts to configuration changes and triggers re-analysis.
 */
public class ApexWorkspaceService implements WorkspaceService {

    private final ApexLanguageServer server;

    public ApexWorkspaceService(ApexLanguageServer server) {
        this.server = server;
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params) {
        if (server != null) {
            server.fetchConfigAndReanalyze();
        }
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
    }
}
