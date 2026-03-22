# CI Pipeline Plan — GitHub Actions

## Context

- Gradle 9.2.0, Java 25, 9 modules (8 produce artifacts)
- 16 JUnit 5 tests in 3 modules (core, parkour, parkour-ascend)
- **Blocker**: every module depends on `HytaleServer.jar` from local filesystem
- No existing CI config

## The HytaleServer.jar Problem

All modules reference `$hytaleHome/install/$patchline/package/game/latest/Server/HytaleServer.jar` as a `compileOnly`/`runtimeOnly` dependency. This JAR isn't in any Maven repository — it's from a local Hytale install.

**Options (pick one):**

| Option | Pros | Cons |
|--------|------|------|
| **A. Upload to private GitHub Packages** | Clean, proper dependency management | Redistribution license risk, setup effort |
| **B. Commit to repo as `libs/HytaleServer.jar`** | Simplest, works immediately | Bloats repo (~30-50MB?), version tracking manual |
| **C. GitHub Actions cache + manual upload** | Doesn't bloat repo | Fragile, cache expires after 7 days unused |
| **D. Self-hosted runner with Hytale installed** | Matches dev environment exactly | Maintenance overhead, single point of failure |

**Recommendation: Option B** — commit the JAR to a `libs/` directory and update `build.gradle` to resolve from there when `hytaleHome` path doesn't exist (CI fallback). Simple, reliable, zero maintenance. The Hytale SDK is a single JAR, not a massive dependency tree. Add to `.gitattributes` with Git LFS if size is a concern later.

### Gradle Change Needed

Add a CI-friendly fallback in root `build.gradle`:

```groovy
ext {
    def localHytale = "${System.getProperty("user.home")}/AppData/Roaming/Hytale"
    hytaleHome = file(localHytale).exists() ? localHytale : null
    hytaleServerJar = hytaleHome
        ? files("$hytaleHome/install/$patchline/package/game/latest/Server/HytaleServer.jar")
        : files("$rootDir/libs/HytaleServer.jar")
}
```

Then each submodule replaces the hardcoded path with `hytaleServerJar`.

---

## Pipeline Design

### Single workflow: `.github/workflows/ci.yml`

**Triggers:**
- `push` to `main`
- `pull_request` to `main`

**Jobs:**

### Job 1: `build-and-test`

```
Steps:
1. actions/checkout@v4
2. actions/setup-java@v4 (temurin, java 25)
3. actions/cache (gradle wrapper + dependencies)
4. ./gradlew build --no-daemon
   - Compiles all 9 modules
   - Runs all 16 tests
   - Produces JARs
5. Upload test reports as artifact (on failure)
6. Upload plugin JARs as artifact (on success)
```

That's it. One job, ~2-3 minutes.

---

## What's NOT Included (and Why)

| Omitted | Reason |
|---------|--------|
| Lint/checkstyle | No existing style config; add later if desired |
| Code coverage (JaCoCo) | Only 16 tests, coverage metrics wouldn't be meaningful yet |
| Deploy/release automation | Not needed — owner handles builds manually |
| Matrix builds (multiple Java versions) | Only Java 25 is targeted |
| Discord bot CI | Separate Node.js project, separate concern |
| Caching Gradle build outputs | `actions/cache` on `~/.gradle` is sufficient for deps |
| Branch protection rules | Organizational decision, not a CI config |

---

## Implementation Steps

1. **Determine HytaleServer.jar approach** — confirm option B or pick alternative
2. **Update `build.gradle`** — add fallback JAR resolution for CI
3. **Copy HytaleServer.jar to `libs/`** (if option B)
4. **Create `.github/workflows/ci.yml`**
5. **Push and verify** — check GitHub Actions tab
6. **Add status badge to README** (optional)

---

## Estimated Workflow File

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 25

      - uses: gradle/actions/setup-gradle@v4

      - run: ./gradlew build --no-daemon

      - uses: actions/upload-artifact@v4
        if: failure()
        with:
          name: test-reports
          path: '**/build/reports/tests/'

      - uses: actions/upload-artifact@v4
        if: success()
        with:
          name: plugin-jars
          path: '**/build/libs/*.jar'
          retention-days: 14
```

`gradle/actions/setup-gradle@v4` handles Gradle caching automatically (wrapper + dependencies + build cache). No manual `actions/cache` config needed.
