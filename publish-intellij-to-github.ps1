# publish-intellij-to-github.ps1
# Canonical repo: Azure DevOps (origin). Main/master lives there with full history.
# GitHub is snapshot only: this script is the only way we deploy to GitHub. It
# creates a single-commit snapshot and force-pushes to github-intellij main.
#
# If the GitHub repo was deleted: create an empty repo at
#   https://github.com/Qualimetry/intellij-apex-plugin
# (no README, no .gitignore). Then run this script again.
#
# Before a release: bump version in root pom.xml, update CHANGELOG-intellij.md,
# commit, then run with -Message "release: X.Y.Z".
#
# Usage:
#   .\publish-intellij-to-github.ps1
#   .\publish-intellij-to-github.ps1 -Message "release: 1.0.0"

param(
    [string]$Message = "Subtree copy to GitHub"
)

$ErrorActionPreference = "Continue"

$GitHubRemoteName = "github-intellij"
$GitHubRepoUrl    = "https://github.com/Qualimetry/intellij-apex-plugin.git"
$TargetBranch     = "main"
$TempBranch       = "github-publish-intellij"
$ExcludeFromGitHub = @(
    "apex-plugin",
    "apex-lsp-server",
    "vscode-client",
    "its",
    "scripts",
    "docs",
    ".cursor",
    ".vs",
    ".vscode",
    "README-sonarqube.md",
    "README-vscode.md",
    "publish-sonarqube-to-github.ps1",
    "publish-vscode-to-github.ps1",
    "publish-intellij-to-github.ps1",
    "package.json",
    "package-lock.json",
    "release-notes.md",
    "CHANGELOG.md",
    "CHANGELOG-sonarqube.md",
    "CHANGELOG-vscode.md",
    "AGENTS.md"
)

$RepoRoot = git rev-parse --show-toplevel 2>$null
if (-not $RepoRoot) {
    Write-Error "Not in a git repository. Run this script from the repo root or a subdirectory."
    exit 1
}
Set-Location $RepoRoot
Write-Host "Repo root: $RepoRoot" -ForegroundColor Cyan

$status = git status --porcelain
if ($status) {
    Write-Warning "You have uncommitted changes. Commit or stash them before publishing."
    git status --short
    exit 1
}

$existingRemote = $null
try { $existingRemote = git remote get-url $GitHubRemoteName 2>$null } catch {}
if (-not $existingRemote) {
    Write-Host "Adding remote '$GitHubRemoteName' -> $GitHubRepoUrl" -ForegroundColor Yellow
    git remote add $GitHubRemoteName $GitHubRepoUrl
} else {
    Write-Host "Remote '$GitHubRemoteName' already configured" -ForegroundColor Green
}

Write-Host "Creating single-commit snapshot for IntelliJ plugin ..." -ForegroundColor Cyan
git checkout --orphan $TempBranch 2>$null

git add -A
foreach ($path in $ExcludeFromGitHub) {
    $exists = git ls-files --cached $path 2>$null
    if ($exists) {
        git rm -r --cached $path 2>$null
    }
}

# Remove the parent pom.xml (IntelliJ plugin uses Gradle, not Maven parent)
$pomInIndex = git ls-files --cached "pom.xml" 2>$null
if ($pomInIndex) { git rm --cached "pom.xml" 2>$null }

# Use the IntelliJ plugin README as the repo README
$readmeExists = Test-Path "intellij-plugin/README.md"
if ($readmeExists) {
    Copy-Item "intellij-plugin/README.md" "README.md" -Force
    git add README.md
}

# Use product-specific changelog as CHANGELOG.md for GitHub
if (Test-Path "CHANGELOG-intellij.md") {
    Copy-Item "CHANGELOG-intellij.md" "CHANGELOG.md" -Force
    git add "CHANGELOG.md"
    git rm --cached "CHANGELOG-intellij.md" 2>$null
}

# Remove CI workflows that belong to other products
$ciToRemove = @(".github/workflows/ci.yml", ".github/workflows/ci-sonarqube.yml", ".github/workflows/ci-vscode.yml")
foreach ($f in $ciToRemove) {
    $ciInIndex = git ls-files --cached $f 2>$null
    if ($ciInIndex) { git rm --cached $f 2>$null }
}
$productCi = ".github/workflows/ci-intellij.yml"
$productCiExists = git ls-files --cached $productCi 2>$null
if ($productCiExists) {
    Copy-Item $productCi ".github/workflows/ci.yml" -Force
    git add ".github/workflows/ci.yml"
    git rm --cached $productCi 2>$null
}

git commit -m $Message
if ($LASTEXITCODE -ne 0) {
    Write-Error "Commit failed. Check for empty index (e.g. everything excluded?)."
    git checkout -f main
    try { git branch -D $TempBranch 2>$null } catch {}
    exit 1
}

$repoSlug = ($GitHubRepoUrl -replace '\.git$','') -replace '^https://github\.com/',''
$hadProtection = $false
try {
    $null = gh api "repos/$repoSlug/branches/$TargetBranch/protection" 2>$null
    if ($LASTEXITCODE -eq 0) {
        $hadProtection = $true
        gh api -X DELETE "repos/$repoSlug/branches/$TargetBranch/protection" 2>$null
        Write-Host "Temporarily removed branch protection on $TargetBranch" -ForegroundColor Yellow
    }
} catch {}

Write-Host "Force-pushing to $GitHubRemoteName/$TargetBranch ..." -ForegroundColor Cyan
$pushOut = git push $GitHubRemoteName "${TempBranch}:${TargetBranch}" --force 2>&1
$pushOut | ForEach-Object { Write-Host $_ }
$pushResult = $LASTEXITCODE

if ($hadProtection) {
    $body = '{"required_status_checks":null,"enforce_admins":null,"required_pull_request_reviews":null,"restrictions":null,"allow_force_pushes":false}'
    $body | gh api -X PUT "repos/$repoSlug/branches/$TargetBranch/protection" --input - 2>$null
    Write-Host "Restored branch protection on $TargetBranch" -ForegroundColor Yellow
}

git checkout -f main 2>$null
try { git branch -D $TempBranch 2>$null } catch {}

if ($pushResult -ne 0) {
    Write-Error "Push failed. Check remote URL and credentials."
    exit 1
}

Write-Host ""
Write-Host "Published IntelliJ plugin to GitHub!" -ForegroundColor Green
Write-Host "  Repository: https://github.com/$repoSlug" -ForegroundColor Green
Write-Host "  Branch:     $TargetBranch" -ForegroundColor Green
