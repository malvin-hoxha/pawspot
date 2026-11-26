# Browser storage and external requests

This document describes browser-facing behavior found in the current PawSpot
backend and frontend source. Framework-derived and deployment-dependent
details are identified separately from application-defined settings.

## Cookies

| Cookie | Purpose | Path | Lifetime | HttpOnly | Secure | SameSite |
|---|---|---|---|---|---|---|
| `accessToken` | Carries the access JWT used to authenticate API requests. | `/` (explicit application setting) | `Max-Age` 900 seconds / 15 minutes (explicit application setting from `jwt.access-expiration=900000`) | `true` (explicit) | Controlled by `auth.cookie.secure`; the checked-in value is `false` (explicit) | `Lax` (explicit) |
| `refreshToken` | Carries the rotating refresh JWT used by `/api/auth` refresh and logout endpoints. | `/api/auth` (explicit application setting) | `Max-Age` 604,800 seconds / 7 days on each issuance (explicit application setting from `jwt.refresh-expiration=604800000`) | `true` (explicit) | Controlled by `auth.cookie.secure`; the checked-in value is `false` (explicit) | `Lax` (explicit) |
| `XSRF-TOKEN` | Holds the CSRF token that Axios reads and sends in the `X-XSRF-TOKEN` header for protected requests. | Request context path; `/` when the application is deployed at the root context (Spring Security SPA default, not explicitly set by PawSpot) | Session cookie; no persistent `Max-Age` is set for a live token (Spring Security SPA default) | `false` (Spring Security SPA preset) | Derived from whether the request is secure (Spring Security default); not explicitly set by PawSpot | Not set by the reviewed Spring Security configuration or framework default; effective browser treatment requires verification |

The authentication cookie factory explicitly sets the attributes shown for
`accessToken` and `refreshToken`. Spring Security's `csrf.spa()` configuration
selects its cookie-based CSRF repository; the `XSRF-TOKEN` attributes marked as
defaults above come from Spring Security 7.1.1 rather than PawSpot cookie code.
The CSRF cookie name and matching header name are framework defaults that the
frontend also explicitly configures in Axios.

Cookie `Max-Age` and token validity are separate controls. Access JWTs are
issued with a 15-minute validity. A new login creates a refresh session with an
original seven-day expiration. Refresh rotation creates a new refresh JWT that
preserves that original session expiration, but the response issues a new
`refreshToken` cookie with another seven-day browser `Max-Age`. The cookie may
therefore remain in the browser after the rotated token and refresh session
have expired; cookie presence does not establish that a session is valid.

Logout, an invalid refresh attempt, and account deletion clear the two
authentication cookies by issuing them with `Max-Age=0` and their original
paths. Actual response headers and stored attributes remain production checks,
especially where a proxy changes the request scheme or context path.

## Browser storage

### Authentication change generation

The frontend uses the exact local storage key
`pawspot-auth-generation`. Its value is a randomly generated UUID, not an
access token, refresh token, user identifier, or authentication credential.
The value acts as a generation marker so a request can detect that
authentication changed while it was in progress.

The key is created or overwritten after a successful login, successful logout,
or successful account deletion, and when a refresh rejected with HTTP 401
causes the current authentication state to be invalidated. A successful token
refresh does not update it. The application does not remove the key and does
not define an expiration for it. It therefore remains until it is overwritten
or browser/site data is cleared.

### Cross-tab coordination

The frontend opens a `BroadcastChannel` named `pawspot-auth-state`. After
changing the generation marker it broadcasts an `AUTH_CHANGED` notification.
Receiving tabs cancel active queries, remove the current user's private-post
query, clear the cached current identity, and request the current identity
again. `BroadcastChannel` transports messages between live browsing contexts;
it is not persistent browser storage.

Authentication operations are serialized across tabs with the Web Locks API
under the lock name `pawspot-auth`. The lock coordinates login, refresh, and
logout work while those operations run. A Web Lock is not persistent storage
and does not hold an authentication credential.

### Query cache and other mechanisms

TanStack Query uses the `QueryClient`'s in-memory cache for query and mutation
state. The reviewed source configures a 30-second query `staleTime`, but no
TanStack Query persistence provider or storage persister is installed or
configured. Cache contents are not deliberately restored after a page reload.

No application use of `sessionStorage`, IndexedDB, the Cache API, or a service
worker was found in the reviewed frontend source. Apart from the cookies and
the single `localStorage` entry described above, no other application-defined
persistent browser storage mechanism was found.

## External requests

Nunito weights 600, 700, and 800 are imported from the `@fontsource/nunito`
package and bundled with the frontend for self-hosted delivery. The current
frontend source contains no Google Fonts stylesheet or font-host imports; those
imports have been removed, so loading these Nunito files does not require a
browser request to Google Fonts.

Uploaded listing images are stored by Cloudinary. The backend saves the
`secure_url` returned by Cloudinary and exposes that URL to the frontend; an
image rendered from that URL is delivered to the browser by Cloudinary rather
than from the PawSpot application origin. The exact production hostname and
requests must be confirmed from the returned URLs and browser network traffic.

No analytics or advertising integration was found in the reviewed application
source or declared frontend dependencies. This is a source review, not a
complete audit of production runtime requests, injected hosting scripts, proxy
behavior, or browser extensions.

## Outstanding production checks

- Inspect browser storage and `Set-Cookie` response headers over production
  HTTPS to verify the effective path, `Max-Age`, `HttpOnly`, `Secure`, and
  `SameSite` attributes for all three cookies.
- Verify that production configuration overrides the checked-in
  `auth.cookie.secure=false` setting so authentication cookies are issued with
  `Secure` over HTTPS.
- Confirm how the production proxy communicates the original HTTPS scheme and
  any context path to Spring, because those values affect the framework-derived
  `XSRF-TOKEN` attributes.
- Inspect production browser network traffic for Cloudinary delivery, font
  delivery, and any requests introduced by hosting, proxy, CDN, or injected
  runtime configuration.
- Review additional hosting, reverse-proxy, and CDN cookie or header rewriting
  behavior that is not represented in this repository.
