#!/usr/bin/env python3
"""Experiment A: generation speed, Opus 5.5 against Opus 5.

Anthropic's claim is about generation speed ("generates output more than 30%
faster than Opus 5"), so this run gives the model no tools and one long,
fixed-format answer, and times the stream itself: when each chunk of text
arrives. Output tokens per second is the reported output token count divided by
the time between the first and the last text chunk. Time to first token is
reported separately and never mixed in.

    python3 harness/run_speed.py --repeats 10 --out results/speed

Every raw stream is kept, one file per run, so any number can be recomputed.
"""
import argparse
import json
import subprocess
import sys
import tempfile
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
MODELS = ["claude-opus-5-5", "claude-opus-5"]
PROMPT = (
    "Write the whole numbers from one to four hundred as English words, one per "
    "line, in order, lowercase, with no other text before or after."
)


def run_once(model: str, raw: Path) -> dict:
    cmd = [
        "claude", "-p", PROMPT, "--model", model,
        "--tools", "",
        "--settings", str(HERE / "settings.json"),
        "--mcp-config", str(HERE / "no-mcp.json"), "--strict-mcp-config",
        "--disable-slash-commands", "--no-chrome",
        "--output-format", "stream-json", "--verbose", "--include-partial-messages",
    ]
    start = time.monotonic()
    first = last = None
    result = None
    with tempfile.TemporaryDirectory() as cwd, raw.open("w") as log:
        proc = subprocess.Popen(cmd, cwd=cwd, stdin=subprocess.DEVNULL,
                                stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
        for line in proc.stdout:
            now = time.monotonic() - start
            log.write(json.dumps({"t": round(now, 4), "line": line.rstrip("\n")}) + "\n")
            try:
                event = json.loads(line)
            except json.JSONDecodeError:
                continue
            inner = event.get("event", {}) if event.get("type") == "stream_event" else {}
            if inner.get("type") == "content_block_delta" and inner.get("delta", {}).get("type") == "text_delta":
                first = now if first is None else first
                last = now
            if event.get("type") == "result":
                result = event
        proc.wait()
    if result is None or first is None or last is None or last <= first:
        raise SystemExit(f"{model}: no usable stream in {raw}")
    usage = result["usage"]
    out = usage["output_tokens"]
    return {
        "model": model,
        "fast_mode": result.get("usage", {}).get("speed"),
        "output_tokens": out,
        "thinking_tokens": usage.get("output_tokens_details", {}).get("thinking_tokens", 0),
        "time_to_first_text_s": round(first, 3),
        "streaming_s": round(last - first, 3),
        # Thinking streams before the text, so only text tokens are timed.
        "text_tokens_per_s": round((out - usage.get("output_tokens_details", {}).get("thinking_tokens", 0))
                                   / (last - first), 1),
        "wall_s": round(result["duration_ms"] / 1000, 3),
        "raw": raw.name,
    }


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--repeats", type=int, default=10)
    ap.add_argument("--out", default="results/speed")
    args = ap.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    rows = []
    for r in range(args.repeats):
        order = MODELS if r % 2 == 0 else MODELS[::-1]  # which model goes first alternates
        for model in order:
            row = run_once(model, out / f"r{r + 1:02d}-{model}.jsonl")
            row["repeat"] = r + 1
            rows.append(row)
            print(json.dumps(row), flush=True)
    (out / "speed.json").write_text(json.dumps(rows, indent=1))
    return 0


if __name__ == "__main__":
    sys.exit(main())
