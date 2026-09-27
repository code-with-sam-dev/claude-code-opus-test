package main

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
)

type Charge struct {
	ID       int64  `json:"id"`
	Amount   int64  `json:"amount"`
	Currency string `json:"currency"`
	Status   string `json:"status"`
}

type NewCharge struct {
	Amount   int64  `json:"amount"`
	Currency string `json:"currency"`
}

var ErrNotFound = errors.New("charge not found")

// Store is the repository: plain SQL, no mapping layer.
type Store struct{ db *sql.DB }

func NewStore(db *sql.DB) *Store { return &Store{db: db} }

func migrate(db *sql.DB) error {
	_, err := db.Exec(`
		CREATE TABLE IF NOT EXISTS charges (
			id       BIGSERIAL PRIMARY KEY,
			amount   BIGINT NOT NULL,
			currency TEXT   NOT NULL,
			status   TEXT   NOT NULL);
		CREATE TABLE IF NOT EXISTS audit (
			id         BIGSERIAL PRIMARY KEY,
			charge_id BIGINT NOT NULL,
			note       TEXT   NOT NULL);
		CREATE TABLE IF NOT EXISTS receipts (
			id         BIGSERIAL PRIMARY KEY,
			charge_id BIGINT NOT NULL)`)
	return err
}

func (s *Store) Get(ctx context.Context, id int64) (Charge, error) {
	return s.one(ctx, `SELECT id, amount, currency, status FROM charges
		WHERE id = $1`, id)
}

func (s *Store) Latest(ctx context.Context) (Charge, error) {
	return s.one(ctx, `SELECT id, amount, currency, status FROM charges
		ORDER BY id DESC LIMIT 1`)
}

func (s *Store) one(ctx context.Context, query string, args ...any) (Charge, error) {
	var p Charge
	err := s.db.QueryRowContext(ctx, query, args...).
		Scan(&p.ID, &p.Amount, &p.Currency, &p.Status)
	if errors.Is(err, sql.ErrNoRows) {
		return p, ErrNotFound
	}
	if err != nil {
		return p, fmt.Errorf("load charge: %w", err)
	}
	return p, nil
}

// Sleep stands in for a report query that takes seconds.
func (s *Store) Sleep(ctx context.Context, seconds int) error {
	_, err := s.db.ExecContext(ctx, `SELECT pg_sleep($1)`, seconds)
	return err
}
