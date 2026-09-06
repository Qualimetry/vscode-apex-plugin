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
const SONAR_TOKEN_SECRET_KEY = 'apex.sonar.token';

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

    autoSyncRulesOnStartup(context).catch((err) => {
        outputChannel.appendLine(`SonarQube rule auto-sync failed: ${err}`);
    });
}

interface SonarSyncOptions {
    serverUrl: string;
    profileNameOrKey: string;
    token?: string;
    interactive: boolean;
}

/**
 * Fetches the active rules from a SonarQube quality profile and writes them to
 * apexAnalyzer.rules + apexAnalyzer.rulesReplaceDefaults. Throws on failure.
 */
async function syncRulesFromSonarQube(
    context: vscode.ExtensionContext,
    opts: SonarSyncOptions
): Promise<{ count: number; targetLabel: string }> {
    const config: SonarConfig = {
        serverUrl: opts.serverUrl.trim(),
        profileNameOrKey: opts.profileNameOrKey.trim(),
        token: opts.token?.trim() || undefined,
    };
    const profiles = await fetchQualityProfiles(config);
    if (profiles.length === 0) {
        throw new Error('No Apex quality profiles found on this SonarQube server.');
    }
    const profileKey = resolveProfileKey(profiles, config.profileNameOrKey);
    if (!profileKey) {
        throw new Error(
            `No matching Apex profile for "${config.profileNameOrKey}". ` +
            `Available: ${profiles.map((p) => p.name).join(', ')}`
        );
    }
    const rules = await fetchActiveRules(config, profileKey);
    if (Object.keys(rules).length === 0) {
        throw new Error(
            'No active rules found in the selected profile (or profile is not for Qualimetry Apex).'
        );
    }
    const hasWorkspace = (vscode.workspace.workspaceFolders?.length ?? 0) > 0;
    const configTarget = hasWorkspace
        ? vscode.ConfigurationTarget.Workspace
        : vscode.ConfigurationTarget.Global;
    const targetLabel = hasWorkspace ? 'workspace' : 'user';
    const cfg = vscode.workspace.getConfiguration('apexAnalyzer', null);
    await cfg.update('rules', rules, configTarget);
    await cfg.update('rulesReplaceDefaults', true, configTarget);
    await context.globalState.update(SONAR_LAST_URL_KEY, config.serverUrl);
    await context.globalState.update(SONAR_LAST_PROFILE_KEY, config.profileNameOrKey);
    return { count: Object.keys(rules).length, targetLabel };
}

async function autoSyncRulesOnStartup(context: vscode.ExtensionContext): Promise<void> {
    const autoSync = vscode.workspace
        .getConfiguration('apexAnalyzer')
        .get<boolean>('sonar.autoSyncOnStartup', true);
    if (!autoSync) {
        return;
    }
    const serverUrl = context.globalState.get<string>(SONAR_LAST_URL_KEY);
    if (!serverUrl) {
        return;
    }
    const profileNameOrKey = context.globalState.get<string>(SONAR_LAST_PROFILE_KEY) ?? '';
    const token = await context.secrets.get(SONAR_TOKEN_SECRET_KEY);
    try {
        const result = await syncRulesFromSonarQube(context, {
            serverUrl,
            profileNameOrKey,
            token: token ?? undefined,
            interactive: false,
        });
        outputChannel.appendLine(
            `SonarQube rule auto-sync: ${result.count} rule(s) refreshed into ${result.targetLabel} settings.`
        );
    } catch (err) {
        const message = err instanceof Error ? err.message : String(err);
        outputChannel.appendLine(`SonarQube rule auto-sync failed: ${message}`);
        vscode.window.showWarningMessage(
            `Qualimetry Apex: SonarQube rule sync failed: ${message}`
        );
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

    const tokenTrimmed = (token ?? '').trim();

    let result: { count: number; targetLabel: string } | undefined;
    await vscode.window.withProgress(
        {
            location: vscode.ProgressLocation.Notification,
            title: 'Importing rules from SonarQube',
            cancellable: false,
        },
        async () => {
            try {
                result = await syncRulesFromSonarQube(context, {
                    serverUrl: urlTrimmed,
                    profileNameOrKey: profileTrimmed,
                    token: tokenTrimmed || undefined,
                    interactive: true,
                });
                if (tokenTrimmed) {
                    await context.secrets.store(SONAR_TOKEN_SECRET_KEY, tokenTrimmed);
                } else {
                    await context.secrets.delete(SONAR_TOKEN_SECRET_KEY);
                }
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
