# Packaged management access (#15)

The default packaged `prod` profile requires an OIDC `operator` realm role for
the non-application root and all of its children. With the default root this is
`/q` and `/q/*`: health, Prometheus, OpenAPI and Swagger UI. Public category and
offering discovery keeps its existing access policy. Business endpoints retain
their existing customer/provider/admin checks.

The policy uses Quarkus HTTP permissions, applies to all HTTP methods, and is
shared so a more specific permission cannot silently replace it. Its paths derive
from `quarkus.http.non-application-root-path`; relative paths follow the HTTP root.
The default configuration and tested path variants are recorded below. Changing
either root or adding another management listener requires repeating access tests
for that deployment. See the official
[HTTP authorization reference](https://quarkus.io/guides/security-authorize-web-endpoints-reference/).

This is application authorization on the existing HTTP listener. It does not
create a separate management port, private network, TLS ingress or hosted service.
The API still binds to `127.0.0.1` by default. Retain loopback for local use and
require private/operator ingress and TLS before hosting. A service on loopback
still requires an operator token in `prod`.

## Roles, probes and documentation

An operator token must come from the configured trusted issuer, have audience
`bliss-api`, a subject, a valid signature/expiry and `operator` in
`realm_access/roles`. An unauthenticated/invalid request is rejected with 401;
an authenticated identity without the role is rejected with 403. Customer,
provider and dispute-admin roles do not imply operator access. Operator alone
does not grant provider management authority. No role mapping or standing operator
account is created by this change.

Assign operator capability through the identity provider only to approved
operational identities. Never grant it through a client field, profile edit or
shared marketplace account. Before hosting, choose credential issuance/rotation,
revocation, access review and audit procedures; these are still owner/operator
decisions, not a managed identity implementation in this repository.

Production probes and scrapers must send `Authorization: Bearer <token>` to
`/q/health/live`, `/q/health/ready` and `/q/metrics`. Supply short-lived credentials
through the eventual runtime's secret mechanism, not URLs or committed files.
Refresh before expiry and distinguish 401/403 from health payload DOWN. A failed
authentication check must not automatically be diagnosed as a failed database or
trigger destructive recovery. Evaluate issuer outages and probe credential
availability when designing hosted restart/readiness behavior. No probe exemption
is public in the default production profile.

OpenAPI and Swagger assets require the same operator authorization. An ordinary
browser navigation does not add bearer credentials to each page/asset request;
use an approved private authenticated operator gateway/tool when providing browser
documentation access. Browser operator login/gateway integration is not implemented.
The local development documentation remains the simple browser workflow.

The `local` demo profile explicitly disables this permission on its default
loopback listener. `dev` and `test` also retain local tools. Do not select any of
these profiles for public hosting. Local runners continue to build and launch
with the same `local` profile. Packaged restart-test probes use freshly signed
ephemeral operator tokens; no real operator credential is stored or logged by
the harness.

## Verification and remaining work

Run with Java 21 and Docker from an isolated API checkout/build directory:

```sh
cd services/api
./mvnw -B -ntp verify -Dquarkus.http.test-port=0
```

`ManagementAccessIT` launches the actual packaged production API against its own
PostgreSQL 17.6 container. Four scenarios check:

- Anonymous denial, customer/provider/admin denial and operator success for
  liveness/readiness/startup health, metrics, OpenAPI and Swagger UI.
- Invalid signature, issuer, audience, expiry, malformed/missing-subject tokens and roleless
  identities cannot read metrics.
- GET/HEAD/OPTIONS/POST cannot bypass protection; root, trailing slash, encoded
  root, dot-segment and query variants are checked without credentials.
- Public category reads still work, anonymous identity reads remain denied, and
  operator-only tokens do not gain provider management access.

`LocalManagementTest` runs the actual local configuration against a dedicated
test-managed PostgreSQL container and verifies unauthenticated readiness, metrics,
OpenAPI and Swagger. It overrides the local datasource and disables dispatch;
it must never target the developer's existing database. Existing operations smoke
tests separately cover the default test profile. All owned test containers are
cleaned up, preserving existing development services/volumes.

See [progress](../agent/PROGRESS.md) for executed commands and results. These
checks use ephemeral test signing keys, not a live Keycloak browser or managed
operator identity. The test identity fixture now serves public JWKS on a random
loopback port and signs tokens with an explicit key ID, exercising the normal
OIDC JWT verification path. Quarkus 3.39.0's offline public-key shortcut does not
apply the configured subject check, as confirmed by the first negative test and
the [pinned implementation](https://github.com/quarkusio/quarkus/blob/3.39.0/extensions/oidc/runtime/src/main/java/io/quarkus/oidc/runtime/OidcIdentityProvider.java#L740).
Avoid that shortcut when testing this boundary. The private signing key stays in
test-process memory and the JWKS server is stopped with its test resource.
Private management networking, TLS, scrapers/alerts, credential
rotation/revocation and outage drills remain open under
[#15](https://github.com/Stuaarts/Bliss/issues/15). The
[readiness plan](READINESS.md) and deferred CI/native release gate still apply.
