# Decisions pending

Nonblocking product decisions: currency/launch geography; cancellation and refund
windows; customer nonresponse policy; dispute evidence retention; provider
verification requirements; legal terms; license choice; production infrastructure.

Until decided: explicit currency per price, UTC timestamps, simulated payments,
manual customer confirmation, no government ID collection, local development only.

Engineering choice: Flow 1.2.0.CR1 aligns with the inspected reference checkout.
It is a release candidate; pin it, test restoration, and evaluate a final release
before product beta. Do not alter the upstream checkout to resolve API uncertainty.
