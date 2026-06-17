import * as vscode from 'vscode';
import * as path from 'path';
import * as fs from 'fs';
import { execSync } from 'child_process';

export interface ApexAnalyzerConfig {
    enabled: boolean;
    javaHome: string;
    rules: Record<string, unknown>;
}

export function getConfiguration(): ApexAnalyzerConfig {
    const config = vscode.workspace.getConfiguration('apexAnalyzer');
    return {
        enabled: config.get<boolean>('enabled', true),
        javaHome: config.get<string>('java.home', ''),
        rules: config.get<Record<string, unknown>>('rules', {}),
    };
}

export function findJavaExecutable(configuredPath?: string): string | undefined {
    if (configuredPath) {
        const resolved = resolveJavaPath(configuredPath);
        if (resolved) {
            return resolved;
        }
    }

    const javaHome = process.env['JAVA_HOME'];
    if (javaHome) {
        const resolved = resolveJavaPath(javaHome);
        if (resolved) {
            return resolved;
        }
    }

    try {
        const cmd = process.platform === 'win32' ? 'where java' : 'which java';
        const result = execSync(cmd, { encoding: 'utf8', timeout: 5000 }).trim();
        const firstLine = result.split(/\r?\n/)[0];
        if (firstLine && fs.existsSync(firstLine)) {
            return firstLine;
        }
    } catch {
        // java not on PATH
    }

    return undefined;
}

export function getJavaVersion(javaExe: string): number | undefined {
    try {
        const output = execSync(`"${javaExe}" -version`, {
            encoding: 'utf8',
            timeout: 10000,
            stdio: ['pipe', 'pipe', 'pipe'],
        });
        const combined = output || '';
        return parseJavaVersion(combined);
    } catch (err: unknown) {
        if (err && typeof err === 'object' && 'stderr' in err) {
            const stderr = (err as { stderr: string }).stderr;
            if (stderr) {
                return parseJavaVersion(stderr);
            }
        }
        return undefined;
    }
}

function parseJavaVersion(output: string): number | undefined {
    const match = output.match(/version\s+"(\d+)(?:\.(\d+))?/);
    if (!match) {
        return undefined;
    }
    const major = parseInt(match[1], 10);
    if (major === 1 && match[2]) {
        return parseInt(match[2], 10);
    }
    return major;
}

function resolveJavaPath(javaHomeOrPath: string): string | undefined {
    if (fs.existsSync(javaHomeOrPath) && fs.statSync(javaHomeOrPath).isFile()) {
        return javaHomeOrPath;
    }

    const ext = process.platform === 'win32' ? '.exe' : '';
    const javaExe = path.join(javaHomeOrPath, 'bin', `java${ext}`);
    if (fs.existsSync(javaExe)) {
        return javaExe;
    }

    return undefined;
}
