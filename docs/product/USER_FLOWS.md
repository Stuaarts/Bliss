# User flows

Available: open catalog → filter category → page through active offerings → inspect
provider/city, description and exact currency/price. Sign in via PKCE, manage the
account, onboard a provider and publish/edit/archive offerings. Loading, empty and
retry states are explicit. Booking APIs work; booking screens remain #7.

Available through the API: customer request → provider quote → customer acceptance,
or customer selects a published offering revision → direct booking. Both then use
provider accept/schedule → start → mark complete → customer confirmation and
simulated capture. Rejection/cancellation/timeout compensate authorization. Owned
timeline/payment/execution reads show progress; repeated submissions are idempotent.

Planned full customer UI: OIDC login → create request → compare quotes → accept
(or directly book a published offering) → booking
timeline → chat → confirm completion or dispute → review after completion.

Planned full provider UI: same identity with provider capability → own offerings → opportunities
→ quote → accept/reject assigned booking → start → mark complete → chat.

Planned admin: authenticated scoped access → review evidence → explicit dispute
decision → idempotent fake capture/refund and audit record.
