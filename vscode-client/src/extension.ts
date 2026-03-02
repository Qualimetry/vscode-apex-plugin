/**
 * Apex Analyzer VS Code extension — LSP client and SonarQube rule import.
 */

import * as path from 'path';
import * as fs from 'fs';
import * as vscode from 'vscode';
import {
    LanguageClient,
    LanguageClientOptions,
    ServerOptions,
    State,
} from 'vscode-languageclient/node';
import { findJavaExecutable, getConfiguration, getJavaVersion } from './configuration';
import {
    fetchQualityProfiles,
    fetchActiveRules,
    resolveProfileKey,
    type SonarConfig,
} from './sonarImport';

const MIN_JAVA_VERSION = 17;

let client: LanguageClient | undefined;
let outputChannel: vscode.OutputChannel;

const SONAR_LAST_URL_KEY = 'apex.sonar.lastServerUrl';
const SONAR_LAST_PROFILE_KEY = 'apex.sonar.lastProfile';

export async function activate(context: vscode.ExtensionContext): Promise<void> {
    outputChannel = vscode.window.createOutputChannel('Apex Analyzer');
    context.subscriptions.push(outputChannel);
    outputChannel.appendLine('Apex Analyzer: activating...');

    context.subscriptions.push(
        vscode.commands.registerCommand('apex.importRulesFromSonarQube', () =>
            importRulesFromSonarQube(context)
        )
    );

    const config = getConfiguration();
    if (!config.enabled) {
        outputChannel.appendLine('Apex Analyzer is disabled via settings.');
        return;
    }

    outputChannel.appendLine('Looking for Java...');
    const javaPath = findJavaExecutable(config.javaHome);
    if (!javaPath) {
        const msg = 'Apex Analyzer: Java 17+ is required. Set "apexAnalyzer.java.home" or JAVA_HOME.';
        outputChannel.appendLine(msg);
        vscode.window.showErrorMessage(msg);
        return;
    }
    outputChannel.appendLine(`Java: ${javaPath}`);

    const version = getJavaVersion(javaPath);
    outputChannel.appendLine(`Java version: ${version ?? 'unknown'}`);
    if (version === undefined) {
        const msg = 'Apex Analyzer: Could not detect Java version. The language server requires Java 17+. Set "apexAnalyzer.java.home" to a JDK 17+ installation.';
        outputChannel.appendLine(msg);
        vscode.window.showErrorMessage(msg);
        return;
    }
    if (version < MIN_JAVA_VERSION) {
        const msg = `Apex Analyzer: Java ${MIN_JAVA_VERSION}+ is required (server is built with Java 17). Found Java ${version} at "${javaPath}". Set "apexAnalyzer.java.home" to a JDK 17+ installation.`;
        outputChannel.appendLine(msg);
        vscode.window.showErrorMessage(msg);
        return;
    }

    const serverJar = path.join(context.extensionPath, 'server', 'apex-lsp-server.jar');
    if (!fs.existsSync(serverJar)) {
        const msg = `Apex Analyzer: Server JAR not found at ${serverJar}.`;
        outputChannel.appendLine(msg);
        vscode.window.showErrorMessage(msg);
        return;
    }
    outputChannel.appendLine(`Server JAR: ${serverJar}`);

    const serverOptions: ServerOptions = {
        command: javaPath,
        args: ['-jar', serverJar],
        options: { env: process.env },
    };

    const clientOptions: LanguageClientOptions = {
        documentSelector: [
            { scheme: 'file', language: 'apex' },
            { scheme: 'file', pattern: '**/*.cls' },
            { scheme: 'file', pattern: '**/*.trigger' },
        ],
        synchronize: {
            configurationSection: 'apexAnalyzer',
        },
        outputChannel,
    };

    client = new LanguageClient(
        'apexAnalyzer',
        'Apex Analyzer',
        serverOptions,
        clientOptions
    );

    const statusBar = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left);
    statusBar.text = '$(checklist) Apex Analyzer';
    statusBar.tooltip = 'Apex Analyzer is active';
    statusBar.show();
    context.subscriptions.push(statusBar);

    outputChannel.appendLine('Starting Apex language server...');
    try {
        await client.start();
        outputChannel.appendLine('Language server started successfully.');
        context.subscriptions.push({
            dispose: () => {
                if (client?.state === State.Running) {
                    client.stop().catch(() => { /* ignore when disposing */ });
                }
            },
        });
    } catch (e) {
        const msg = 'Failed to start: ' + (e instanceof Error ? e.message : String(e));
        outputChannel.appendLine(msg);
        vscode.window.showErrorMessage('Apex Analyzer: failed to start language server.');
        return;
    }
}

async function importRulesFromSonarQube(context: vscode.ExtensionContext): Promise<void> {
    const lastUrl = context.globalState.get<string>(SONAR_LAST_URL_KEY) ?? '';
    const lastProfile = context.globalState.get<string>(SONAR_LAST_PROFILE_KEY) ?? '';

    const serverUrl = await vscode.window.showInputBox({
        title: 'SonarQube server URL',
        prompt: 'e.g. https://sonar.mycompany.com or https://myorg.sonarcloud.io',
        value: lastUrl,
        placeHolder: 'https://',
        ignoreFocusOut: true,
        validateInput: (v) => {
            const s = v?.trim() ?? '';
            if (!s) return 'URL is required';
            if (!/^https?:\/\//i.test(s) && !/^[a-zA-Z0-9.-]+/.test(s)) return 'Enter a valid URL';
            return undefined;
        },
    });
    if (serverUrl === undefined) return;
    const urlTrimmed = serverUrl.trim();
    await context.globalState.update(SONAR_LAST_URL_KEY, urlTrimmed);

    const profileNameOrKey = await vscode.window.showInputBox({
        title: 'Apex quality profile',
        prompt: 'Profile name or key (e.g. "Qualimetry Apex" or the profile key)',
        value: lastProfile,
        placeHolder: 'Qualimetry Apex',
        ignoreFocusOut: true,
    });
    if (profileNameOrKey === undefined) return;
    const profileTrimmed = profileNameOrKey.trim();
    await context.globalState.update(SONAR_LAST_PROFILE_KEY, profileTrimmed);

    const token = await vscode.window.showInputBox({
        title: 'SonarQube token (optional)',
        prompt: 'Paste token here. If this box closes when you switch apps, run the command again - URL and profile are already saved.',
        password: true,
        ignoreFocusOut: true,
    });
    if (token === undefined) return;

    const sonarConfig: SonarConfig = {
        serverUrl: serverUrl.trim(),
        profileNameOrKey: profileNameOrKey.trim(),
        token: (token ?? '').trim() || undefined,
    };

    let result: { count: number; targetLabel: string } | undefined;
    await vscode.window.withProgress(
        {
            location: vscode.ProgressLocation.Notification,
            title: 'Importing rules from SonarQube',
            cancellable: false,
        },
        async () => {
            try {
                const profiles = await fetchQualityProfiles(sonarConfig);
                if (profiles.length === 0) {
                    vscode.window.showErrorMessage(
                        'No Apex quality profiles found on this SonarQube server.'
                    );
                    return;
                }
                const profileKey = resolveProfileKey(profiles, sonarConfig.profileNameOrKey);
                if (!profileKey) {
                    vscode.window.showErrorMessage(
                        `No matching Apex profile for "${sonarConfig.profileNameOrKey}". ` +
                        `Available: ${profiles.map((p) => p.name).join(', ')}`
                    );
                    return;
                }
                const rules = await fetchActiveRules(sonarConfig, profileKey);
                if (Object.keys(rules).length === 0) {
                    vscode.window.showErrorMessage(
                        'No active rules found in the selected profile (or profile is not for Qualimetry Apex).'
                    );
                    return;
                }
                const hasWorkspace = (vscode.workspace.workspaceFolders?.length ?? 0) > 0;
                const configTarget = hasWorkspace
                    ? vscode.ConfigurationTarget.Workspace
                    : vscode.ConfigurationTarget.Global;
                const targetLabel = hasWorkspace ? 'workspace' : 'user';
                const cfg = vscode.workspace.getConfiguration('apexAnalyzer', null);
                await cfg.update('rules', rules, configTarget);
                await cfg.update('rulesReplaceDefaults', true, configTarget);
                await context.globalState.update(SONAR_LAST_URL_KEY, sonarConfig.serverUrl);
                await context.globalState.update(SONAR_LAST_PROFILE_KEY, sonarConfig.profileNameOrKey);
                const count = Object.keys(rules).length;
                result = { count, targetLabel };
            } catch (err) {
                const message = err instanceof Error ? err.message : String(err);
                outputChannel.appendLine(`Import from SonarQube failed: ${message}`);
                vscode.window.showErrorMessage(`Import from SonarQube failed: ${message}`);
            }
        }
    );
    if (result) {
        const location = result.targetLabel === 'workspace'
            ? 'workspace settings (.vscode/settings.json)'
            : 'user settings (global settings.json)';
        vscode.window.showInformationMessage(
            `Imported ${result.count} rule${result.count === 1 ? '' : 's'} from SonarQube into ${location}.`
        );
    }
}

export function deactivate(): Promise<void> {
    if (!client) return Promise.resolve();
    if (client.state !== State.Running) {
        client = undefined;
        return Promise.resolve();
    }
    const c = client;
    client = undefined;
    try {
        return c.stop().catch(() => { /* avoid uncaught rejection */ });
    } catch {
        return Promise.resolve();
    }
}
