// Command charges serves the same API as spring-charges with the standard
// library's net/http and database/sql, and the pgx driver for Postgres.
// Everything a Spring container would wire is wired here, by hand, in main.
package main

import (
	"context"
	"database/sql"
	"errors"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	_ "github.com/jackc/pgx/v5/stdlib"
)

func main() {
	db, err := sql.Open("pgx", env("DATABASE_URL",
		"postgres://charges:charges@localhost:5442/charges"))
	if err != nil {
		log.Fatal(err)
	}
	db.SetMaxOpenConns(4)
	if err := migrate(db); err != nil {
		log.Fatal(err)
	}

	// Bottom up: each constructor asks for what it needs.
	store := NewStore(db)
	receipts := NewReceipts(db)
	charges := NewChargeService(store)
	handlers := NewHandlers(charges, receipts)

	mux := http.NewServeMux()
	mux.HandleFunc("GET /charges/{id}", handlers.get)
	mux.HandleFunc("GET /charges/latest", handlers.latest)
	mux.HandleFunc("POST /charges", handlers.create)
	mux.HandleFunc("GET /reports/slow", handlers.slow)

	srv := &http.Server{
		Addr:              ":" + env("PORT", "8093"),
		Handler:           logRequests(mux),
		ReadHeaderTimeout: 5 * time.Second,
		IdleTimeout:       60 * time.Second,
	}
	serve(srv, receipts)
}

// serve runs until SIGTERM or SIGINT, stops accepting new requests, lets the
// in-flight ones finish, then waits for receipts still being sent.
func serve(srv *http.Server, receipts *Receipts) {
	ctx, stop := signal.NotifyContext(context.Background(),
		os.Interrupt, syscall.SIGTERM)
	defer stop()
	go func() {
		err := srv.ListenAndServe()
		if err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Fatal(err)
		}
	}()
	log.Printf("listening on %s", srv.Addr)
	<-ctx.Done()
	log.Print("shutting down, waiting for in-flight requests")
	shutdown, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()
	if err := srv.Shutdown(shutdown); err != nil {
		log.Print(err)
	}
	// Server.Shutdown knows about HTTP connections, not about goroutines this
	// application started. Those are ours to wait for, inside the same bound.
	if err := receipts.Wait(shutdown); err != nil {
		log.Printf("receipts still in flight: %v", err)
	}
	log.Print("stopped")
}

// logRequests is middleware: a function that wraps a handler. Authentication,
// correlation IDs and metrics compose the same way.
func logRequests(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		next.ServeHTTP(w, r)
		log.Printf("%s %s %s", r.Method, r.URL.Path,
			time.Since(start).Round(time.Millisecond))
	})
}

func env(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
