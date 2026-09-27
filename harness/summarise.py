#!/usr/bin/env python3
"""Summarise experiments B and C: one row per run, then medians and ranges.

    python3 harness/summarise.py --regrade      # re-run every checker first
    python3 harness/summarise.py                # use the grades stored in each row

--regrade runs the task's current check.sh on each run's working copy and keeps
the grade the run was given at the time as `checks_original`, so a checker
correction is applied to every run alike and stays visible.
"""
import argparse
import json
import statistics as st
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MODELS = ["claude-opus-5-5", "claude-opus-5", "claude-fable-5-1"]
NAMES = {"claude-opus-5-5": "Opus 5.5", "claude-opus-5": "Opus 5", "claude-fable-5-1": "Fable 5.1"}


def regrade(row_path: Path, row: dict) -> dict:
    out = subprocess.run([str(ROOT / "tasks" / row["task"] / "check.sh"), row["workdir"]],
                         capture_output=True, text=True).stdout
    checks = json.loads(out[out.index("{"):])
    row.setdefault("checks_original", row["checks"])
    passed = [v for v in checks.values() if isinstance(v, bool)]
    row.update(checks=checks, checks_passed=sum(passed), checks_total=len(passed),
               all_passed=bool(passed) and all(passed))
    row_path.write_text(json.dumps(row, indent=1))
    return row


def spread(values: list[float], fmt: str) -> str:
    return f"{fmt.format(st.median(values))} ({fmt.format(min(values))} to {fmt.format(max(values))})"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--regrade", action="store_true")
    ap.add_argument("--out", default="results/tasks")
    args = ap.parse_args()
    base = ROOT / args.out
    rows = []
    for p in sorted(base.glob("T*/*-r[0-9].json")):
        row = json.loads(p.read_text())
        rows.append(regrade(p, row) if args.regrade else row)

    lines = ["# Experiments B and C: results", "",
             "Every run, then the median and range per task and model. Cost is the",
             "calculated API list-price equivalent: tokens by category times the",
             "published rates. Nobody paid per token; the runs used a subscription.", "",
             "## Every run", "",
             "| Task | Model | Repeat | Order | Checks | Wall s | Turns | Tool calls | Denials | Cost $ | CC total_cost_usd | Leak |",
             "|---|---|---|---|---|---|---|---|---|---|---|---|"]
    for r in sorted(rows, key=lambda r: (r["task"], MODELS.index(r["model"]), r["repeat"])):
        c = r["cost"] or {}
        lines.append(f"| {r['task']} | {NAMES[r['model']]} | {r['repeat']} | {r['order_in_repeat']} "
                     f"| {r['checks_passed']}/{r['checks_total']} | {r['wall_s']} | {r['num_turns']} "
                     f"| {r['tool_calls']} | {r['permission_denials']} | {c.get('calculated_usd')} "
                     f"| {c.get('claude_code_total_cost_usd')} | {', '.join(r['possible_leak']) or '-'} |")

    lines += ["", "## Median (range) per task and model", "",
              "| Task | Model | All checks passed | Wall s | Tool calls | Output tokens | Cost $ |",
              "|---|---|---|---|---|---|---|"]
    for task in sorted({r["task"] for r in rows}):
        for m in MODELS:
            rs = [r for r in rows if r["task"] == task and r["model"] == m]
            if not rs:
                continue
            out_tokens = [sum(v["output"] for v in r["cost"]["by_model"].values()) for r in rs]
            lines.append(f"| {task} | {NAMES[m]} | {sum(r['all_passed'] for r in rs)} of {len(rs)} "
                         f"| {spread([r['wall_s'] for r in rs], '{:.0f}')} "
                         f"| {spread([r['tool_calls'] for r in rs], '{:.0f}')} "
                         f"| {spread(out_tokens, '{:.0f}')} "
                         f"| {spread([r['cost']['calculated_usd'] for r in rs], '{:.2f}')} |")

    lines += ["", "## All three tasks together, per model", "",
              "| Model | Runs with every check passed | Total wall s | Total cost $ |", "|---|---|---|---|"]
    for m in MODELS:
        rs = [r for r in rows if r["model"] == m]
        if rs:
            lines.append(f"| {NAMES[m]} | {sum(r['all_passed'] for r in rs)} of {len(rs)} "
                         f"| {sum(r['wall_s'] for r in rs):.0f} | {sum(r['cost']['calculated_usd'] for r in rs):.2f} |")
    changed = [r for r in rows if r.get("checks_original") and r["checks_original"] != r["checks"]]
    lines += ["", f"Runs whose grade changed on regrading: {len(changed)}"]
    for r in changed:
        diff = {k: (r["checks_original"].get(k), v) for k, v in r["checks"].items()
                if r["checks_original"].get(k) != v}
        lines.append(f"- {r['task']} {NAMES[r['model']]} r{r['repeat']}: {diff}")

    (base / "SUMMARY.md").write_text("\n".join(lines) + "\n")
    (base / "rows.json").write_text(json.dumps(rows, indent=1))
    print("\n".join(lines))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
