# Decisions pending

Nonblocking product decisions: currency/launch geography; cancellation and refund
windows; customer nonresponse policy; dispute evidence retention; provider
verification requirements; legal terms; license choice; production infrastructure.

Until decided: explicit currency per price, UTC timestamps, simulated payments,
manual customer confirmation, no government ID collection, local development only.

Current local quote policy: validity at most 30 days (configurable with
`bliss.quotes.max-validity-seconds`); one quote per provider/request; submitted
quote scope is immutable; request scope freezes after a quote exists. Customers
may cancel open requests, retaining history. Quote revisions/withdrawal and actual
booking cancellation/refund policy remain future product decisions.

Engineering choice: Flow 1.2.0.CR1 aligns with the inspected reference checkout.
It is a release candidate; pin it, test restoration, and evaluate a final release
before product beta. Do not alter the upstream checkout to resolve API uncertainty.
