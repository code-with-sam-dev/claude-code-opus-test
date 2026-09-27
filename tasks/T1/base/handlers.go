package main

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"strconv"
	"time"
)

// Charges is what the handlers need, declared where it is used. Anything
// with these methods satisfies it, with no implements clause: the service in
// main, and a fake in the tests.
type Charges interface {
	Create(ctx context.Context, in NewCharge) (Charge, error)
	Get(ctx context.Context, id int64) (Charge, error)
	Latest(ctx context.Context) (Charge, error)
	Report(ctx context.Context, seconds int) error
}

type ReceiptSender interface {
	SendAsync(ctx context.Context, p Charge)
}

type Handlers struct {
	charges Charges
	receipts ReceiptSender
}

func NewHandlers(charges Charges, receipts ReceiptSender) *Handlers {
	if charges == nil || receipts == nil {
		panic("NewHandlers: a dependency is nil")
	}
	return &Handlers{charges: charges, receipts: receipts}
}

func (h *Handlers) create(w http.ResponseWriter, r *http.Request) {
	var in NewCharge
	dec := json.NewDecoder(r.Body)
	dec.DisallowUnknownFields()
	if err := dec.Decode(&in); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	if in.Amount <= 0 {
		http.Error(w, "amount must be positive", http.StatusBadRequest)
		return
	}
	p, err := h.charges.Create(r.Context(), in)
	if errors.Is(err, ErrOverLimit) {
		http.Error(w, err.Error(), http.StatusUnprocessableEntity)
		return
	}
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	// The receipt outlives the request, so it must not inherit the request's
	// cancellation. WithoutCancel keeps its values and drops its lifetime.
	h.receipts.SendAsync(context.WithoutCancel(r.Context()), p)
	writeJSON(w, http.StatusCreated, p)
}

func (h *Handlers) get(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.ParseInt(r.PathValue("id"), 10, 64)
	if err != nil {
		http.Error(w, "id must be a number", http.StatusBadRequest)
		return
	}
	h.respond(w, r, func(ctx context.Context) (Charge, error) {
		return h.charges.Get(ctx, id)
	})
}

func (h *Handlers) latest(w http.ResponseWriter, r *http.Request) {
	h.respond(w, r, h.charges.Latest)
}

func (h *Handlers) respond(w http.ResponseWriter, r *http.Request,
	load func(context.Context) (Charge, error)) {
	p, err := load(r.Context())
	if errors.Is(err, ErrNotFound) {
		http.Error(w, err.Error(), http.StatusNotFound)
		return
	}
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	writeJSON(w, http.StatusOK, p)
}

// slow passes the request's context to the database, so a client that gives
// up cancels the query too.
func (h *Handlers) slow(w http.ResponseWriter, r *http.Request) {
	seconds, _ := strconv.Atoi(r.URL.Query().Get("seconds"))
	start := time.Now()
	if err := h.charges.Report(r.Context(), seconds); err != nil {
		http.Error(w, err.Error(), http.StatusServiceUnavailable)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"slept": time.Since(start).Round(time.Second).String()})
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(v)
}
