#!/usr/bin/env python3
"""Experiments B and C: three coding tasks, three models, three repeats.

Each run gets a fresh copy of the task's base in a scratch directory outside
this repository, one short test-only CLAUDE.md, and the task prompt. Claude Code
runs headless with Read, Edit and Write inside that copy, and Bash limited to an
allowlist: the project's build wrappers and read-only commands. Anything else is
denied, identically for every model. Effort is never set: the claim is about
default settings. When the run ends, the task's own check.sh grades the working
copy against tests the model never saw.

    python3 harness/run_tasks.py --out results/tasks            # all 27, resumable
    python3 harness/run_tasks.py --tasks T1 --models claude-opus-5-5 --repeats 1

Order is rotated within each task, so no model always goes first:
repeat 1 is 5.5, 5, Fable; repeat 2 is 5, Fable, 5.5; repeat 3 is Fable, 5.5, 5.
A finished run is never redone; delete its row to redo it.
"""
import argparse
import json
import os
import shutil
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
MODELS = ["claude-opus-5-5", "claude-opus-5", "claude-fable-5-1"]
TOOLS = "Bash,Read,Edit,Write"
ALLOWED = [
    "Read", "Edit", "Write",
    "Bash(./go.sh:*)", "Bash(./mvnw:*)",
    "Bash(ls:*)", "Bash(cat:*)", "Bash(head:*)", "Bash(tail:*)", "Bash(wc:*)",
    "Bash(grep:*)", "Bash(rg:*)", "Bash(find:*)", "Bash(pwd)", "Bash(mkdir:*)",
    "Bash(git status:*)", "Bash(git diff:*)", "Bash(git log:*)",
]
TIMEOUT_S = 45 * 60
SCRATCH = Path("/private/tmp/opus-runs")
JAVA_HOME = os.environ.get("JAVA_HOME_25", str(Path.home() / ".sdkman/candidates/java/25.0.4-amzn"))

# Dollars per million tokens, from the published pricing page, 27 September 2026.
RATES = {
    "claude-opus-5-5": {"in": 4, "out": 20, "w5m": 5, "w1h": 8, "read": 0.20},
    "claude-opus-5": {"in": 5, "out": 25, "w5m": 6.25, "w1h": 10, "read": 0.50},
    "claude-fable-5-1": {"in": 10, "out": 50, "w5m": 12.50, "w1h": 20, "read": 0.25},
    "claude-haiku-4-5": {"in": 1, "out": 5, "w5m": 1.25, "w1h": 2, "read": 0.10},
}
TEST_CLAUDE_MD = "This is a disposable working copy for a coding task. Work only inside this directory.\n"
LEAK_MARKERS = ["claude-code-opus-test", "HeldOut", "heldout", "check.sh", "reference.patch"]


def rates_for(model_id: str) -> dict | None:
    # Longest key first, so claude-opus-5-5 is not priced as claude-opus-5.
    for key in sorted(RATES, key=len, reverse=True):
        if model_id.startswith(key):
            return RATES[key]
    return None


def price(model_id: str, uncached: int, w5m: int, w1h: int, read: int, out: int) -> float | None:
    r = rates_for(model_id)
    if r is None:
        return None
    return (uncached * r["in"] + w5m * r["w5m"] + w1h * r["w1h"] + read * r["read"] + out * r["out"]) / 1e6


def order_for(repeat: int) -> list[str]:
    k = (repeat - 1) % len(MODELS)
    return MODELS[k:] + MODELS[:k]


def prepare(task: str, model: str, repeat: int) -> Path:
    work = SCRATCH / f"{task}-{model}-r{repeat}-{int(time.time())}"
    shutil.copytree(ROOT / "tasks" / task / "base", work)
    (work / "CLAUDE.md").write_text(TEST_CLAUDE_MD)
    git = ["git", "-c", "user.name=test", "-c", "user.email=test@example.com"]
    subprocess.run(["git", "init", "-q"], cwd=work, check=True)
    subprocess.run(git + ["add", "-A"], cwd=work, check=True)
    subprocess.run(git + ["commit", "-qm", "base"], cwd=work, check=True)
    return work


def run_agent(model: str, prompt: str, work: Path, raw: Path) -> tuple[dict | None, float, bool]:
    cmd = [
        "claude", "-p", prompt, "--model", model,
        "--tools", TOOLS, "--allowedTools", *ALLOWED,
        "--settings", str(HERE / "settings.json"),
        "--mcp-config", str(HERE / "no-mcp.json"), "--strict-mcp-config",
        "--disable-slash-commands", "--no-chrome",
        "--output-format", "stream-json", "--verbose", "--include-partial-messages",
    ]
    env = dict(os.environ, JAVA_HOME=JAVA_HOME, PATH=f"{JAVA_HOME}/bin:{os.environ['PATH']}")
    start = time.monotonic()
    result, timed_out = None, False
    with raw.open("w") as log:
        proc = subprocess.Popen(cmd, cwd=work, env=env, stdin=subprocess.DEVNULL,
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        for line in proc.stdout:
            now = time.monotonic() - start
            log.write(json.dumps({"t": round(now, 3), "line": line.rstrip("\n")}) + "\n")
            try:
                event = json.loads(line)
            except json.JSONDecodeError:
                continue
            if event.get("type") == "result":
                result = event
            if now > TIMEOUT_S:
                proc.kill()
                timed_out = True
                break
        proc.wait()
    return result, time.monotonic() - start, timed_out


def tool_calls(raw: Path) -> tuple[int, dict, list[str], int]:
    count, by_name, leaks, denied = 0, {}, [], 0
    for line in raw.open():
        try:
            event = json.loads(json.loads(line)["line"])
        except (json.JSONDecodeError, KeyError):
            continue
        for block in event.get("message", {}).get("content", []) if isinstance(event.get("message"), dict) else []:
            if not isinstance(block, dict):
                continue
            if event.get("type") == "assistant" and block.get("type") == "tool_use":
                count += 1
                by_name[block["name"]] = by_name.get(block["name"], 0) + 1
                text = json.dumps(block.get("input", {}))
                leaks += [m for m in LEAK_MARKERS if m in text]
            if event.get("type") == "user" and block.get("type") == "tool_result" and block.get("is_error"):
                if "permission" in json.dumps(block.get("content", "")).lower():
                    denied += 1
    return count, by_name, sorted(set(leaks)), denied


def cost(result: dict) -> dict:
    usage = result.get("usage", {})
    split = usage.get("cache_creation", {}) or {}
    per_model = result.get("modelUsage", {}) or {}
    total, lines = 0.0, {}
    for mid, u in per_model.items():
        uncached, read, out = u.get("inputTokens", 0), u.get("cacheReadInputTokens", 0), u.get("outputTokens", 0)
        writes = u.get("cacheCreationInputTokens", 0)
        if len(per_model) == 1:
            w5m, w1h = split.get("ephemeral_5m_input_tokens", 0), split.get("ephemeral_1h_input_tokens", 0)
        else:
            # modelUsage does not split cache writes by lifetime. Claude Code
            # writes the main conversation with the 1 hour lifetime.
            w5m, w1h = 0, writes
        p = price(mid, uncached, w5m, w1h, read, out)
        lines[mid] = {"uncached_input": uncached, "cache_write_5m": w5m, "cache_write_1h": w1h,
                      "cache_read": read, "output": out, "calculated_usd": p}
        total += p or 0.0
    return {"by_model": lines, "calculated_usd": round(total, 4),
            "claude_code_total_cost_usd": result.get("total_cost_usd")}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--tasks", nargs="+", default=["T1", "T2", "T3"])
    ap.add_argument("--models", nargs="+", default=MODELS)
    ap.add_argument("--repeats", type=int, default=3)
    ap.add_argument("--out", default="results/tasks")
    args = ap.parse_args()
    out = Path(args.out)
    SCRATCH.mkdir(parents=True, exist_ok=True)
    for task in args.tasks:
        prompt = (ROOT / "tasks" / task / "prompt.md").read_text()
        for repeat in range(1, args.repeats + 1):
            for model in [m for m in order_for(repeat) if m in args.models]:
                row_path = out / task / f"{model}-r{repeat}.json"
                if row_path.exists():
                    continue
                row_path.parent.mkdir(parents=True, exist_ok=True)
                work = prepare(task, model, repeat)
                raw = row_path.with_suffix(".stream.jsonl")
                result, wall, timed_out = run_agent(model, prompt, work, raw)
                check = subprocess.run([str(ROOT / "tasks" / task / "check.sh"), str(work)],
                                       capture_output=True, text=True)
                try:
                    checks = json.loads(check.stdout[check.stdout.index("{"):])
                except (json.JSONDecodeError, ValueError):
                    checks = {"checker_failed": check.stdout[-500:] + check.stderr[-500:]}
                subprocess.run(["git", "add", "-A"], cwd=work)
                patch = subprocess.run(["git", "diff", "--cached", "HEAD", "--", ".", ":!CLAUDE.md", ":!.check.log"],
                                       cwd=work, capture_output=True, text=True).stdout
                row_path.with_suffix(".patch").write_text(patch)
                calls, by_name, leaks, denied = tool_calls(raw)
                passed = [v for v in checks.values() if isinstance(v, bool)]
                row = {
                    "task": task, "model": model, "repeat": repeat,
                    "order_in_repeat": order_for(repeat).index(model) + 1,
                    "workdir": str(work),
                    "timed_out": timed_out,
                    "result_subtype": result.get("subtype") if result else None,
                    "wall_s": round(wall, 1),
                    "claude_duration_s": round(result["duration_ms"] / 1000, 1) if result else None,
                    "num_turns": result.get("num_turns") if result else None,
                    "tool_calls": calls, "tool_calls_by_name": by_name,
                    "permission_denials": denied,
                    "possible_leak": leaks,
                    "checks": checks,
                    "checks_passed": sum(passed), "checks_total": len(passed),
                    "all_passed": bool(passed) and all(passed),
                    "cost": cost(result) if result else None,
                }
                row_path.write_text(json.dumps(row, indent=1))
                print(json.dumps({k: row[k] for k in ("task", "model", "repeat", "wall_s", "num_turns",
                                                      "tool_calls", "permission_denials",
                                                      "checks_passed", "checks_total")}
                                 | {"usd": row["cost"]["calculated_usd"] if row["cost"] else None,
                                    "leak": leaks}), flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
