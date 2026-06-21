#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$PROJECT_DIR/.firstclub.env"
APP_URL="http://localhost:8080"

log() {
    printf '\n[firstclub] %s\n' "$1"
}

fail() {
    printf '\n[firstclub] ERROR: %s\n' "$1" >&2
    exit 1
}

install_macos_dependencies() {
    command -v brew >/dev/null 2>&1 ||
        fail "Homebrew is required to install Docker Desktop and Java 21. Install it from https://brew.sh and rerun this script."

    if ! command -v docker >/dev/null 2>&1; then
        log "Installing Docker Desktop..."
        brew install --cask docker
    fi

    if ! command -v java >/dev/null 2>&1 ||
        ! java -version 2>&1 | grep -qE 'version "(21|2[2-9]|[3-9][0-9])'; then
        log "Installing Java 21..."
        brew install openjdk@21
        export PATH="$(brew --prefix openjdk@21)/bin:$PATH"
    fi

    if ! docker info >/dev/null 2>&1; then
        log "Starting Docker Desktop. Its first launch may ask for macOS approval..."
        open -a Docker
    fi
}

install_linux_dependencies() {
    if ! command -v java >/dev/null 2>&1 ||
        ! java -version 2>&1 | grep -qE 'version "(21|2[2-9]|[3-9][0-9])'; then
        log "Installing Java 21..."
        if command -v apt-get >/dev/null 2>&1; then
            sudo apt-get update
            sudo apt-get install -y openjdk-21-jdk
        elif command -v dnf >/dev/null 2>&1; then
            sudo dnf install -y java-21-openjdk-devel
        else
            fail "Install Java 21, then rerun this script."
        fi
    fi

    if ! command -v docker >/dev/null 2>&1; then
        log "Installing Docker..."
        if command -v apt-get >/dev/null 2>&1; then
            sudo apt-get update
            sudo apt-get install -y docker.io docker-compose-v2
        elif command -v dnf >/dev/null 2>&1; then
            sudo dnf install -y docker docker-compose-plugin
        else
            fail "Install Docker Engine with the Compose plugin, then rerun this script."
        fi
    fi

    if command -v systemctl >/dev/null 2>&1; then
        sudo systemctl enable --now docker
    fi
}

wait_for_docker() {
    log "Waiting for the Docker engine..."
    for _ in $(seq 1 90); do
        if docker info >/dev/null 2>&1; then
            return
        fi
        sleep 2
    done
    fail "Docker did not become ready. Open Docker Desktop and rerun the script."
}

write_local_environment() {
    if [[ ! -f "$ENV_FILE" ]]; then
        cat >"$ENV_FILE" <<'EOF'
FIRSTCLUB_ADMIN_EMAIL=admin@example.com
FIRSTCLUB_ADMIN_PASSWORD=FirstClubAdmin123!
DB_URL=jdbc:postgresql://localhost:5432/firstclub
DB_USERNAME=firstclub
DB_PASSWORD=firstclub
EOF
        chmod 600 "$ENV_FILE"
        log "Created .firstclub.env with local administrator credentials."
    fi

    set -a
    # shellcheck disable=SC1090
    source "$ENV_FILE"
    set +a
}

wait_for_postgres() {
    log "Waiting for PostgreSQL..."
    for _ in $(seq 1 60); do
        if docker compose -f "$PROJECT_DIR/compose.yaml" exec -T postgres \
            pg_isready -U firstclub -d firstclub >/dev/null 2>&1; then
            return
        fi
        sleep 2
    done
    docker compose -f "$PROJECT_DIR/compose.yaml" logs postgres
    fail "PostgreSQL did not become healthy."
}

handle_existing_port() {
    if ! command -v lsof >/dev/null 2>&1 ||
        ! lsof -nP -iTCP:8080 -sTCP:LISTEN >/dev/null 2>&1; then
        return
    fi

    if command -v curl >/dev/null 2>&1 &&
        curl --max-time 3 -fsS "$APP_URL/login" 2>/dev/null |
            grep -q "FirstClub"; then
        log "FirstClub is already running at $APP_URL"
        printf '[firstclub] Member signup: %s/signup\n' "$APP_URL"
        printf '[firstclub] Admin login:  %s/login\n' "$APP_URL"
        printf '[firstclub] Nothing else needs to be started.\n'
        exit 0
    fi

    local listener
    listener="$(lsof -nP -iTCP:8080 -sTCP:LISTEN | tail -n +2 | head -n 1)"
    fail "Port 8080 is occupied by another process: $listener"
}

case "$(uname -s)" in
    Darwin) install_macos_dependencies ;;
    Linux) install_linux_dependencies ;;
    *) fail "This setup script currently supports macOS and Linux." ;;
esac

wait_for_docker

command -v java >/dev/null 2>&1 || fail "Java is unavailable."
java -version 2>&1 | grep -qE 'version "(21|2[2-9]|[3-9][0-9])' ||
    fail "Java 21 or newer is required."
docker compose version >/dev/null 2>&1 ||
    fail "Docker Compose v2 is required."

write_local_environment

log "Starting PostgreSQL..."
docker compose -f "$PROJECT_DIR/compose.yaml" up -d
wait_for_postgres

handle_existing_port

log "Starting FirstClub at $APP_URL"
printf '[firstclub] Member signup: %s/signup\n' "$APP_URL"
printf '[firstclub] Admin login:  %s/login\n' "$APP_URL"
printf '[firstclub] Admin email:  %s\n' "$FIRSTCLUB_ADMIN_EMAIL"
printf '[firstclub] Admin password is stored in %s\n\n' "$ENV_FILE"

cd "$PROJECT_DIR"
exec ./mvnw spring-boot:run
