# Opus 5.5, tested in Claude Code

A reproducible test of three claims Anthropic made for Claude Opus 5.5 on
22 September 2026, run in Claude Code on 27 September 2026. It goes with the
Code with Sam video "Opus 5.5 in 7 Minutes: 27 Real Claude Code Runs".

The claims, verbatim, from the launch page:

1. "generates output more than 30% faster than Opus 5"
2. "at default settings it will cost 40% less than Opus 5 on typical workloads"
3. "performs at the level of Claude Fable 5.1 on most work"

## What was measured

**A. Generation.** No tools, one fixed long answer, ten runs per model, order
alternated, every streamed chunk timestamped.
Result: `results/speed/SUMMARY.md`.

| | Opus 5.5 | Opus 5 |
|---|---|---|
| Text tokens per second, median | 159.9 | 136.0 |
| Thinking included | 161.4 | 127.8 |
| Time to first text, median | 3.45 s | 11.12 s |

**B and C. Three coding jobs.** Taken from frozen commits of public
repositories and renamed, so no model can lean on the published code:

| Task | Job | Graded by |
|---|---|---|
| T1 | a list endpoint with a currency filter in a Go service | held-out Go tests, vet, SQL checks |
| T2 | a batched GraphQL field and a paginated query with a NOT_FOUND error, Spring for GraphQL | held-out tests on real Postgres with a statement counter |
| T3 | bounded retry with backoff and a dead letter topic, Spring Kafka | held-out tests on an embedded broker |

Three runs per model per task, order rotated, the same Bash allowlist for every
model, effort never set. Result: `results/tasks/SUMMARY.md`.

| All three jobs, nine runs each | Every check passed | Total wall time | API list price equivalent |
|---|---|---|---|
| Opus 5.5 | 9 of 9 | 1,341 s | $4.91 |
| Opus 5 | 9 of 9 | 4,823 s | $16.47 |
| Fable 5.1 | 9 of 9 | 2,991 s | $20.18 |

## What it does not show

It does not show the 30% claim is false: Anthropic does not publish its method.
It does not verify the 40%: that is a typical mix of work, and these are three
backend jobs. It does not verify Fable level quality: every model passed every
check, so the tests could not separate them. It measured speed and price on jobs
all three models solved.

## Rerun it

Needs Claude Code, Docker, Python 3, and JDK 25 for T2 and T3 (Go runs in its
official container).

```bash
python3 harness/run_speed.py --repeats 10 --out results/speed
python3 harness/run_tasks.py --out results/tasks
python3 harness/summarise.py --regrade
```

`run_tasks.py` copies each task's `base` to a scratch directory, runs Claude
Code headless with the task's `prompt.md`, then grades the copy with
`tasks/<task>/check.sh`. The model never sees `checks/`.

Costs are tokens by category (uncached input, cache writes, cache reads, output)
times the published per-million rates. On a subscription nobody pays per token;
the figure is what the same work costs at API list prices, and it matched Claude
Code's own `total_cost_usd` to the cent on every run.

## Disclosed corrections

- One T1 run first graded 7 of 8: the parameterised SQL check rejected any use
  of `Sprintf`, even where it only numbered a placeholder. The check was
  corrected once (`tasks/T1/checks/param_check.py`) and every saved solution was
  re-graded. Only that grade changed.
- One Opus 5 speed run gave no comparable output (with no tools it wrote a
  script and an invented output). It is kept in `results/speed` and left out of
  the speed figures; that rule was set after seeing it.
- One T1 run before the series began is in `results/discarded`: the task still
  named the published repository. It is not counted.

`reference/` holds the reference solutions used to prove every checker passes a
correct answer. The discriminating checks were also run against deliberately
wrong solutions before any model run: a one-query-per-customer count in T2 (34
statements against a limit of 3) and Spring's default error handler in T3 (10
attempts where 4 and 1 are required). Both failed exactly the check meant to
catch them.

Versions and prices change. Everything here was true on 27 September 2026.
