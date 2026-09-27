package main

import (
	"context"
	"errors"
	"fmt"
)

var ErrOverLimit = errors.New("amount over the single charge limit")

const limit = 1_000_000

// ChargeService owns the transaction, the way a @Transactional service would.
type ChargeService struct{ store *Store }

func NewChargeService(store *Store) *ChargeService {
	return &ChargeService{store: store}
}

func (s *ChargeService) Create(ctx context.Context, in NewCharge) (Charge, error) {
	tx, err := s.store.db.BeginTx(ctx, nil)
	if err != nil {
		return Charge{}, fmt.Errorf("begin: %w", err)
	}
	defer tx.Rollback() // harmless once Commit has succeeded

	p := Charge{Amount: in.Amount, Currency: in.Currency, Status: "CAPTURED"}
	err = tx.QueryRowContext(ctx,
		`INSERT INTO charges (amount, currency, status) VALUES ($1, $2, $3)
		 RETURNING id`, p.Amount, p.Currency, p.Status).Scan(&p.ID)
	if err != nil {
		return Charge{}, fmt.Errorf("insert charge: %w", err)
	}
	if p.Amount > limit {
		return Charge{}, ErrOverLimit
	}
	_, err = tx.ExecContext(ctx,
		`INSERT INTO audit (charge_id, note) VALUES ($1, 'captured')`, p.ID)
	if err != nil {
		return Charge{}, fmt.Errorf("audit charge %d: %w", p.ID, err)
	}
	return p, tx.Commit()
}

func (s *ChargeService) Get(ctx context.Context, id int64) (Charge, error) {
	return s.store.Get(ctx, id)
}

func (s *ChargeService) Latest(ctx context.Context) (Charge, error) {
	return s.store.Latest(ctx)
}

func (s *ChargeService) Report(ctx context.Context, seconds int) error {
	return s.store.Sleep(ctx, seconds)
}
