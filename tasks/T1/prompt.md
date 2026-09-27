Add a list endpoint to this Go service.

- `GET /charges` returns the charges as a JSON array, ordered by id ascending,
  at most 50. With no charges it returns `[]`, never `null`.
- An optional `currency` query parameter filters by currency. It must be three
  letters A to Z in any case and is normalised to upper case (`usd` means `USD`).
  Anything else is a 400 and nothing is queried.
- Add `List(ctx context.Context, currency string) ([]Charge, error)` to the
  `Charges` interface and implement it on `ChargeService` and `Store`. An empty
  currency means no filter.
- The handler method is `list` on `Handlers`, registered as `GET /charges`.
- Keep the existing behaviour and tests passing. Use parameterised SQL.

Go is not installed here; `./go.sh` runs it in a container, so run
`./go.sh vet ./...` and `./go.sh test ./...` before you finish. Postgres is not
available; tests must not need it.
