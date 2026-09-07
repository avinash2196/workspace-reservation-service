Execute Milestone 0 from the approved docs/.ai/Plan.md.

Read:
- docs/requirements.md
- docs/.ai/Plan.md

Create only:
docs/.ai/0_API_Contract.md

Define the approved external contract for:
- POST /api/v1/reservations
- GET /api/v1/reservations/{id}
- DELETE /api/v1/reservations/{id}

Include schemas, status codes, validation/error structure, conflict behavior,
and externally observable concurrency guarantee.

Do not invent behavior.
Do not create code or tests.
Do not mark the contract approved.