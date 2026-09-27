#!/usr/bin/env bash
# T2 checks, run on a finished working copy: check.sh <workdir> -> JSON on stdout.
# Works on a scratch copy, so the model's working copy is never touched.
set -uo pipefail
W=$1; HERE=$(cd "$(dirname "$0")" && pwd)
export JAVA_HOME="${JAVA_HOME_25:-$HOME/.sdkman/candidates/java/25.0.4-amzn}"
S=$(mktemp -d); cp -R "$W"/. "$S"/; rm -rf "$S/target"
cp "$HERE/checks/HeldOutCustomerOrdersTests.java" "$S/src/test/java/dev/example/store/"
PORT=$(python3 -c 'import socket;s=socket.socket();s.bind(("",0));print(s.getsockname()[1])')
C=$(docker run -d --rm -e POSTGRES_USER=store -e POSTGRES_PASSWORD=store -e POSTGRES_DB=store \
  -p "$PORT:5432" postgres:17)
for _ in $(seq 60); do
  docker exec "$C" pg_isready -U store -d store -h 127.0.0.1 >/dev/null 2>&1 && break; sleep 1
done
(cd "$S" && POSTGRES_PORT=$PORT ./mvnw -B test > "$S/.check.log" 2>&1)
docker stop "$C" >/dev/null
cp "$S/.check.log" "$W/.check.log" 2>/dev/null
python3 - "$S" <<'PY'
import glob, json, sys, xml.etree.ElementTree as ET
held, others = {}, []
for f in glob.glob(f"{sys.argv[1]}/target/surefire-reports/TEST-*.xml"):
    for tc in ET.parse(f).getroot().iter("testcase"):
        ok = not any(c.tag in ("failure", "error", "skipped") for c in tc)
        (held.__setitem__(tc.get("name"), ok) if tc.get("classname", "").endswith("HeldOutCustomerOrdersTests")
         else others.append((tc.get("classname"), tc.get("name"), ok)))
names = ["orderCountIsCorrect", "orderCountIsBatched", "firstPageOfOneCustomer",
         "nextPageContinuesFromTheCursor", "defaultPageIsTen", "unknownCustomerIsNotFound",
         "nestedFieldsStayBatched"]
existing = [o for o in others if o[0].endswith("StoreControllerTests")]
out = {n: held.get(n, False) for n in names}
out["regression"] = len(existing) >= 2 and all(o[2] for o in others)
print(json.dumps(out))
PY
