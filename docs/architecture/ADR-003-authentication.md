# ADR-003: External OIDC identity

Implemented in the identity slice (#2). Keycloak supplies local demo OIDC,
Quarkus validates bearer tokens, Flutter uses Authorization Code + PKCE and secure
token storage. One user can be both customer and provider. Provider membership and
resource ownership are application concerns, enforced alongside token roles.

Do not carry forward legacy password/JWT handling or expose protected mutations
until authorization and negative tests exist. No temporary fake identity adapter
is included in the public catalog foundation.

The API accepts JWT access tokens from one configured issuer, requires a subject
and `bliss-api` audience, and reads roles from signed `realm_access/roles` claims.
Historical `user_role` rows are never authorization evidence. `/me/bootstrap`
upserts the validated subject; the client cannot select a user ID or capability.
Provider mutations additionally require an OWNER membership, and use optimistic
versions. Profile/provider edits write transactional audit metadata without
submitted profile text or credentials.

Both public clients enforce S256 PKCE and disable password/implicit grants. Web
and native clients include Keycloak's `basic` default scope, which supplies the
required access-token subject claim. Missing subjects are rejected, not inferred
from an ID token or username. The live browser verification covers this configuration.
Web code uses openid_client's PKCE flow, validates the ID token signature, issuer,
audience, expiry and nonce, and checks a one-use state transaction. Tokens remain
in memory. AppAuth handles the native browser flow and refresh; refresh tokens
are retained only in platform secure storage. Logout clears private client state
and invokes the identity provider's end-session endpoint. Existing access tokens
remain valid until their short expiry; global immediate revocation is not claimed.

Local setup: run `infrastructure/scripts/prepare-local-auth.ps1` before Compose.
Generated realm/password files live only under ignored `.work`. Keycloak 26.7.4
uses a persistent local volume and loopback port 8180; its development mode is
not production infrastructure. Client redirects are exact, not wildcard URLs.
API production startup requires `BLISS_OIDC_URL`; real deployments require HTTPS.
Real Android browser login, customer/provider session recovery after process death,
logout and account switching are verified in [the Android evidence](../demo/ANDROID.md).
iOS device validation and production session hardening remain tracked work.
