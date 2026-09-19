# ADR-003: External OIDC identity

Accepted direction; implementation pending #2. Keycloak supplies local demo OIDC,
Quarkus validates bearer tokens, Flutter uses Authorization Code + PKCE and secure
token storage. One user can be both customer and provider. Provider membership and
resource ownership are application concerns, enforced alongside token roles.

Do not carry forward legacy password/JWT handling or expose protected mutations
until authorization and negative tests exist. No temporary fake identity adapter
is included in the public catalog foundation.
