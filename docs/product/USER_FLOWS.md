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
decline and eligible cancellation are available. After completion, Booking review
lets the customer publish one rating and optional public comment. Offering detail
→ Provider reviews shows the provider's average, review count and paged comments
without sign-in. Dispute / support lets an eligible customer open a private case
within seven days of the provider marking work complete. Existing cases reopen.

Provider UI: same identity with provider capability → own offerings → opportunities
→ quote → accept/reject assigned booking → start at the scheduled time → mark
complete. See [verified demo](../demo/MARKETPLACE.md).

Customer/provider chat: open an owned booking → Messages → send plain text.
Both participants can reopen it through Your activity → Messages. The visible
chat polls every three seconds, supports refresh and loading earlier messages,
and retains an uncertain send for an idempotent retry. Completed/cancelled bookings
keep their conversations. See [chat evidence and access policy](../demo/CHAT.md).

Dispute participants: Your activity → Disputes → case → Add evidence. Collection
closes once both parties have submitted or after 24 hours; the initial customer
reason counts as evidence. The case then waits for manual review without a default
winner. Current provider owners inherit case access (unlike chat snapshots).

Admin: sign in with admin capability → Your activity → Disputes → read the case
and evidence → Resolve dispute → choose an outcome and explain the decision.
An admin who is the customer or any member of the assigned provider cannot decide.
The admitted decision remains pending until Flow commits simulated settlement.
Customer-favored outcomes void an authorization or refund a capture; provider-
favored outcomes capture or retain capture. See [dispute demo](../demo/DISPUTES.md).
