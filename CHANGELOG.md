# Changelog

## [Unreleased]

- (None.)

## [1.9.9] - 2026-03-19

283 analysis rules covering convention, design, error handling, security, performance, testing, Salesforce best practices, and complexity metrics.

- All 283 rules enabled by default for comprehensive Apex coverage.
- Per-rule settings via `apexAnalyzer.rules` with enable/disable and severity override.
- **Import from SonarQube** — fetch active rules from a SonarQube quality profile into VS Code settings.
- Security rules with CWE and OWASP references for SOQL injection, XSS, SSRF, cryptographic misuse, and data exposure.
- Complexity metrics: cyclomatic complexity, cognitive complexity, NPath, NCSS, and coupling analysis.
- Governor limit enforcement: SOQL/DML in loops, @Future in loops, batch scope limits.
- Comprehensive HTML documentation with noncompliant and compliant Apex examples for every rule.

## [1.9.4] - 2026-03-02

283 analysis rules covering convention, design, error handling, security, performance, testing, Salesforce best practices, and complexity metrics.

- All 283 rules enabled by default for comprehensive Apex coverage.
- Security rules with CWE and OWASP references for SOQL injection, XSS, SSRF, cryptographic misuse, and data exposure.
- Complexity metrics: cyclomatic complexity, cognitive complexity, NPath, NCSS, and coupling analysis.
- Governor limit enforcement: SOQL/DML in loops, @Future in loops, batch scope limits.
- Comprehensive HTML documentation with noncompliant and compliant Apex examples for every rule.

## [1.0.0] - 2026-03-01

First public release.

- Real-time diagnostics for `.cls` / `.trigger` files as you type.
- **Apex: Import rules from SonarQube** – pull quality profile and rule severities from a SonarQube server into settings.
- Configurable per-rule severity and enable/disable via `apexAnalyzer.rules`.
- Requires Java 17+ (uses `JAVA_HOME` or `apexAnalyzer.java.home`).
