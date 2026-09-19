# User flows

Available: open catalog → filter category → page through active offerings → inspect
provider/city, description and exact currency/price. Loading, empty and retry states
are explicit. The client does not pretend booking is available.

Planned customer: OIDC login → create request → compare quotes → accept → booking
timeline → chat → confirm completion or dispute → review after completion.

Planned provider: same identity with provider capability → own offerings → opportunities
→ quote → accept/reject assigned booking → start → mark complete → chat.

Planned admin: authenticated scoped access → review evidence → explicit dispute
decision → idempotent fake capture/refund and audit record.
