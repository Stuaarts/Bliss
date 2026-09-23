# User flows

Available: open catalog → filter category → page through active offerings → inspect
provider/city, description and exact currency/price. Sign in via PKCE, manage the
account, onboard a provider and publish/edit/archive offerings. Loading, empty and
retry states are explicit. Your activity supports owned requests, provider
opportunities, quotes and bookings with pagination.

Available through Flutter and the API: customer request → provider quote → customer acceptance,
or customer selects a published offering revision → direct booking. Both then use
provider accept/schedule → start → mark complete → customer confirmation and
simulated capture. Rejection/cancellation/timeout compensate authorization. Owned
timeline/payment/execution reads show progress; repeated submissions are idempotent.

Customer UI: OIDC login → create request → compare quotes → accept (or directly
book a published offering) → booking timeline → confirm completion. Request/quote
decline and eligible cancellation are available. Chat, disputes and reviews are
the remaining steps toward the full customer experience.

Provider UI: same identity with provider capability → own offerings → opportunities
→ quote → accept/reject assigned booking → start at the scheduled time → mark
complete. Chat remains planned. See [verified demo](../demo/MARKETPLACE.md).

Planned admin: authenticated scoped access → review evidence → explicit dispute
decision → idempotent fake capture/refund and audit record.
