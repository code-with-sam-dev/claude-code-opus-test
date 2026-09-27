#!/usr/bin/env bash
# Go is not installed on this machine. This runs the Go toolchain in its
# official container with this directory mounted. Usage: ./go.sh test ./...
set -euo pipefail
cd "$(dirname "$0")"
exec docker run --rm -v "$PWD":/src -w /src \
  -v "${HOME}/.cache/go-mod":/go/pkg/mod -v "${HOME}/.cache/go-build":/root/.cache/go-build \
  -e CGO_ENABLED=0 golang:1.27.1 go "$@"
