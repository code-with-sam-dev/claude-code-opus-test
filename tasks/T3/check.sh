#!/usr/bin/env bash
# T3 checks, run on a finished working copy: check.sh <workdir> -> JSON on stdout.
# Works on a scratch copy, so the model's working copy is never touched.
set -uo pipefail
W=$1; HERE=$(cd "$(dirname "$0")" && pwd)
export JAVA_HOME="${JAVA_HOME_25:-$HOME/.sdkman/candidates/java/25.0.4-amzn}"
S=$(mktemp -d); cp -R "$W"/. "$S"/; rm -rf "$S/target"
mkdir -p "$S/src/test/java/dev/example/orders"
cp "$HERE/checks/HeldOutDeadLetterTests.java" "$S/src/test/java/dev/example/orders/"
# The embedded broker comes from spring-kafka-test; add it if the model did not.
grep -q "<artifactId>spring-kafka-test</artifactId>" "$S/pom.xml" || perl -0pi -e \
 's#</dependencies>#\t<dependency>\n\t\t\t<groupId>org.springframework.kafka</groupId>\n\t\t\t<artifactId>spring-kafka-test</artifactId>\n\t\t\t<scope>test</scope>\n\t\t</dependency>\n\t</dependencies>#' "$S/pom.xml"
PORT=$(python3 -c 'import socket;s=socket.socket();s.bind(("",0));print(s.getsockname()[1])')
C=$(docker run -d --rm -e POSTGRES_USER=orders -e POSTGRES_PASSWORD=orders -e POSTGRES_DB=orders \
  -p "$PORT:5432" postgres:17)
for _ in $(seq 60); do
  docker exec "$C" pg_isready -U orders -d orders -h 127.0.0.1 >/dev/null 2>&1 && break; sleep 1
done
(cd "$S" && POSTGRES_PORT=$PORT ./mvnw -B test > "$S/.check.log" 2>&1)
docker stop "$C" >/dev/null
cp "$S/.check.log" "$W/.check.log" 2>/dev/null
python3 - "$S" <<'PY'
import glob, json, re, sys, xml.etree.ElementTree as ET
S = sys.argv[1]
held, others = {}, []
for f in glob.glob(f"{S}/target/surefire-reports/TEST-*.xml"):
    for tc in ET.parse(f).getroot().iter("testcase"):
        ok = not any(c.tag in ("failure", "error", "skipped") for c in tc)
        (held.__setitem__(tc.get("name"), ok) if tc.get("classname", "").endswith("HeldOutDeadLetterTests")
         else others.append(ok))
names = ["goodRecordIsPaidOnce", "transientFailureRecovers", "transientRetriesAreBoundedAndBackedOff",
         "malformedRecordGoesStraightToTheDlt", "unknownOrderIsNotRetried", "goodRecordAfterAPoisonOneIsPaid"]
out = {n: held.get(n, False) for n in names}
svc = open(f"{S}/src/main/java/dev/example/orders/OrderService.java").read()
m = re.search(r"void markPaid\(.*?\n    }\n", svc, re.S)
out["service_throws_not_found"] = bool(m and "OrderNotFoundException" in m.group(0)
                                       and "IllegalStateException" not in m.group(0))
out["own_tests_pass"] = len(others) > 0 and all(others)
print(json.dumps(out))
PY
