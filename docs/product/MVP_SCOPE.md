# MVP scope

Implemented: public category/offering discovery, OIDC profiles and memberships,
provider onboarding, owned offering management, Flutter catalog/account/business
screens, request/quote APIs, direct offering bookings with frozen scope/price,
durable BookingLifecycleFlow and fake settlement, protected booking timeline,
schema constraints, operational endpoints and CI. Real process-restart tests
cover both quote and direct sources; populated migration tests preserve old bookings.

Required next: mobile request/quote/direct-booking lifecycle and native-device
authentication validation; persisted chat; eligible reviews; manual-admin
DisputeFlow. See issues #7–#10. Backend booking execution is verified; the Flutter
booking screens are not implemented yet.

Portfolio hardening: migration/concurrency tests, restart demo, screenshots and
complete CI evidence (#11–#12). Completion means the full master-prompt release
criteria pass, not just catalog compilation.

Out of MVP: live money, maps/geocoding, production messaging vendors, ID collection,
tax/insurance compliance, paid hosting, Kubernetes, Kafka and advanced search.
