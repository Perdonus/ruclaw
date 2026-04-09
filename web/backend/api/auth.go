package api

import (
	"context"
	"crypto/subtle"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"

	"github.com/Perdonus/ruclaw/web/backend/middleware"
)

// PasswordStore is the interface for bcrypt-backed dashboard password persistence.
// Implemented by dashboardauth.Store; a nil value falls back to the legacy
// static-token comparison.
type PasswordStore interface {
	IsInitialized(ctx context.Context) (bool, error)
	SetPassword(ctx context.Context, plain string) error
	VerifyPassword(ctx context.Context, plain string) (bool, error)
}

// LauncherAuthRouteOpts configures dashboard auth handlers.
type LauncherAuthRouteOpts struct {
	// DashboardToken is the fallback plaintext token used when PasswordStore is
	// nil or not yet initialized (env-var / config-file source, and ?token= auto-login).
	DashboardToken string
	SessionCookie  string
	SecureCookie   func(*http.Request) bool
	// PasswordStore enables bcrypt-backed password persistence. When non-nil and
	// initialized, web-form login verifies against the stored hash instead of
	// the plaintext DashboardToken.
	PasswordStore PasswordStore
	// StoreError holds the error returned when opening the password store. When
	// non-nil and PasswordStore is nil, the auth endpoints surface a recovery
	// message instead of an opaque 501/503.
	StoreError error
}

type launcherAuthLoginBody struct {
	Password string `json:"password"`
}

type launcherAuthSetupBody struct {
	Password string `json:"password"`
	Confirm  string `json:"confirm"`
}

type launcherAuthStatusResponse struct {
	Authenticated bool `json:"authenticated"`
	Initialized   bool `json:"initialized"`
}

// RegisterLauncherAuthRoutes registers /api/auth/login|logout|status|setup.
func RegisterLauncherAuthRoutes(mux *http.ServeMux, opts LauncherAuthRouteOpts) {
	secure := opts.SecureCookie
	if secure == nil {
		secure = middleware.DefaultLauncherDashboardSecureCookie
	}
	h := &launcherAuthHandlers{
		token:         opts.DashboardToken,
		sessionCookie: opts.SessionCookie,
		secureCookie:  secure,
		store:         opts.PasswordStore,
		storeErr:      opts.StoreError,
		loginLimit:    newLoginRateLimiter(),
	}
	mux.HandleFunc("POST /api/auth/login", h.handleLogin)
	mux.HandleFunc("POST /api/auth/logout", h.handleLogout)
	mux.HandleFunc("GET /api/auth/status", h.handleStatus)
	mux.HandleFunc("POST /api/auth/setup", h.handleSetup)
}

type launcherAuthHandlers struct {
	token         string
	sessionCookie string
	secureCookie  func(*http.Request) bool
	store         PasswordStore
	storeErr      error // set when the store failed to open; drives recovery messages
	loginLimit    *loginRateLimiter
}

// isStoreInitialized safely queries the store.
// Returns (false, nil) when no store is configured (storeErr also nil).
// Returns (false, err) on store errors — callers must treat this as a 5xx, not as
// "uninitialized", to keep auth fail-closed.
// Exception: handleLogin swallows storeErr and falls back to token auth so
// that a corrupt DB does not lock out all access.
func (h *launcherAuthHandlers) isStoreInitialized(ctx context.Context) (bool, error) {
	if h.store == nil {
		if h.storeErr != nil {
			return false, fmt.Errorf(TranslateLauncher(AuthPasswordStoreUnavailable), h.storeErr)
		}
		return false, nil
	}
	return h.store.IsInitialized(ctx)
}

func (h *launcherAuthHandlers) handleLogin(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	var body launcherAuthLoginBody
	if err := json.NewDecoder(http.MaxBytesReader(w, r.Body, 1<<20)).Decode(&body); err != nil {
		w.WriteHeader(http.StatusBadRequest)
		writeErrorKey(w, AuthInvalidJSON)
		return
	}
	ip := clientIPForLimiter(r)
	if !h.loginLimit.allow(ip) {
		w.WriteHeader(http.StatusTooManyRequests)
		writeErrorKey(w, AuthTooManyLoginAttempts)
		return
	}
	in := strings.TrimSpace(body.Password)
	var ok bool

	initialized, initErr := h.isStoreInitialized(r.Context())
	if initErr != nil {
		if h.storeErr != nil {
			// Store failed to open at startup — token login remains available.
			initialized = false
		} else {
			w.WriteHeader(http.StatusInternalServerError)
			writeErrorMessage(w, fmt.Sprintf("%v", initErr))
			return
		}
	}

	if initialized {
		// Bcrypt path: verify against the stored hash.
		var err error
		ok, err = h.store.VerifyPassword(r.Context(), in)
		if err != nil {
			w.WriteHeader(http.StatusInternalServerError)
			writeErrorKey(w, AuthPasswordVerifyFailed, err)
			return
		}
	} else {
		// Fallback: constant-time compare against the plaintext token.
		ok = len(in) == len(h.token) &&
			subtle.ConstantTimeCompare([]byte(in), []byte(h.token)) == 1
	}

	if !ok {
		w.WriteHeader(http.StatusUnauthorized)
		writeErrorKey(w, AuthInvalidPassword)
		return
	}

	middleware.SetLauncherDashboardSessionCookie(w, r, h.sessionCookie, h.secureCookie)
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write([]byte(`{"status":"ok"}`))
}

func (h *launcherAuthHandlers) handleLogout(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	if r.Method != http.MethodPost {
		w.WriteHeader(http.StatusMethodNotAllowed)
		writeErrorKey(w, AuthMethodNotAllowed)
		return
	}
	ct := strings.ToLower(strings.TrimSpace(r.Header.Get("Content-Type")))
	if !strings.HasPrefix(ct, "application/json") {
		w.WriteHeader(http.StatusUnsupportedMediaType)
		writeErrorKey(w, AuthContentTypeJSONRequired)
		return
	}
	dec := json.NewDecoder(http.MaxBytesReader(w, r.Body, logoutBodyMaxBytes))
	if err := dec.Decode(&struct{}{}); err != nil && err != io.EOF {
		w.WriteHeader(http.StatusBadRequest)
		writeErrorKey(w, AuthInvalidJSONBody)
		return
	}
	if err := dec.Decode(&struct{}{}); err != io.EOF {
		w.WriteHeader(http.StatusBadRequest)
		writeErrorKey(w, AuthInvalidJSONBody)
		return
	}

	middleware.ClearLauncherDashboardSessionCookie(w, r, h.secureCookie)
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write([]byte(`{"status":"ok"}`))
}

func (h *launcherAuthHandlers) handleStatus(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	authed := false
	if c, err := r.Cookie(middleware.LauncherDashboardCookieName); err == nil {
		authed = subtle.ConstantTimeCompare([]byte(c.Value), []byte(h.sessionCookie)) == 1
	}
	initialized, initErr := h.isStoreInitialized(r.Context())
	if initErr != nil {
		w.WriteHeader(http.StatusServiceUnavailable)
		writeErrorMessage(w, initErr.Error())
		return
	}
	resp := launcherAuthStatusResponse{
		Authenticated: authed,
		Initialized:   initialized,
	}
	enc, err := json.Marshal(resp)
	if err != nil {
		w.WriteHeader(http.StatusInternalServerError)
		writeErrorKey(w, AuthMarshalResponseFailed, err)
		return
	}
	_, _ = w.Write(enc)
}

// handleSetup sets or changes the dashboard password.
//
// Rules:
//   - If the store has no password yet, the endpoint is open (no session required).
//   - If a password is already set, the caller must hold a valid session cookie.
func (h *launcherAuthHandlers) handleSetup(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")

	if h.store == nil {
		w.WriteHeader(http.StatusNotImplemented)
		writeErrorKey(w, AuthPasswordStoreNotConfigured)
		return
	}

	initialized, initErr := h.isStoreInitialized(r.Context())
	if initErr != nil {
		w.WriteHeader(http.StatusServiceUnavailable)
		writeErrorMessage(w, initErr.Error())
		return
	}

	// If already initialized, require an active session (change-password flow).
	if initialized {
		authed := false
		if c, err := r.Cookie(middleware.LauncherDashboardCookieName); err == nil {
			authed = subtle.ConstantTimeCompare([]byte(c.Value), []byte(h.sessionCookie)) == 1
		}
		if !authed {
			w.WriteHeader(http.StatusUnauthorized)
			writeErrorKey(w, AuthMustAuthenticateToChange)
			return
		}
	}

	var body launcherAuthSetupBody
	if err := json.NewDecoder(http.MaxBytesReader(w, r.Body, 1<<20)).Decode(&body); err != nil {
		w.WriteHeader(http.StatusBadRequest)
		writeErrorKey(w, AuthInvalidJSON)
		return
	}

	pw := strings.TrimSpace(body.Password)
	if pw == "" {
		w.WriteHeader(http.StatusBadRequest)
		writeErrorKey(w, AuthPasswordEmpty)
		return
	}
	if pw != strings.TrimSpace(body.Confirm) {
		w.WriteHeader(http.StatusBadRequest)
		writeErrorKey(w, AuthPasswordsDoNotMatch)
		return
	}
	if len([]rune(pw)) < 8 {
		w.WriteHeader(http.StatusBadRequest)
		writeErrorKey(w, AuthPasswordTooShort)
		return
	}

	if err := h.store.SetPassword(r.Context(), pw); err != nil {
		w.WriteHeader(http.StatusInternalServerError)
		writeErrorKey(w, AuthSavePasswordFailed, err)
		return
	}

	w.WriteHeader(http.StatusOK)
	_, _ = w.Write([]byte(`{"status":"ok"}`))
}

// writeErrorMessage writes a JSON error response with a message string.
func writeErrorMessage(w http.ResponseWriter, message string) {
	msg, _ := json.Marshal(message)
	_, _ = w.Write([]byte(`{"error":` + string(msg) + `}`))
}

func writeErrorKey(w http.ResponseWriter, key TranslationKey, args ...any) {
	writeErrorMessage(w, TranslateLauncher(key, args...))
}
