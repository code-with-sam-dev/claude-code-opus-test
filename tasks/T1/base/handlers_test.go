package main

import (
	"context"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

// fakeCharges satisfies Charges with no database and no container.
type fakeCharges struct{ created []NewCharge }

func (f *fakeCharges) Create(_ context.Context, in NewCharge) (Charge, error) {
	f.created = append(f.created, in)
	return Charge{ID: 1, Amount: in.Amount, Currency: in.Currency}, nil
}

func (f *fakeCharges) Get(context.Context, int64) (Charge, error) {
	return Charge{}, nil
}

func (f *fakeCharges) Latest(context.Context) (Charge, error) {
	return Charge{}, nil
}

func (f *fakeCharges) Report(context.Context, int) error { return nil }

type noReceipts struct{}

func (noReceipts) SendAsync(context.Context, Charge) {}

func TestNegativeAmountIsRejectedAndNotCreated(t *testing.T) {
	fake := &fakeCharges{}
	h := NewHandlers(fake, noReceipts{})
	req := httptest.NewRequest(http.MethodPost, "/charges",
		strings.NewReader(`{"amount":-5,"currency":"USD"}`))
	rec := httptest.NewRecorder()

	h.create(rec, req)

	if rec.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want 400", rec.Code)
	}
	if len(fake.created) != 0 {
		t.Fatalf("Create was called %d time(s) for a rejected charge",
			len(fake.created))
	}
}

func TestPositiveAmountIsCreated(t *testing.T) {
	fake := &fakeCharges{}
	h := NewHandlers(fake, noReceipts{})
	req := httptest.NewRequest(http.MethodPost, "/charges",
		strings.NewReader(`{"amount":500,"currency":"USD"}`))
	rec := httptest.NewRecorder()

	h.create(rec, req)

	if rec.Code != http.StatusCreated || len(fake.created) != 1 {
		t.Fatalf("status = %d, created = %d", rec.Code, len(fake.created))
	}
}
