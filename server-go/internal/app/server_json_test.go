package app

import (
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestDecodeJSONBodyLimitedAcceptsSmallBody(t *testing.T) {
	req := httptest.NewRequest(http.MethodPost, "/test", strings.NewReader(`{"order_id":"o1"}`))

	var body orderRequest
	if err := decodeJSONBodyLimited(req, &body, 64); err != nil {
		t.Fatalf("decodeJSONBodyLimited: %v", err)
	}
	if body.OrderID != "o1" {
		t.Fatalf("OrderID = %q, want o1", body.OrderID)
	}
}

func TestDecodeJSONBodyLimitedRejectsTooLargeBody(t *testing.T) {
	req := httptest.NewRequest(http.MethodPost, "/test", strings.NewReader(`{"order_id":"`+strings.Repeat("x", 80)+`"}`))

	var body orderRequest
	err := decodeJSONBodyLimited(req, &body, 32)
	if !errors.Is(err, errJSONBodyTooLarge) {
		t.Fatalf("decodeJSONBodyLimited error = %v, want errJSONBodyTooLarge", err)
	}
}

func TestDecodeJSONBodyLimitedRejectsTrailingData(t *testing.T) {
	req := httptest.NewRequest(http.MethodPost, "/test", strings.NewReader(`{"order_id":"o1"} {"order_id":"o2"}`))

	var body orderRequest
	err := decodeJSONBodyLimited(req, &body, 128)
	if !errors.Is(err, errJSONBodyTrailingData) {
		t.Fatalf("decodeJSONBodyLimited error = %v, want errJSONBodyTrailingData", err)
	}
}

func TestWriteJSONDecodeErrorUsesTooLargeStatus(t *testing.T) {
	server := &Server{}
	recorder := httptest.NewRecorder()

	server.writeJSONDecodeError(recorder, errJSONBodyTooLarge)

	if recorder.Code != http.StatusRequestEntityTooLarge {
		t.Fatalf("status = %d, want %d", recorder.Code, http.StatusRequestEntityTooLarge)
	}
	if !strings.Contains(recorder.Body.String(), "body_too_large") {
		t.Fatalf("body = %q, want body_too_large", recorder.Body.String())
	}
}

func TestWriteJSONDecodeErrorUsesTooLargeStatusForMaxBytesError(t *testing.T) {
	server := &Server{}
	recorder := httptest.NewRecorder()

	server.writeJSONDecodeError(recorder, &http.MaxBytesError{Limit: 8})

	if recorder.Code != http.StatusRequestEntityTooLarge {
		t.Fatalf("status = %d, want %d", recorder.Code, http.StatusRequestEntityTooLarge)
	}
	if !strings.Contains(recorder.Body.String(), "body_too_large") {
		t.Fatalf("body = %q, want body_too_large", recorder.Body.String())
	}
}
