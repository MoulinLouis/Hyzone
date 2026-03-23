# Migration Plan — Move Project to Native WSL2 Filesystem

## Why

The project lives on Windows filesystem (`/mnt/c/`) accessed from WSL2 via the 9P bridge. Every file operation (git, grep, build, Claude Code tools) pays a 2-10x I/O penalty. Moving source code to native WSL2 ext4 eliminates this.

## Architecture

**Hybrid setup:** source code on WSL2, Hytale server runtime on Windows.

- **Source code** (`~/dev/Hytale/hyvexa_plugin/`) → native WSL2 ext4 — fast builds, fast git, fast Claude Code
- **Server runtime** (`C:\Users\User\Documents\dev\Hytale\hyvexa_server\`) → standalone directory on Windows — Hytale server reads from here natively, decoupled from source code
- **`stagePlugins`** → builds on WSL2, copies 7 JARs to Windows `hyvexa_server/mods/` via `/mnt/c/` (< 1 second)
- **IntelliJ run config** → uses Windows JDK to launch server with Windows `hyvexa_server/` as working dir

**What changes in daily workflow: nothing.** Code → Ctrl+F9 → Run → localhost. Same one-click. Just faster.

## Current State

| Component | Value |
|-----------|-------|
| Project path | `/mnt/c/Users/User/Documents/dev/Hytale/hyvexa_plugin` |
| Git remote | `git@github.com:MoulinLouis/Hyzone.git` |
| Local branches | `main`, `feat/singleton-decoupling`, `feat/singleton-phase2-interfaces` |
| Stash | 1 entry (`WIP on main: 41a3043d build instruction`) |
| Java (WSL2) | OpenJDK 25.0.2 (already installed) |
| Gradle | Wrapper 9.2.0 |
| `hytaleHome` | `${user.home}/AppData/Roaming/Hytale` (resolves on Windows only) |
| `HytaleServer.jar` | `/mnt/c/Users/User/AppData/Roaming/Hytale/install/release/package/game/latest/Server/HytaleServer.jar` |
| IntelliJ run config | `HytaleServer` — Application type, Windows JDK 25, `$PROJECT_DIR$/run`, Before launch: stagePlugins + Build |
| `deploy.sh` | Production SFTP deployment script (uses `cmd.exe /c "gradlew.bat collectPlugins"`) |
| `.gitattributes` | Does not exist |
| `core.autocrlf` | Not set |
| Repo size | 2.7 GB |
| WSL2 free space | 915 GB |

## Step-by-Step

### Step 1 — Push everything to remote (2 min)

Ensure nothing is lost. Run from the current Windows-mounted project:

```bash
cd /mnt/c/Users/User/Documents/dev/Hytale/hyvexa_plugin

# Push all local branches
git push origin main
git push origin feat/singleton-decoupling
git push origin feat/singleton-phase2-interfaces

# Verify stash exists (we'll recreate it on WSL2 if needed)
git stash list
# stash@{0}: WIP on main: 41a3043d build instruction
# Note: stashes don't transfer via clone. If this stash matters,
# apply it to a temp branch and push:
#   git stash branch temp/stash-backup
#   git push origin temp/stash-backup
```

### Step 2 — Move server runtime to standalone directory (2 min)

The server runtime (`run/`) must live at a stable Windows path, independent of the source code directory. This prevents path breakage when we archive the old source later.

```bash
cd /mnt/c/Users/User/Documents/dev/Hytale

# Move run/ to its own directory
mv hyvexa_plugin/run hyvexa_server
```

All subsequent paths in this plan use `hyvexa_server/` for the server runtime.

**Note:** After this step, the existing IntelliJ run config (which points to `$PROJECT_DIR$/run`) will break. Don't try to run the server from the old project — you'll set up the new run config in step 14.

Verify:
```bash
ls /mnt/c/Users/User/Documents/dev/Hytale/hyvexa_server/mods/
# Should list the existing plugin JARs
```

### Step 3 — Set up line endings (1 min)

```bash
# Global git config for WSL2
git config --global core.autocrlf input
```

### Step 4 — Clone to native WSL2 (2 min)

```bash
mkdir -p ~/dev/Hytale
cd ~/dev/Hytale
git clone git@github.com:MoulinLouis/Hyzone.git hyvexa_plugin
cd hyvexa_plugin

# Fetch all branches
git fetch --all
git branch -a
```

### Step 5 — Add .gitattributes (2 min)

Prevents line ending issues between WSL2 (LF) and Windows (CRLF):

```bash
cd ~/dev/Hytale/hyvexa_plugin

cat > .gitattributes << 'EOF'
* text=auto eol=lf
*.bat text eol=crlf
*.cmd text eol=crlf
*.png binary
*.jpg binary
*.gif binary
*.ogg binary
*.wav binary
*.mp3 binary
*.jar binary
*.enc binary
EOF

git add .gitattributes
git add --renormalize .
# Check if renormalize changed anything
git diff --cached --stat
# If there are changes:
git commit -m "chore: add .gitattributes for consistent LF line endings"
```

### Step 6 — Make Gradle wrapper executable (1 min)

```bash
chmod +x gradlew
```

### Step 7 — Make `hytaleHome` overridable in build.gradle (2 min)

The current `build.gradle` hardcodes `hytaleHome` via `ext {}`, which unconditionally overwrites any value from `gradle.properties`. Change it to respect an external override:

Edit `build.gradle` — change the `ext {}` block:

**Before:**
```groovy
ext {
    hytaleHome = "${System.getProperty("user.home")}/AppData/Roaming/Hytale"
```

**After:**
```groovy
ext {
    hytaleHome = project.findProperty('hytaleHome') ?: "${System.getProperty("user.home")}/AppData/Roaming/Hytale"
```

This way:
- If `hytaleHome` is set in `~/.gradle/gradle.properties` (or command line `-P`), that value wins
- Otherwise, the existing Windows default is used as fallback
- The rest of the `ext {}` block (resolving `HytaleServer.jar`) stays unchanged

```bash
git add build.gradle
git commit -m "chore: allow hytaleHome override via gradle property"
```

### Step 8 — Make `stagePlugins` output dir configurable (2 min)

`stagePlugins` needs to put JARs where the Windows Hytale server reads them — the standalone `hyvexa_server/mods/` directory. Edit `build.gradle`:

**Before:**
```groovy
tasks.register('stagePlugins', Copy) {
    dependsOn ...
    def modsDir = rootProject.file("run/mods")
    ...
    into(modsDir)
}
```

**After:**
```groovy
tasks.register('stagePlugins', Copy) {
    dependsOn ':hyvexa-parkour:jar', ':hyvexa-hub:jar', ':hyvexa-parkour-ascend:jar', ':hyvexa-purge:jar', ':hyvexa-runorfall:jar', ':hyvexa-wardrobe:jar', ':hyvexa-votifier:jar'
    def modsDir = project.hasProperty('stageModsDir')
            ? file(project.stageModsDir)
            : rootProject.file("run/mods")
    if (!modsDir.exists()) {
        modsDir.mkdirs()
    }
    from(project(':hyvexa-parkour').tasks.named('jar').get().archiveFile)
    from(project(':hyvexa-hub').tasks.named('jar').get().archiveFile)
    from(project(':hyvexa-parkour-ascend').tasks.named('jar').get().archiveFile)
    from(project(':hyvexa-purge').tasks.named('jar').get().archiveFile)
    from(project(':hyvexa-runorfall').tasks.named('jar').get().archiveFile)
    from(project(':hyvexa-wardrobe').tasks.named('jar').get().archiveFile)
    from(project(':hyvexa-votifier').tasks.named('jar').get().archiveFile)
    into(modsDir)
}
```

The default `run/mods` fallback keeps the task working for anyone without the override (e.g., CI, other developers).

```bash
git add build.gradle
git commit -m "chore: allow stagePlugins output dir override via gradle property"
```

### Step 9 — Set machine-local Gradle properties (1 min)

Machine-specific paths go in the **user-level** Gradle properties file, which is never committed to the repo:

```bash
# Create or append to ~/.gradle/gradle.properties
mkdir -p ~/.gradle
cat >> ~/.gradle/gradle.properties << 'EOF'

# Hyvexa WSL2 overrides
hytaleHome=/mnt/c/Users/User/AppData/Roaming/Hytale
stageModsDir=/mnt/c/Users/User/Documents/dev/Hytale/hyvexa_server/mods
EOF
```

**Why user-level, not project-level?** These paths contain `/mnt/c/Users/User/...` which is specific to this machine. Committing them would break builds for any other checkout path, developer, or CI. The project's `gradle.properties` stays portable with only project-level settings.

### Step 10 — Update `deploy.sh` (1 min)

The production deploy script uses `cmd.exe /c "gradlew.bat collectPlugins"`. Change to native:

Edit `deploy.sh` line 59:
```bash
# Old:
cmd.exe /c "gradlew.bat collectPlugins" 2>&1 | tail -5
# New:
./gradlew collectPlugins 2>&1 | tail -5
```

```bash
git add deploy.sh
git commit -m "chore: use native gradlew in deploy.sh"
```

### Step 11 — Test the build (3 min)

```bash
cd ~/dev/Hytale/hyvexa_plugin

# Full build
./gradlew build

# Verify stagePlugins works and JARs land on Windows
./gradlew stagePlugins
ls -la /mnt/c/Users/User/Documents/dev/Hytale/hyvexa_server/mods/Hyvexa*.jar
```

### Step 12 — Update CLAUDE.md (2 min)

Edit the Quick Commands section in `CLAUDE.md`:

```bash
# Old:
# cmd.exe /c "gradlew.bat build" (only when explicitly requested by the user)
# cmd.exe /c "gradlew.bat test"  (use cmd.exe — WSL2 has I/O issues with gradlew)

# New:
# ./gradlew build  (only when explicitly requested by the user)
# ./gradlew test
```

```bash
git add CLAUDE.md
git commit -m "docs: update CLAUDE.md build commands for native WSL2"
```

### Step 13 — Push all changes (1 min)

```bash
git push origin main
```

### Step 14 — Configure IntelliJ (10 min)

This is the one-time IntelliJ setup. All done from IntelliJ on Windows.

#### 14a — Open the WSL2 project

1. File → Open
2. In the path bar, type: `\\wsl$\Ubuntu\home\playfade\dev\Hytale\hyvexa_plugin`
3. Click OK → Open as Project
4. Wait for IntelliJ to index (first time takes a few minutes)

#### 14b — Configure WSL2 JDK for Gradle

1. File → Settings → Build, Execution, Deployment → Build Tools → Gradle
2. Gradle JVM: click dropdown → Add JDK → "Download JDK" or "Add JDK from WSL"
   - If "Add JDK from WSL": select Ubuntu, it should find `/usr/lib/jvm/java-25-openjdk-amd64`
   - If manual: set path to `\\wsl$\Ubuntu\usr\lib\jvm\java-25-openjdk-amd64`
3. Distribution: "Wrapper" (default)
4. Click Apply

#### 14c — Create the HytaleServer run configuration

The auto-generated run config from `idea-ext` won't work because it resolves paths in WSL2 context. Create a manual one:

1. Run → Edit Configurations → + → Application
2. Name: `HytaleServer`
3. JDK: select your **Windows** JDK 25 (NOT the WSL2 one)
4. Main class: `com.hypixel.hytale.Main`
5. Module classpath: `hyvexa_plugin.hyvexa-launch.main`
6. Program arguments:
   ```
   --allow-op --assets=C:\Users\User\AppData\Roaming\Hytale\install\release\package\game\latest\Assets.zip
   ```
7. Working directory:
   ```
   C:\Users\User\Documents\dev\Hytale\hyvexa_server
   ```
8. Before launch (in order):
   - Run Gradle task: `stagePlugins` (this uses the WSL2 Gradle JDK automatically)
   - Build module: `hyvexa_plugin.hyvexa-launch.main`
9. Click Apply → OK

#### 14d — Test the run config

1. Select `HytaleServer` in the run config dropdown
2. Click Run (green arrow)
3. Expected: Gradle compiles on WSL2 → copies JARs to Windows `hyvexa_server/mods/` → server launches → you connect via Hytale client

### Step 15 — Migrate Claude Code memory (2 min)

```bash
# Launch Claude Code once from the new project to create the project key
cd ~/dev/Hytale/hyvexa_plugin
# (start claude code, then exit)

# Find the new project key
ls /home/playfade/.claude/projects/ | grep hyvexa

# Copy memory from old location
OLD_KEY="-mnt-c-Users-User-Documents-dev-Hytale-hyvexa-plugin"
NEW_KEY="<the key you found above>"
cp /home/playfade/.claude/projects/$OLD_KEY/memory/* \
   /home/playfade/.claude/projects/$NEW_KEY/memory/
```

### Step 16 — Archive the Windows source code (1 min)

The server runtime is already at `hyvexa_server/` (moved in step 2), so the old source directory can be safely archived without breaking any paths:

```bash
# Option A: rename (safe, reversible)
cd /mnt/c/Users/User/Documents/dev/Hytale
mv hyvexa_plugin hyvexa_plugin_OLD

# Option B: delete (after you're confident)
# Do this later, not now
```

**Wait a few days before deleting.** Make sure the IntelliJ + WSL2 workflow is stable first.

### Step 17 — Verify everything (5 min)

```bash
cd ~/dev/Hytale/hyvexa_plugin

# Git
git status && git log --oneline -3

# Build speed (should be noticeably faster)
time ./gradlew build

# Stage JARs to Windows
./gradlew stagePlugins
ls /mnt/c/Users/User/Documents/dev/Hytale/hyvexa_server/mods/Hyvexa*.jar

# Production deploy
./deploy.sh  # (cancel at confirmation prompt)

# Worktrees (for implement-plan skill)
git worktree add ../hyvexa_plugin--test -b test/worktree main
ls ../hyvexa_plugin--test/CLAUDE.md && echo "OK"
git worktree remove ../hyvexa_plugin--test
git branch -d test/worktree

# Claude Code (launch from new path and test a grep)
```

Then in IntelliJ: Run `HytaleServer` → connect via Hytale client → play.

## Summary of changes to files in the repo

| File | Change |
|------|--------|
| `.gitattributes` | **New** — enforce LF line endings |
| `build.gradle` | `hytaleHome` uses `findProperty()` with fallback; `stagePlugins` reads `stageModsDir` property |
| `deploy.sh` | `cmd.exe /c "gradlew.bat ..."` → `./gradlew ...` |
| `CLAUDE.md` | Build commands updated for native WSL2 |

| File | Change |
|------|--------|
| `~/.gradle/gradle.properties` | **Local only** — `hytaleHome` and `stageModsDir` overrides (never committed) |

**Note:** The project's `gradle.properties` is **not modified**. Machine-specific paths live only in the user-level `~/.gradle/gradle.properties`.

## Rollback

If anything goes wrong:
1. Move server runtime back: `mv /mnt/c/.../hyvexa_server /mnt/c/.../hyvexa_plugin_OLD/run`
2. Rename: `mv /mnt/c/.../hyvexa_plugin_OLD /mnt/c/.../hyvexa_plugin`
3. Remove WSL2 Gradle overrides: edit `~/.gradle/gradle.properties` and delete the `hytaleHome` and `stageModsDir` lines
4. Reopen that project in IntelliJ
5. Everything works exactly as before
6. The WSL2 clone can be deleted: `rm -rf ~/dev/Hytale/hyvexa_plugin`

No data can be lost — the remote has everything pushed.

## Expected performance gains

| Operation | Before (cross-fs) | After (native WSL2) |
|-----------|-------------------|---------------------|
| `./gradlew build` (incremental) | ~15-30s | ~5-10s |
| `git status` | ~500ms | ~50ms |
| Claude Code grep | ~200-500ms | ~30-80ms |
| Claude Code file read | ~10-30ms | ~1-3ms |
| `stagePlugins` JAR copy | instant (local) | ~1s (7 JARs via /mnt/c/) |
