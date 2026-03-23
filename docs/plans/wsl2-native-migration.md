# Migration Plan — Move Project to Native WSL2 Filesystem

## Why

The project lives on Windows filesystem (`/mnt/c/`) accessed from WSL2 via the 9P bridge. Every file operation (git, grep, build, Claude Code tools) pays a 2-10x I/O penalty. Moving source code to native WSL2 ext4 eliminates this.

## Architecture

**Hybrid setup:** source code on WSL2, Hytale server runtime on Windows.

- **Source code** (`~/dev/Hytale/hyvexa_plugin/`) → native WSL2 ext4 — fast builds, fast git, fast Claude Code
- **Server runtime** (`C:\Users\User\Documents\dev\Hytale\hyvexa_plugin\run\`) → stays on Windows — Hytale server reads from here natively
- **`stagePlugins`** → builds on WSL2, copies 7 JARs to Windows `run/mods/` via `/mnt/c/` (< 1 second)
- **IntelliJ run config** → uses Windows JDK to launch server with Windows `run/` as working dir

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

### Step 2 — Set up line endings (1 min)

```bash
# Global git config for WSL2
git config --global core.autocrlf input
```

### Step 3 — Clone to native WSL2 (2 min)

```bash
mkdir -p ~/dev/Hytale
cd ~/dev/Hytale
git clone git@github.com:MoulinLouis/Hyzone.git hyvexa_plugin
cd hyvexa_plugin

# Fetch all branches
git fetch --all
git branch -a
```

### Step 4 — Add .gitattributes (2 min)

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

### Step 5 — Make Gradle wrapper executable (1 min)

```bash
chmod +x gradlew
```

### Step 6 — Override `hytaleHome` for WSL2 (1 min)

The default `hytaleHome` uses `${user.home}/AppData/Roaming/Hytale` which doesn't resolve on Linux. Override it in `gradle.properties`:

```bash
cd ~/dev/Hytale/hyvexa_plugin

# Add the WSL2-compatible path (this is a local override, commit it)
```

Edit `gradle.properties` — add this line:

```properties
hytaleHome=/mnt/c/Users/User/AppData/Roaming/Hytale
```

This lets Gradle find `HytaleServer.jar` for compilation. The `/mnt/c/` read is slow but only happens once during dependency resolution, not on every build.

**Important:** This change should be committed because the project now lives on WSL2. The old `${user.home}/AppData/Roaming/Hytale` fallback in `build.gradle` won't apply anymore since `gradle.properties` takes precedence.

```bash
git add gradle.properties
git commit -m "chore: set hytaleHome for WSL2 native development"
```

### Step 7 — Modify `stagePlugins` to copy to Windows (2 min)

`stagePlugins` needs to put JARs where the Windows Hytale server reads them — the original `run/mods/` on Windows. Edit `build.gradle`:

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

Then add the property to `gradle.properties`:

```properties
stageModsDir=/mnt/c/Users/User/Documents/dev/Hytale/hyvexa_plugin/run/mods
```

This way `stagePlugins` compiles on WSL2 (fast), then copies 7 JARs to Windows (< 1 second via `/mnt/c/`).

```bash
git add build.gradle gradle.properties
git commit -m "chore: configure stagePlugins to output to Windows mods directory"
```

### Step 8 — Update `deploy.sh` (1 min)

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

### Step 9 — Test the build (3 min)

```bash
cd ~/dev/Hytale/hyvexa_plugin

# Full build
./gradlew build

# Verify stagePlugins works and JARs land on Windows
./gradlew stagePlugins
ls -la /mnt/c/Users/User/Documents/dev/Hytale/hyvexa_plugin/run/mods/Hyvexa*.jar
```

### Step 10 — Update CLAUDE.md (2 min)

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

### Step 11 — Push all changes (1 min)

```bash
git push origin main
```

### Step 12 — Configure IntelliJ (10 min)

This is the one-time IntelliJ setup. All done from IntelliJ on Windows.

#### 12a — Open the WSL2 project

1. File → Open
2. In the path bar, type: `\\wsl$\Ubuntu\home\playfade\dev\Hytale\hyvexa_plugin`
3. Click OK → Open as Project
4. Wait for IntelliJ to index (first time takes a few minutes)

#### 12b — Configure WSL2 JDK for Gradle

1. File → Settings → Build, Execution, Deployment → Build Tools → Gradle
2. Gradle JVM: click dropdown → Add JDK → "Download JDK" or "Add JDK from WSL"
   - If "Add JDK from WSL": select Ubuntu, it should find `/usr/lib/jvm/java-25-openjdk-amd64`
   - If manual: set path to `\\wsl$\Ubuntu\usr\lib\jvm\java-25-openjdk-amd64`
3. Distribution: "Wrapper" (default)
4. Click Apply

#### 12c — Create the HytaleServer run configuration

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
   C:\Users\User\Documents\dev\Hytale\hyvexa_plugin\run
   ```
8. Before launch (in order):
   - Run Gradle task: `stagePlugins` (this uses the WSL2 Gradle JDK automatically)
   - Build module: `hyvexa_plugin.hyvexa-launch.main`
9. Click Apply → OK

#### 12d — Test the run config

1. Select `HytaleServer` in the run config dropdown
2. Click Run (green arrow)
3. Expected: Gradle compiles on WSL2 → copies JARs to Windows `run/mods/` → server launches → you connect via Hytale client

### Step 13 — Migrate Claude Code memory (2 min)

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

### Step 14 — Archive the Windows source code (1 min)

Keep `run/` on Windows (the server reads from there). Delete only the source code:

```bash
# DON'T delete run/ — the server needs it
# Only clean up source code from the Windows copy

# Option A: rename (safe, reversible)
cd /mnt/c/Users/User/Documents/dev/Hytale
mv hyvexa_plugin hyvexa_plugin_OLD

# Option B: keep run/ and delete the rest (after you're confident)
# Do this later, not now
```

**Wait a few days before deleting.** Make sure the IntelliJ + WSL2 workflow is stable first.

### Step 15 — Verify everything (5 min)

```bash
cd ~/dev/Hytale/hyvexa_plugin

# Git
git status && git log --oneline -3

# Build speed (should be noticeably faster)
time ./gradlew build

# Stage JARs to Windows
./gradlew stagePlugins
ls /mnt/c/Users/User/Documents/dev/Hytale/hyvexa_plugin/run/mods/Hyvexa*.jar

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
| `gradle.properties` | Add `hytaleHome` and `stageModsDir` overrides |
| `build.gradle` | `stagePlugins` reads `stageModsDir` property |
| `deploy.sh` | `cmd.exe /c "gradlew.bat ..."` → `./gradlew ...` |
| `CLAUDE.md` | Build commands updated for native WSL2 |

## Rollback

If anything goes wrong:
1. The Windows repo is still at `/mnt/c/.../hyvexa_plugin_OLD/` (or the original path)
2. Reopen that project in IntelliJ
3. Everything works exactly as before
4. The WSL2 clone can be deleted: `rm -rf ~/dev/Hytale/hyvexa_plugin`

No data can be lost — the remote has everything pushed.

## Expected performance gains

| Operation | Before (cross-fs) | After (native WSL2) |
|-----------|-------------------|---------------------|
| `./gradlew build` (incremental) | ~15-30s | ~5-10s |
| `git status` | ~500ms | ~50ms |
| Claude Code grep | ~200-500ms | ~30-80ms |
| Claude Code file read | ~10-30ms | ~1-3ms |
| `stagePlugins` JAR copy | instant (local) | ~1s (7 JARs via /mnt/c/) |
