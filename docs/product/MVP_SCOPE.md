# MVP scope

Implemented: public category/offering discovery, OIDC profiles and memberships,
provider onboarding, owned offering management, Flutter catalog/account/business
screens, request/quote APIs and atomic booking creation, schema constraints,
operational endpoints and CI configuration.

Required next: direct booking; durable BookingLifecycleFlow with fake payments;
mobile request/quote/booking lifecycle; persisted chat; eligible reviews;
manual-admin DisputeFlow. See issues #5–#10 and the direct-booking acceptance
criteria under the booking work. A CREATED row alone is not workflow execution.

Portfolio hardening: migration/concurrency tests, restart demo, screenshots and
complete CI evidence (#11–#12). Completion means the full master-prompt release
criteria pass, not just catalog compilation.

Out of MVP: live money, maps/geocoding, production messaging vendors, ID collection,
tax/insurance compliance, paid hosting, Kubernetes, Kafka and advanced search.
