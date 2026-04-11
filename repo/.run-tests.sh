#!/usr/bin/env bash

set -euo pipefail

REPO_ROOT="$(CDPATH= cd -- "$(dirname "$0")" && pwd)"
BACKEND_DIR="$REPO_ROOT/backend"
FRONTEND_DIR="$REPO_ROOT/frontend"
CACHE_DIR="$REPO_ROOT/.cache/test-runner"
M2_CACHE="$CACHE_DIR/.m2"
NPM_CACHE="$CACHE_DIR/.npm"
MAVEN_IMAGE="${MAVEN_DOCKER_IMAGE:-maven:3.9-eclipse-temurin-21}"
NODE_IMAGE="${NODE_DOCKER_IMAGE:-node:22-bullseye}"

log() {
  printf '\n[%s] %s\n' "$(date +%H:%M:%S)" "$*"
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

run_backend_tests() {
  require_cmd docker
  mkdir -p "$M2_CACHE"

  log "Running backend tests in Docker via ${MAVEN_IMAGE}"
  docker run --rm \
    --network host \
    -v "${M2_CACHE}:/root/.m2" \
    -v "${BACKEND_DIR}:/workspace" \
    -w /workspace \
    "$MAVEN_IMAGE" \
    mvn test
}

run_frontend_tests() {
  require_cmd docker
  mkdir -p "$NPM_CACHE"

  log "Running frontend tests in Docker via ${NODE_IMAGE}"
  docker run --rm \
    -v "${NPM_CACHE}:/root/.npm" \
    -v "${FRONTEND_DIR}:/workspace" \
    -w /workspace \
    "$NODE_IMAGE" \
    sh -lc '
      if [ -f package-lock.json ]; then
        npm ci
      else
        npm install
      fi
      CI=true npm test -- --watch=false
    '
}

main() {
  log "Starting repository test run"
  run_backend_tests
  run_frontend_tests
  log "All tests passed"
}

main "$@"
