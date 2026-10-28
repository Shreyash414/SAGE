#!/usr/bin/env bash
# ==============================================================================
# SAGE — Institutional Approval & Compliance Platform
# Master Service Runner: Database -> Backend API -> User Frontend -> Admin Panel
# ==============================================================================

set -eo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_ROOT"

# Load local environment variables if .env exists
if [ -f "$PROJECT_ROOT/.env" ]; then
  # shellcheck source=/dev/null
  set -a
  source "$PROJECT_ROOT/.env"
  set +a
fi

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_USER="${DB_USER:-aicte_app_user}"
DB_NAME="${DB_NAME:-aicte_platform_db}"
SERVER_PORT="${SERVER_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-5173}"
ADMIN_PORT="${ADMIN_PORT:-5174}"

MINIO_PORT="${MINIO_PORT:-9000}"
MINIO_CONSOLE_PORT="${MINIO_CONSOLE_PORT:-9001}"

# Colors
C_RESET='\033[0m'
C_BOLD='\033[1m'
C_GREEN='\033[32m'
C_BLUE='\033[34m'
C_YELLOW='\033[33m'
C_CYAN='\033[36m'
C_RED='\033[31m'

echo -e "${C_BOLD}${C_BLUE}==============================================================================${C_RESET}"
echo -e "${C_BOLD}${C_CYAN}    SAGE — Institutional Approval & Compliance Platform${C_RESET}"
echo -e "${C_BOLD}${C_BLUE}==============================================================================${C_RESET}"

# PID tracking for clean teardown
BACKEND_PID=""
FRONTEND_PID=""
ADMIN_PID=""
MINIO_PID=""

cleanup() {
  echo ""
  echo -e "${C_YELLOW}>>> Initiating graceful shutdown of all platform services...${C_RESET}"
  if [ -n "$ADMIN_PID" ] && kill -0 "$ADMIN_PID" 2>/dev/null; then
    echo -e "Stopping Admin Frontend (PID: $ADMIN_PID)..."
    kill "$ADMIN_PID" 2>/dev/null || true
  fi
  if [ -n "$FRONTEND_PID" ] && kill -0 "$FRONTEND_PID" 2>/dev/null; then
    echo -e "Stopping User Frontend (PID: $FRONTEND_PID)..."
    kill "$FRONTEND_PID" 2>/dev/null || true
  fi
  if [ -n "$BACKEND_PID" ] && kill -0 "$BACKEND_PID" 2>/dev/null; then
    echo -e "Stopping Backend (PID: $BACKEND_PID)..."
    kill "$BACKEND_PID" 2>/dev/null || true
  fi
  if [ -n "$MINIO_PID" ] && kill -0 "$MINIO_PID" 2>/dev/null; then
    echo -e "Stopping MinIO Object Storage (PID: $MINIO_PID)..."
    kill "$MINIO_PID" 2>/dev/null || true
  fi
  echo -e "${C_GREEN}>>> All services stopped cleanly.${C_RESET}"
  exit 0
}

trap cleanup SIGINT SIGTERM EXIT

# ------------------------------------------------------------------------------
# 1. Start / Verify PostgreSQL Database
# ------------------------------------------------------------------------------
echo -e "\n${C_BOLD}[1/4] Checking PostgreSQL Database (${DB_HOST}:${DB_PORT})...${C_RESET}"

is_db_ready() {
  if command -v pg_isready >/dev/null 2>&1; then
    pg_isready -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" >/dev/null 2>&1
  else
    # Fallback socket test via bash/nc
    timeout 1 bash -c "cat < /dev/null > /dev/tcp/${DB_HOST}/${DB_PORT}" 2>/dev/null
  fi
}

if is_db_ready; then
  echo -e "${C_GREEN}✓ PostgreSQL is active and accepting connections on port ${DB_PORT}.${C_RESET}"
else
  echo -e "${C_YELLOW}! PostgreSQL is not currently running. Attempting startup...${C_RESET}"

  if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    echo -e "Starting PostgreSQL container via docker compose..."
    docker compose up -d postgres || sudo docker compose up -d postgres || true
  elif command -v docker-compose >/dev/null 2>&1; then
    echo -e "Starting PostgreSQL container via docker-compose..."
    docker-compose up -d postgres || true
  elif command -v systemctl >/dev/null 2>&1; then
    echo -e "Starting system PostgreSQL service..."
    sudo systemctl start postgresql 2>/dev/null || true
  fi

  # Wait for database readiness
  echo -n "Waiting for PostgreSQL to be ready..."
  READY=0
  for _ in {1..30}; do
    if is_db_ready; then
      READY=1
      break
    fi
    echo -n "."
    sleep 1
  done
  echo ""

  if [ "$READY" -eq 1 ]; then
    echo -e "${C_GREEN}✓ PostgreSQL database is now ready.${C_RESET}"
  else
    echo -e "${C_RED}✗ Unable to connect to PostgreSQL at ${DB_HOST}:${DB_PORT}.${C_RESET}"
    echo -e "Please ensure PostgreSQL is started with credentials matching .env or compose.yaml."
    exit 1
  fi
fi

# ------------------------------------------------------------------------------
# 2. Check / Start MinIO Object Storage (Port: 9000)
# ------------------------------------------------------------------------------
echo -e "\n${C_BOLD}[2/5] Checking MinIO Object Storage (${MINIO_PORT})...${C_RESET}"

is_minio_ready() {
  curl -s -m 1 "http://localhost:${MINIO_PORT}/minio/health/live" >/dev/null 2>&1
}

if is_minio_ready; then
  echo -e "${C_GREEN}✓ MinIO Object Storage is active on port ${MINIO_PORT}.${C_RESET}"
else
  echo -e "${C_YELLOW}! MinIO is not running. Attempting startup...${C_RESET}"

  MINIO_BIN="$HOME/.local/bin/minio"
  if [ ! -x "$MINIO_BIN" ] && command -v minio >/dev/null 2>&1; then
    MINIO_BIN="$(command -v minio)"
  fi

  if [ -x "$MINIO_BIN" ]; then
    MINIO_DATA_DIR="${HOME}/.sage_minio_data"
    mkdir -p "$MINIO_DATA_DIR"
    export MINIO_ROOT_USER="${MINIO_ACCESS_KEY:-sage_minio_admin}"
    export MINIO_ROOT_PASSWORD="${MINIO_SECRET_KEY:-sage_minio_secret_2026}"
    "$MINIO_BIN" server "$MINIO_DATA_DIR" --address ":${MINIO_PORT}" --console-address ":${MINIO_CONSOLE_PORT}" > /tmp/minio_sage.log 2>&1 &
    MINIO_PID=$!
    echo -e "MinIO launched locally (PID: ${MINIO_PID})..."
  elif command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    echo -e "Starting MinIO container via docker compose..."
    docker compose up -d minio || sudo docker compose up -d minio || true
  fi

  # Wait for MinIO readiness
  echo -n "Waiting for MinIO to become ready..."
  MINIO_READY=0
  for _ in {1..20}; do
    if is_minio_ready; then
      MINIO_READY=1
      break
    fi
    echo -n "."
    sleep 1
  done
  echo ""

  if [ "$MINIO_READY" -eq 1 ]; then
    echo -e "${C_GREEN}✓ MinIO Object Storage is now ready on port ${MINIO_PORT}.${C_RESET}"
  else
    echo -e "${C_YELLOW}! MinIO startup timed out; proceeding (bucket will be auto-created when accessible).${C_RESET}"
  fi
fi

# ------------------------------------------------------------------------------
# 3. Start Spring Boot Backend
# ------------------------------------------------------------------------------
echo -e "\n${C_BOLD}[3/5] Starting Spring Boot Backend API (Port: ${SERVER_PORT})...${C_RESET}"

# Free port 8080 if an old instance is lingering
if command -v fuser >/dev/null 2>&1; then
  fuser -k "${SERVER_PORT}/tcp" 2>/dev/null || true
elif command -v lsof >/dev/null 2>&1; then
  OLD_PID=$(lsof -ti:"${SERVER_PORT}" 2>/dev/null || true)
  if [ -n "$OLD_PID" ]; then
    kill -9 "$OLD_PID" 2>/dev/null || true
  fi
fi

# Launch backend in background
./mvnw spring-boot:run &
BACKEND_PID=$!
echo -e "Spring Boot starting in background (PID: ${BACKEND_PID})..."

# Wait for backend health check
echo -n "Waiting for Backend API to become healthy..."
BACKEND_READY=0
for _ in {1..60}; do
  HEALTH_STATUS=$(curl -s "http://localhost:${SERVER_PORT}/api/v1/health" 2>/dev/null | grep -o '"status":"UP"' || true)
  if [ -n "$HEALTH_STATUS" ]; then
    BACKEND_READY=1
    break
  fi
  if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
    echo -e "\n${C_RED}✗ Spring Boot process exited unexpectedly. Check logs for details.${C_RESET}"
    exit 1
  fi
  echo -n "."
  sleep 1
done
echo ""

if [ "$BACKEND_READY" -eq 1 ]; then
  echo -e "${C_GREEN}✓ Backend is healthy and running on http://localhost:${SERVER_PORT}${C_RESET}"
else
  echo -e "${C_RED}✗ Backend health check timed out on http://localhost:${SERVER_PORT}/api/v1/health${C_RESET}"
  exit 1
fi

# ------------------------------------------------------------------------------
# 4. Start Vite React User Frontend (Port: 5173)
# ------------------------------------------------------------------------------
echo -e "\n${C_BOLD}[4/5] Starting Vite React User Frontend (Port: ${FRONTEND_PORT})...${C_RESET}"

# Free frontend port if lingering
if command -v fuser >/dev/null 2>&1; then
  fuser -k "${FRONTEND_PORT}/tcp" 2>/dev/null || true
fi

cd "$PROJECT_ROOT/frontend"

# Ensure dependencies are installed
if [ ! -d "node_modules" ]; then
  echo -e "Installing user frontend dependencies..."
  npm install
fi

# Start frontend development server in background
npm run dev -- --host 0.0.0.0 --port "${FRONTEND_PORT}" --force &
FRONTEND_PID=$!
cd "$PROJECT_ROOT"

echo -e "User Frontend starting in background (PID: ${FRONTEND_PID})..."

# Wait for frontend port
echo -n "Waiting for User Frontend to respond..."
FRONTEND_READY=0
for _ in {1..20}; do
  if curl -s -I "http://localhost:${FRONTEND_PORT}" 2>/dev/null | grep -q "HTTP/"; then
    FRONTEND_READY=1
    break
  fi
  if ! kill -0 "$FRONTEND_PID" 2>/dev/null; then
    echo -e "\n${C_RED}✗ User Frontend process exited unexpectedly.${C_RESET}"
    exit 1
  fi
  echo -n "."
  sleep 1
done
echo ""

if [ "$FRONTEND_READY" -eq 1 ]; then
  echo -e "${C_GREEN}✓ User Frontend is accessible on http://localhost:${FRONTEND_PORT}${C_RESET}"
fi

# ------------------------------------------------------------------------------
# 5. Start SAGE Admin Panel (Port: 5174)
# ------------------------------------------------------------------------------
echo -e "\n${C_BOLD}[5/5] Starting SAGE Admin Panel (Port: ${ADMIN_PORT})...${C_RESET}"

# Free admin port if lingering
if command -v fuser >/dev/null 2>&1; then
  fuser -k "${ADMIN_PORT}/tcp" 2>/dev/null || true
fi

cd "$PROJECT_ROOT/admin"

# Ensure dependencies are installed
if [ ! -d "node_modules" ]; then
  echo -e "Installing admin panel dependencies..."
  npm install
fi

# Start admin development server explicitly on ADMIN_PORT with strictPort
npm run dev -- --host 0.0.0.0 --port "${ADMIN_PORT}" --strictPort --force &
ADMIN_PID=$!
cd "$PROJECT_ROOT"

echo -e "Admin Panel starting in background (PID: ${ADMIN_PID})..."

# Wait for admin panel port
echo -n "Waiting for Admin Panel to respond..."
ADMIN_READY=0
for _ in {1..20}; do
  if curl -s -I "http://localhost:${ADMIN_PORT}" 2>/dev/null | grep -q "HTTP/"; then
    ADMIN_READY=1
    break
  fi
  if ! kill -0 "$ADMIN_PID" 2>/dev/null; then
    echo -e "\n${C_RED}✗ Admin Panel process exited unexpectedly.${C_RESET}"
    exit 1
  fi
  echo -n "."
  sleep 1
done
echo ""

if [ "$ADMIN_READY" -eq 1 ]; then
  echo -e "${C_GREEN}✓ Admin Panel is accessible on http://localhost:${ADMIN_PORT}${C_RESET}"
fi

# ------------------------------------------------------------------------------
# Platform Ready Summary
# ------------------------------------------------------------------------------
echo -e "\n${C_BOLD}${C_GREEN}==============================================================================${C_RESET}"
echo -e "${C_BOLD}${C_GREEN}    🚀 SAGE — Institutional Approval & Compliance Platform is FULLY OPERATIONAL${C_RESET}"
echo -e "${C_BOLD}${C_GREEN}==============================================================================${C_RESET}"
echo -e "${C_BOLD}• User Portal UI:${C_RESET}     ${C_CYAN}http://localhost:${FRONTEND_PORT}${C_RESET}"
echo -e "${C_BOLD}• SAGE Admin Panel:${C_RESET}   ${C_CYAN}http://localhost:${ADMIN_PORT}${C_RESET}"
echo -e "${C_BOLD}• Backend REST API:${C_RESET}   ${C_CYAN}http://localhost:${SERVER_PORT}${C_RESET}"
echo -e "${C_BOLD}• MinIO Object Storage:${C_RESET} ${C_CYAN}http://localhost:${MINIO_PORT}${C_RESET} (Console: ${MINIO_CONSOLE_PORT})"
echo -e "${C_BOLD}• Swagger OpenAPI UI:${C_RESET} ${C_CYAN}http://localhost:${SERVER_PORT}/swagger-ui.html${C_RESET}"
echo -e "${C_BOLD}• Health Endpoint:${C_RESET}    ${C_CYAN}http://localhost:${SERVER_PORT}/api/v1/health${C_RESET}"
echo -e "${C_BOLD}• PostgreSQL DB:${C_RESET}      ${C_CYAN}localhost:${DB_PORT}/${DB_NAME}${C_RESET} (User: ${DB_USER})"
echo -e "------------------------------------------------------------------------------"
echo -e "${C_YELLOW}Press Ctrl+C at any time to shut down all services gracefully.${C_RESET}"
echo -e "------------------------------------------------------------------------------\n"

# Keep the script running and streaming service output until Ctrl+C
wait "$BACKEND_PID" "$FRONTEND_PID" "$ADMIN_PID"
