# Experiment A: generation speed, Opus 5.5 against Opus 5

Run 27 September 2026, Claude Code headless, no tools, fast mode off in every
run (`usage.speed` = standard), 10 repeats per model, order alternated.
Prompt: the numbers one to four hundred as words, one per line.

| | Opus 5.5 | Opus 5 |
|---|---|---|
| Text tokens per second, median (range) | 159.9 (159.2 to 163.5) | 136.0 (135.9 to 136.3), 9 valid runs |
| All output tokens per second, thinking included, median | 161.4 | 127.8 (9 valid runs) |
| Time to first text, median | 3.45 s | 11.12 s (9 valid runs) |
| Thinking tokens, median (range) | 64 (56 to 203) | 673 (479 to 1,113), 9 valid runs |
| Text tokens, typical | about 3,390 | about 3,390 |

Ratio of medians, text streaming: 1.18. Every paired repeat gives 1.17 to 1.20.
Thinking included: 1.26. Neither reaches the claimed "more than 30%" on this
prompt; how the claim is measured by Anthropic is not stated on the page.

Run 6 of Opus 5 is recorded but not a speed sample: with no tools it wrote a
shell script and an invented, truncated "output" instead of the list
(1,343 output tokens, 110 s). Including it leaves the median at 136.0.

Raw streams: `r01..r10-<model>.jsonl`; rows: `speed.json`.
