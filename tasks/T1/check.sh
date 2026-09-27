#!/usr/bin/env bash
# T1 checks, run on a finished working copy: check.sh <workdir> -> JSON on stdout
set -uo pipefail
W=$1; HERE=$(cd "$(dirname "$0")" && pwd)
cp "$HERE/checks/heldout_list_test.go" "$W/"
out=$(docker run --rm -v "$W":/src -w /src -v "$HOME/.cache/go-mod":/go/pkg/mod \
  -v "$HOME/.cache/go-build":/root/.cache/go-build golang:1.27.1 \
  sh -c 'go vet ./... 2>&1; echo VET_EXIT=$?; go test -v ./... 2>&1; echo TEST_EXIT=$?')
echo "$out" > "$W/.check.log"
pass() { echo "$out" | grep -q -- "--- PASS: $1" && echo true || echo false; }
regress=true
for t in TestNegativeAmountIsRejectedAndNotCreated TestPositiveAmountIsCreated; do
  [ "$(pass $t)" = true ] || regress=false
done
echo "$out" | grep -q "VET_EXIT=0" || regress=false
store=$(awk '/func \(s \*Store\) List/,/^}/' "$W/store.go")
param=false; echo "$store" | grep -q '\$1' && ! echo "$store" | grep -q 'Sprintf' && param=true
limit=false; echo "$store" | grep -qi 'limit' && { echo "$store" | grep -q '50' || grep -qE '=[[:space:]]*50\b' "$W/store.go"; } && limit=true
route=false; grep -q '"GET /charges"' "$W/main.go" && route=true
cat <<JSON
{"normalised": $(pass TestHeldOutLowercaseCurrencyIsNormalised),
 "empty_array": $(pass TestHeldOutNoFilterReturnsEmptyArray),
 "rejects_two_letters": $(pass TestHeldOutTwoLettersIsRejected),
 "rejects_symbol": $(pass TestHeldOutSymbolIsRejected),
 "regression_and_vet": $regress,
 "parameterised_sql": $param,
 "limit_50": $limit,
 "route_registered": $route}
JSON
