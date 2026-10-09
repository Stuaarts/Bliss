# iOS secondary acceptance — draft, not yet verified

The native drivers now prepare direct booking cancellation, a manual dispute and
catalog connection recovery. **Neither new XCTest has compiled or executed on
macOS yet.** These scenarios do not establish iOS acceptance until their native
results, screenshots and database evidence pass review.

On October 9, one retry of [main CI](https://github.com/Stuaarts/Bliss/actions/runs/37957100805)
at `f1421a3e55c08101a7ec4b53410909b70729ef2b` was rejected before any steps ran
(attempt 2). GitHub again reported failed recent account payments or a spending
limit requiring adjustment. The account owner must check GitHub Billing & plans;
this message does not identify which condition applies. No billing change was
made. There is no local Mac available in this Windows workspace.

## Run on a Mac

Use Xcode/iOS Simulator, Java 21, Flutter 3.47.5, Homebrew PostgreSQL 17 and the
repository's Maven wrapper. From the repository root:

```sh
python3 -B -m unittest discover -s infrastructure/scripts/tests -v
python3 infrastructure/scripts/test-ios-oidc.py --scenario secondary
python3 infrastructure/scripts/test-ios-oidc.py --scenario connectivity
```

Each invocation uses the [shared isolated runner](IOS_AUTH.md): fresh loopback
database, pinned Keycloak realm, packaged API and a newly created simulator.
The secondary scenario adds a generated independent admin identity to its own
realm. No personal credentials, real payments or existing local data are used.
Business mutations occur through normal app controls and real browser PKCE login.

The [workflow](../../.github/workflows/ios-secondary.yml) runs the two scenarios
independently on relevant source pushes, with manual dispatch also available
after the workflow reaches the default branch. Once Mac runners can execute jobs,
rerun the rejected branch jobs while the PR stays in draft. Rerun ordinary CI,
the authentication-only gate, primary marketplace gate
and synthetic session gate on the final head before merging; shared native UI
helpers and the runner changed.

## Required secondary evidence

`NativeSecondaryTests/testDirectCancellationAndManualDispute` must pass exactly
once, with nine named checkpoints and nine screenshots:

1. Customer directly books seeded Garden refresh for CAD 65.50, cancels while
   waiting for the provider and reopens the cancelled/voided booking after restart.
2. Customer creates another direct booking, identified by a different unique city.
3. Provider accepts the normal two-minute schedule, waits for the real start time,
   starts service and marks it complete.
4. Customer files a dispute and has no decision controls.
5. Provider submits evidence; the case awaits admin review without decision controls.
6. Independent admin verifies this run's reason and evidence, records a manual
   customer-favored decision and waits for the applied result.
7. Customer sees a cancelled booking and simulated void.
8. Decision and rationale survive background/resume and app-process restart.
9. The customer signs out.

The runner also requires all nine named read-only database assertions: exactly
two distinct direct bookings from the seeded offering assigned to the expected
customer/provider, a dispute opened by that booking customer, cancelled status, exact
CAD 65.50 simulated voids, exactly one AUTHORIZE and one VOID per booking with no
other payment operations, one customer-opened resolved dispute, one provider
evidence row, one applied independent-admin decision with the exact rationale,
and no dispute on the first cancelled booking. Missing, null, false or unexpected
proof keys fail the runner. This check does not restart the API or claim backend
workflow restoration; that remains covered by separate packaged-process tests.

## Required connectivity evidence

`NativeConnectivityTests/testCatalogConnectionRecovery` must pass exactly once,
with two checkpoints and screenshots. A runner-only GET proxy closes API
connections before the normal app launches. The app must show the connection
failure and Try again control. The test restores forwarding through a separate,
authenticated loopback control listener, then taps Try again on the same screen
without relaunching. It must display the real Garden refresh offering and
CAD 65.50 price, with the retry error removed.

The proxy forwards only public `/api/v1/` GET requests to the disposable API;
it supplies no catalog fixtures and allows no business mutations. Its randomly
allocated listeners and generated control secret belong only to this run. The
secret is masked/redacted and the proxy closes during runner cleanup. The
production Flutter app has no outage-control code.

## Local validation and acceptance boundary

Seven Python tests pass on Windows: real socket outage/recovery, unauthorized
and malformed controls, route/mutation rejection, listener cleanup, complete
boolean database-proof enforcement and secret redaction. Existing runner
redaction and subprocess-exit checks also pass. Native source wiring and marker
consistency are checked locally, but these checks do not compile Swift, execute
XCTest, validate its selectors, or verify records produced by the new native UI flow.

The evidence query also passes 28 cases against real PostgreSQL 17.6 with all ten
repository migrations and the local catalog seed applied. These use synthetic SQL
fixtures in a fresh networkless Docker container, with no published ports or
persistent volumes. The valid fixture passes; 27 incorrect cases fail, covering
missing/incorrect bookings and participants, payment amount/currency/status and
transaction counts, dispute opener/reason/status, evidence and independent admin
decision identity/outcome/rationale/application. Missing payment records explicitly
fail their own assertion instead of being ignored by PostgreSQL's null aggregation.
This check found and fixed acceptance of bookings assigned to the wrong customer
or provider, and failure to correlate the dispute opener to the booking customer.

To reproduce independently of a Mac, with a ready Linux Docker engine:

```sh
python3 -B infrastructure/scripts/check-ios-secondary-database.py --isolated-docker
```

The script checks a unique ownership label before removing its container and uses
rollback transactions for each negative case. The ordinary API CI job runs this
check separately from Maven. It validates the SQL/acceptance validator; it does
not drive the app or establish native marketplace acceptance. Docker started
successfully on Windows through the existing guarded helper on October 9; no
existing containers, persistent volumes, or runtime backups were removed.

Sanitized evidence belongs in `.work/ios-secondary/run-*` and
`.work/ios-connectivity/run-*`; the workflow retains those private artifacts for
seven days. Only allowlisted named app screenshots are exported. Raw XCTest
bundles, browser captures and generated credentials stay private and are removed.
Issues #7/#12 remain open. Physical devices and signed distribution remain
separate from simulator acceptance.
