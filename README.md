# Bliss V2

A local-services marketplace connecting customers with individual professionals
and companies. Rebuilding the preserved prototype as a Java 21 / Quarkus modular
monolith with PostgreSQL and a Flutter client.

**Status: foundation under construction, not a completed portfolio release.**

The core process will be request → quote → booking → service → confirmation or
dispute → simulated settlement. Standard offerings will also support direct booking.
Quarkus Flow coordinates the long waits, timeouts, retries and payment compensation;
the relational domain remains authoritative for API reads.

- [Reconnaissance](docs/agent/RECONNAISSANCE.md)
- [Execution plan](docs/agent/PLAN.md)
- [Progress and validation](docs/agent/PROGRESS.md)
- [GitHub backlog](https://github.com/Stuaarts/Bliss/issues)

The original `Bliss-*` folders and `quarkus-flow` are preserved local references,
excluded from this repository. The upstream checkout must not be edited.

License: not yet selected. No license grant is implied; resolve before public release.
