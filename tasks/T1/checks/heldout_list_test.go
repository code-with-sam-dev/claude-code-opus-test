package main

// Held out from the model: copied in only after the run finishes.

import (
	"context"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

type heldOutCharges struct {
	called   bool
	currency string
}

func (h *heldOutCharges) Create(context.Context, NewCharge) (Charge, error) { return Charge{}, nil }
func (h *heldOutCharges) Get(context.Context, int64) (Charge, error)       { return Charge{}, nil }
func (h *heldOutCharges) Latest(context.Context) (Charge, error)           { return Charge{}, nil }
func (h *heldOutCharges) Report(context.Context, int) error                { return nil }
func (h *heldOutCharges) List(_ context.Context, currency string) ([]Charge, error) {
	h.called, h.currency = true, currency
	return nil, nil
}

type heldOutReceipts struct{}

func (heldOutReceipts) SendAsync(context.Context, Charge) {}

func heldOutList(t *testing.T, url string) (*heldOutCharges, *httptest.ResponseRecorder) {
	t.Helper()
	fake := &heldOutCharges{}
	h := NewHandlers(fake, heldOutReceipts{})
	rec := httptest.NewRecorder()
	h.list(rec, httptest.NewRequest(http.MethodGet, url, nil))
	return fake, rec
}

func TestHeldOutLowercaseCurrencyIsNormalised(t *testing.T) {
	fake, rec := heldOutList(t, "/charges?currency=usd")
	if rec.Code != http.StatusOK || !fake.called || fake.currency != "USD" {
		t.Fatalf("code=%d called=%v currency=%q", rec.Code, fake.called, fake.currency)
	}
}

func TestHeldOutNoFilterReturnsEmptyArray(t *testing.T) {
	fake, rec := heldOutList(t, "/charges")
	body := strings.TrimSpace(rec.Body.String())
	if rec.Code != http.StatusOK || fake.currency != "" || body != "[]" {
		t.Fatalf("code=%d currency=%q body=%q", rec.Code, fake.currency, body)
	}
}

func TestHeldOutTwoLettersIsRejected(t *testing.T) {
	fake, rec := heldOutList(t, "/charges?currency=US")
	if rec.Code != http.StatusBadRequest || fake.called {
		t.Fatalf("code=%d called=%v", rec.Code, fake.called)
	}
}

func TestHeldOutSymbolIsRejected(t *testing.T) {
	fake, rec := heldOutList(t, "/charges?currency=u%24d")
	if rec.Code != http.StatusBadRequest || fake.called {
		t.Fatalf("code=%d called=%v", rec.Code, fake.called)
	}
}
