# reservation-events — API / External Contract

## Contract Scope

This contract defines the externally observable behavior of the single event published by the
`reservation-events` work item: the `ReservationCreated` event written to Kafka when a reservation is
successfully created (RE-R4). It is the deliverable of Plan milestone RE-M1 (CONTRACT) and resolves
every decision the requirements defer to that milestone (RE-C1).

This contract governs externally observable behavior only. The approved Plan
(`docs/.ai/reservation-events/Plan.md`) remains the single source of truth for scope, milestones, and
requirement ownership. The approved requirements (`docs/.ai/reservation-events/requirements.md`)
remain authoritative for delivery semantics.

The consumer of this event is the notifications team, which uses it to email the user a confirmation
(Goal, RE-X1).

## Out of Scope

- Any event other than `ReservationCreated`, including a cancellation event (RE-R6, RE-X2).
- Any change to the HTTP contract of the existing operations defined by the `initial-build` work
  item's contract (`docs/.ai/initial-build/0_API_Contract.md`) (RE-R9, RE-X8). The HTTP status,
  response body, and stored reservation are the same whether or not the publish succeeds.
- Email address or any other personal data in the event (RE-R7, RE-X6).
- Schema registry, schema-registry-dependent formats, or any external integration other than the
  Kafka publish (RE-R2, RE-X5).
- Delivery guarantees beyond best-effort: durable retry, outbox, dead-letter handling, transactional
  or exactly-once publishing (RE-R8, RE-R10, RE-R11, RE-X3, RE-X4).
- Kafka record headers: this contract defines none, and consumers must not rely on any.
- Topic provisioning, partition count, replication, retention, and broker connection configuration.
  These are operational or internal concerns (RE-D4), not event behavior.
- Consumer-side behavior, including deduplication and email delivery, owned by the notifications
  team (RE-R11, RE-X1).
- Internal publishing structure, client library, and configuration keys (RE-D1, RE-D4, RE-D5).

## Requirement Traceability

| Capability | Requirement / Plan Reference |
| --- | --- |
| Publish `ReservationCreated` on successful creation | RE-R4, RE-R5; Plan RE-M1, RE-M4, RE-M6 |
| `ReservationCreated` is the only event | RE-R6, RE-X2 |
| Topic, key, serialization, schema/field set, versioning, ordering | RE-C1; Plan RE-M1 "Decisions this milestone owns" |
| `userId` as the only user identification; no personal data | RE-R7, RE-X6 |
| Best-effort delivery; possible loss; possible duplicates | RE-R8, RE-R10, RE-R11, RE-R12, RE-O2 |
| No effect on the creation HTTP outcome | RE-R9, RE-X8 |
| Timestamps in UTC, ISO-8601 | Product `docs/requirements.md` § Time Precision (read-only context); `initial-build` contract § Timestamp Format and Precision |

## API Capabilities

### ReservationCreated event

- **Purpose:** Tell the notifications team that a reservation was successfully created, so they can
  email the user a confirmation.
- **Related requirement:** RE-R4, RE-R5, RE-R6, RE-R7, RE-C1.
- **Endpoint:** Kafka topic `reservation-events`. HTTP does not apply.
- **HTTP method:** Not applicable (asynchronous Kafka publish).
- **Trigger:** A reservation is successfully created, i.e. `POST /api/v1/reservations` returns
  **201 Created** (RE-R4). No event is published for a creation rejected by validation (400) or by
  conflict detection (409) (RE-R5).
- **Record key:** The reservation id (the same value as `reservationId` in the payload), serialized
  as a UTF-8 string in canonical UUID form, e.g. `3f2b8c1e-4a5d-4e6f-8a9b-0c1d2e3f4a5b`. It is never
  null.
- **Record value:** A UTF-8 encoded JSON object with exactly the fields below.

| Field | JSON type | Required | Value |
| --- | --- | --- | --- |
| `eventType` | string | yes | Always the literal `"ReservationCreated"`. |
| `schemaVersion` | integer | yes | Always `1` for the schema defined here. |
| `occurredAt` | string | yes | The UTC instant, in ISO-8601 instant form (see Timestamps), at which the service produced this event for the successfully created reservation. |
| `reservationId` | string | yes | The created reservation's `id`, in canonical UUID form. Equal to the `id` in the 201 response body. |
| `workspaceId` | string | yes | The created reservation's `workspaceId`, exactly as in the 201 response body. |
| `userId` | string | yes | The created reservation's `userId`, exactly as in the 201 response body. This is the only user identification in the event (RE-R7). |
| `startTime` | string | yes | The created reservation's `startTime` as a UTC ISO-8601 instant, the same value as in the 201 response body. |
| `endTime` | string | yes | The created reservation's `endTime` as a UTC ISO-8601 instant, the same value as in the 201 response body. |

Example value:

```json
{
  "eventType": "ReservationCreated",
  "schemaVersion": 1,
  "occurredAt": "2026-10-01T08:59:12.345Z",
  "reservationId": "3f2b8c1e-4a5d-4e6f-8a9b-0c1d2e3f4a5b",
  "workspaceId": "ws-42",
  "userId": "user-7",
  "startTime": "2026-10-02T09:00:00Z",
  "endTime": "2026-10-02T10:00:00Z"
}
```

The event carries no email address, name, or other personal data (RE-R7, RE-X6). It carries no
reservation `status` field.

- **Required inputs:** Not applicable. The event is produced by the service, not submitted by a
  client.
- **Optional inputs:** Not applicable.
- **Success status code:** Not applicable. There is no response to the publish. The creation
  request's HTTP outcome is defined by the `initial-build` contract and is unaffected (RE-R9).
- **Success response body:** Not applicable.
- **Failure status codes:** Not applicable. A publish failure, including a slow or unavailable broker,
  produces no event and no externally visible error. The creation still returns 201 with its normal
  body (RE-R9, RE-R12). Failed publishes are logged and counted inside the service (RE-R16); that is
  not part of this external contract.
- **Failure response body:** Not applicable.
- **Validation rules:** The service publishes only reservations that passed the existing creation
  validation (RE-R5). The event adds no validation of its own.
- **Identity / existence behavior:** `reservationId` identifies the reservation and is the
  deduplication key for consumers (RE-R11). The event is not an operation on an identifier, so
  unknown vs malformed identifiers do not apply.
- **Mutation semantics:** Not applicable. The event is an immutable notification of a past creation.
  Later changes to the reservation, such as cancellation, publish no event (RE-R6).

### Delivery semantics (normative for consumers)

- **Best-effort** (RE-R8). An event may never be delivered: failed publishes are not retried durably,
  and unsent events may be lost when the service restarts (RE-R10, RE-R12).
- **Duplicates possible** (RE-R11). The same `ReservationCreated` for one reservation may be
  delivered more than once. Consumers must deduplicate by `reservationId`. Exactly-once delivery is
  not provided (RE-X4).
- **No ordering guarantee.** This contract promises no ordering between any two events, including
  events for the same workspace or the same user. Because every reservation produces at most one
  logical event and the record key is the reservation id, consumers need no cross-event ordering to
  process an event correctly.
- **Timing.** No delivery latency is promised (RE-O6).

### Timestamps

`occurredAt`, `startTime`, and `endTime` are UTC instants in ISO-8601 instant form with a `Z` suffix.
Fractional seconds appear only when non-zero. This is the same representation the service uses for
timestamps in its HTTP responses (`ReservationController.java:114-115`, `ISO_INSTANT`). Consumers must
accept an instant with or without fractional seconds.

## Contract-Wide Rules

- **Input parsing and type errors:** Not applicable. The service accepts no input through this
  contract. Malformed creation requests are handled by the `initial-build` contract and publish no
  event (RE-R5).
- **Absent / null / empty / blank values:** Every field in the table above is always present and
  never null. `eventType`, `occurredAt`, `reservationId`, `startTime`, and `endTime` are never empty.
  `workspaceId` and `userId` carry the values accepted by creation validation, so they are never null
  or blank under the `initial-build` contract's validation rules.
- **Read-only and unknown fields:** The producer emits exactly the fields listed for
  `schemaVersion` `1`. A later compatible change may add new optional fields without changing
  `schemaVersion` (see Compatibility Notes), so consumers must ignore fields they do not recognize.
- **Numeric semantics:** The only numeric field is `schemaVersion`, a JSON integer. No decimal,
  monetary, or computed numeric values exist.
- **Collection ordering, empty results, limits:** Not applicable. The payload contains no
  collections. Ordering across events is covered under Delivery semantics.
- **Matching and filtering semantics:** Not applicable. The event defines no lookup or filter.
- **Error response shape and which parts are normative:** Not applicable. The event channel has no
  error responses, and a publish failure is not externally visible (RE-R9).

## Decisions Resolved

| Decision (RE-C1) | Resolution | Source |
| --- | --- | --- |
| Topic name | `reservation-events` | User decision at the RE-M1 clarification, 2026-09-28 |
| Message key | Reservation id as a UTF-8 canonical-UUID string, equal to `reservationId` | User decision at the RE-M1 clarification, 2026-09-28; matches the consumer's dedup key (RE-R11) |
| Serialization format | UTF-8 JSON object as the record value; no schema registry | User decision at the RE-M1 clarification, 2026-09-28; no registry per RE-R2/RE-X5 |
| Event schema and exact field set | `eventType`, `schemaVersion`, `occurredAt`, `reservationId`, `workspaceId`, `userId`, `startTime`, `endTime`; no personal data | User decision at the RE-M1 clarification, 2026-09-28; RE-R7 |
| Schema versioning | In-payload integer `schemaVersion`, starting at `1`. Adding optional fields keeps the version; any breaking change increments it | User decision at the RE-M1 clarification, 2026-09-28 |
| Ordering guarantees | None promised, across any events | User decision at the RE-M1 clarification, 2026-09-28; consistent with RE-O2 |

## Compatibility Notes

- **Existing HTTP contract:** unchanged (RE-X8). No field, status code, or behavior of
  `POST /api/v1/reservations`, `GET /api/v1/reservations/{id}`, or `DELETE /api/v1/reservations/{id}`
  changes.
- **Event schema evolution:** these changes are **compatible** and keep `schemaVersion` `1`:
  - adding a new optional field.

  These changes are **breaking** and require incrementing `schemaVersion`:
  - removing or renaming a field;
  - changing a field's type or meaning;
  - making an optional field required;
  - changing the record key or the topic.

  Any breaking change is a contract change and requires a new approved contract revision.

## Contract Review Status

- Status: **Approved** by human review (2026-09-28).
- Implementation Plans from RE-M2 onward must conform to this contract. Any change to it requires a
  new approved contract revision.
