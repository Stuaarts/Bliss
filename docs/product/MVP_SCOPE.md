# MVP scope

Implemented: public category/offering discovery, OIDC profiles and memberships,
provider onboarding, owned offering management, Flutter catalog/account/business
screens, customer/provider request and quote screens, direct offering booking
confirmation, lifecycle actions, request/quote APIs, bookings with frozen scope/price,
durable BookingLifecycleFlow and fake settlement, protected booking timeline,
persisted booking conversations with authorized Flutter chat, completed-booking
reviews and public provider rating aggregates, manual-admin disputes with private
evidence and durable simulated settlement, schema constraints,
operational endpoints and CI. Real process-restart tests
cover both quote and direct sources; populated migration tests preserve old bookings.

Required next: native-device authentication validation (#7)
and final portfolio sign-off (#12). Database hardening (#11) is verified and merged.
Flutter web lifecycle evidence
is recorded in the [marketplace demo](../demo/MARKETPLACE.md). Native execution is
not yet validated, and the full portfolio release remains incomplete.
Chat browser and restart evidence is in the [chat demo](../demo/CHAT.md).

Portfolio hardening includes migration/concurrency tests, restart demo, screenshots
and complete CI evidence. See the [release checklist](RELEASE_CHECKLIST.md).
Completion means the full master-prompt release
criteria pass, not just catalog compilation.

Out of MVP: live money, maps/geocoding, production messaging vendors, ID collection,
tax/insurance compliance, paid hosting, Kubernetes, Kafka and advanced search.
