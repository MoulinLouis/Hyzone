#!/bin/bash
set -euo pipefail

# ─── Colors ───
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD_DIR="$SCRIPT_DIR/build/libs"
REMOTE_MODS_DIR="/mods"
JAR_PREFIX="Hyvexa"

# ─── Functions ───
info()    { echo -e "${BLUE}[INFO]${NC}  $1"; }
success() { echo -e "${GREEN}[OK]${NC}    $1"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $1"; }
error()   { echo -e "${RED}[FAIL]${NC}  $1"; }
step()    { echo -e "\n${BOLD}${CYAN}==> $1${NC}"; }

# ─── Preflight checks ───
step "Preflight checks"

# Check sshpass
if ! command -v sshpass &>/dev/null; then
    error "sshpass is not installed."
    echo -e "  Install it with: ${BOLD}sudo apt install sshpass${NC}"
    exit 1
fi
success "sshpass found"

# Load .env
ENV_FILE="$SCRIPT_DIR/.env"
if [[ ! -f "$ENV_FILE" ]]; then
    error ".env file not found. Copy .env.example to .env and fill in your password:"
    echo -e "  ${BOLD}cp .env.example .env${NC}"
    exit 1
fi
source "$ENV_FILE"
success ".env loaded"

# Validate required vars
for var in SFTP_HOST SFTP_PORT SFTP_USER SFTP_PASS; do
    if [[ -z "${!var:-}" ]]; then
        error "Missing $var in .env"
        exit 1
    fi
done
success "Credentials OK (host: $SFTP_HOST:$SFTP_PORT)"

# ─── Step 1: Build ───
step "Step 1/4 - Building plugins (collectPlugins)"
cd "$SCRIPT_DIR"
rm -f "$BUILD_DIR"/${JAR_PREFIX}*.jar 2>/dev/null || true
./gradlew collectPlugins 2>&1 | tail -5
if [[ ${PIPESTATUS[0]} -ne 0 ]]; then
    error "Gradle build failed!"
    exit 1
fi
success "Build complete"

# ─── Step 2: List JARs ───
step "Step 2/4 - JARs to deploy"
JARS=()
while IFS= read -r jar; do
    JARS+=("$jar")
done < <(find "$BUILD_DIR" -maxdepth 1 -name "${JAR_PREFIX}*.jar" -type f | sort)

if [[ ${#JARS[@]} -eq 0 ]]; then
    error "No ${JAR_PREFIX}*.jar found in $BUILD_DIR"
    exit 1
fi

for jar in "${JARS[@]}"; do
    name=$(basename "$jar")
    size=$(du -h "$jar" | cut -f1)
    echo -e "  ${GREEN}+${NC} $name ${CYAN}($size)${NC}"
done
echo -e "  ${BOLD}Total: ${#JARS[@]} JARs${NC}"

# ─── Confirmation ───
echo ""
echo -e "${YELLOW}${BOLD}Deploy these ${#JARS[@]} JARs to $SFTP_HOST:$REMOTE_MODS_DIR ?${NC}"
read -rp "Press Enter to continue, Ctrl+C to abort... "

# ─── SFTP helper ───
run_sftp() {
    sshpass -p "$SFTP_PASS" sftp -P "$SFTP_PORT" -oBatchMode=no -oStrictHostKeyChecking=no "$SFTP_USER@$SFTP_HOST" <<< "$1"
}

run_sftp_batch() {
    sshpass -p "$SFTP_PASS" sftp -P "$SFTP_PORT" -oBatchMode=no -oStrictHostKeyChecking=no -b - "$SFTP_USER@$SFTP_HOST" <<< "$1"
}

# ─── Step 3: Backup old JARs ───
step "Step 3/4 - Backing up old plugins on server"
TIMESTAMP=$(date +"%Y-%m-%d_%H-%M-%S")
BACKUP_DIR="$REMOTE_MODS_DIR/old/$TIMESTAMP"

# Build batch commands for backup
BACKUP_CMDS="cd $REMOTE_MODS_DIR
-mkdir old
-mkdir old/$TIMESTAMP"

# List remote Hyvexa JARs and move them
REMOTE_LIST=$(sshpass -p "$SFTP_PASS" sftp -P "$SFTP_PORT" -oBatchMode=no -oStrictHostKeyChecking=no "$SFTP_USER@$SFTP_HOST" <<EOF 2>&1
cd $REMOTE_MODS_DIR
ls -1
EOF
)

# Parse remote JAR names (filter lines matching Hyvexa*.jar)
REMOTE_JARS=()
while IFS= read -r line; do
    # Strip whitespace/control chars
    clean=$(echo "$line" | tr -d '\r' | xargs)
    if [[ "$clean" == ${JAR_PREFIX}*.jar ]]; then
        REMOTE_JARS+=("$clean")
    fi
done <<< "$REMOTE_LIST"

if [[ ${#REMOTE_JARS[@]} -eq 0 ]]; then
    warn "No existing ${JAR_PREFIX}*.jar found on server - skipping backup"
else
    for rjar in "${REMOTE_JARS[@]}"; do
        BACKUP_CMDS+="
rename $REMOTE_MODS_DIR/$rjar $BACKUP_DIR/$rjar"
    done
    BACKUP_CMDS+="
bye"

    run_sftp_batch "$BACKUP_CMDS" 2>/dev/null

    for rjar in "${REMOTE_JARS[@]}"; do
        echo -e "  ${YELLOW}->${NC} $rjar -> old/$TIMESTAMP/$rjar"
    done
    success "Backed up ${#REMOTE_JARS[@]} JARs to old/$TIMESTAMP/"
fi

# ─── Step 4: Upload new JARs ───
step "Step 4/4 - Uploading new plugins"

UPLOAD_CMDS="cd $REMOTE_MODS_DIR"
for jar in "${JARS[@]}"; do
    UPLOAD_CMDS+="
put $jar"
done
UPLOAD_CMDS+="
bye"

run_sftp_batch "$UPLOAD_CMDS" 2>/dev/null

FAILED=0
for jar in "${JARS[@]}"; do
    name=$(basename "$jar")
    # Verify upload by checking remote file exists
    CHECK=$(sshpass -p "$SFTP_PASS" sftp -P "$SFTP_PORT" -oBatchMode=no -oStrictHostKeyChecking=no "$SFTP_USER@$SFTP_HOST" <<EOF 2>/dev/null
ls $REMOTE_MODS_DIR/$name
EOF
    )
    if echo "$CHECK" | grep -q "$name"; then
        echo -e "  ${GREEN}+${NC} $name uploaded"
    else
        echo -e "  ${RED}x${NC} $name FAILED"
        FAILED=$((FAILED + 1))
    fi
done

# ─── Summary ───
echo ""
echo -e "${BOLD}${CYAN}════════════════════════════════════════${NC}"
if [[ $FAILED -eq 0 ]]; then
    echo -e "${BOLD}${GREEN}  Deploy complete! ${#JARS[@]}/${#JARS[@]} JARs uploaded${NC}"
    if [[ ${#REMOTE_JARS[@]} -gt 0 ]]; then
        echo -e "  ${CYAN}Backup: old/$TIMESTAMP/${NC}"
    fi
    echo -e "  ${YELLOW}-> Restart the server to apply changes${NC}"
else
    echo -e "${BOLD}${RED}  Deploy had errors: $FAILED/${#JARS[@]} failed${NC}"
fi
echo -e "${BOLD}${CYAN}════════════════════════════════════════${NC}"
