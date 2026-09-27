package main

import (
	"context"
	"database/sql"
	"log"
	"sync"
	"time"
)

// Receipts stands in for an email provider that takes a moment to answer.
type Receipts struct {
	db       *sql.DB
	inFlight sync.WaitGroup
}

func NewReceipts(db *sql.DB) *Receipts { return &Receipts{db: db} }

// SendAsync sends in the background. The caller decides which lifetime the
// work inherits: the ctx passed in is the one that can cancel it. Whatever it
// is, the send gets its own ten second bound, so detached work still ends.
func (r *Receipts) SendAsync(ctx context.Context, p Charge) {
	r.inFlight.Go(func() {
		ctx, cancel := context.WithTimeout(ctx, 10*time.Second)
		defer cancel()
		if err := r.send(ctx, p); err != nil {
			log.Printf("receipt for charge %d: %v", p.ID, err)
		}
	})
}

func (r *Receipts) send(ctx context.Context, p Charge) error {
	select {
	case <-time.After(200 * time.Millisecond): // the provider's round trip
	case <-ctx.Done():
		return ctx.Err()
	}
	_, err := r.db.ExecContext(ctx,
		`INSERT INTO receipts (charge_id) VALUES ($1)`, p.ID)
	return err
}

// Wait blocks until every receipt already started has finished, or until ctx
// ends, whichever comes first. Shutdown must not be able to hang forever.
func (r *Receipts) Wait(ctx context.Context) error {
	done := make(chan struct{})
	go func() {
		r.inFlight.Wait()
		close(done)
	}()
	select {
	case <-done:
		return nil
	case <-ctx.Done():
		return ctx.Err()
	}
}
