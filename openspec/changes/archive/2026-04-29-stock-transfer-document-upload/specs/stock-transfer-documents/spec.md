# Delta: stock-transfer-documents

> Retrofitted on 2026-04-29 to align the archived change with the OpenSpec
> archive structure. The change itself was authored and implemented before
> the `openspec` CLI was available locally, so the original archive lacked
> this `specs/` folder. The requirements below are extracted verbatim from
> the implemented behaviour described in `proposal.md` and `design.md` —
> nothing here introduces new contract that wasn't already shipped.

## ADDED Requirements

### Requirement: Locations and bins MAY require an attached document before a stock transfer completes

The system SHALL provide two activity codes — `REQUIRE_TRANSFER_OUT_DOCUMENT`
and `REQUIRE_TRANSFER_IN_DOCUMENT` — that admins can enable on a parent depot
location OR on an individual bin location. When enabled, the system SHALL
block stock transfer completion until at least one document is attached to
the underlying `Order`.

The check SHALL evaluate four sources for any single transfer:
- `order.origin.supports(REQUIRE_TRANSFER_OUT_DOCUMENT)`
- any `orderItems[].originBinLocation.supports(REQUIRE_TRANSFER_OUT_DOCUMENT)`
- `order.destination.supports(REQUIRE_TRANSFER_IN_DOCUMENT)`
- any `orderItems[].destinationBinLocation.supports(REQUIRE_TRANSFER_IN_DOCUMENT)`

If any source returns true, completion SHALL be blocked when no documents
are attached.

#### Scenario: Parent depot enforces outbound and no documents attached
- **GIVEN** the order's origin location supports `REQUIRE_TRANSFER_OUT_DOCUMENT`
- **AND** the order has zero attached documents
- **WHEN** the user attempts to complete the stock transfer
- **THEN** the backend SHALL throw a `ValidationException` carrying the
  reject code `customStockTransferDocument.required.error`
- **AND** the frontend SHALL display the localized error message via the
  global `apiClient` response interceptor

#### Scenario: Origin bin enforces outbound (parent depot does not)
- **GIVEN** the order's origin parent depot does NOT support
  `REQUIRE_TRANSFER_OUT_DOCUMENT`
- **AND** at least one `orderItem.originBinLocation` supports
  `REQUIRE_TRANSFER_OUT_DOCUMENT`
- **AND** the order has zero attached documents
- **WHEN** the user attempts to complete the stock transfer
- **THEN** the backend SHALL throw a `ValidationException`

#### Scenario: Destination bin enforces inbound
- **GIVEN** the order's destination parent depot does NOT support
  `REQUIRE_TRANSFER_IN_DOCUMENT`
- **AND** at least one `orderItem.destinationBinLocation` supports
  `REQUIRE_TRANSFER_IN_DOCUMENT`
- **AND** the order has zero attached documents
- **WHEN** the user attempts to complete the stock transfer
- **THEN** the backend SHALL throw a `ValidationException`

#### Scenario: Required and at least one document attached
- **GIVEN** any of the four enforcement sources returns true
- **AND** the order has at least one attached document
- **WHEN** the user attempts to complete the stock transfer
- **THEN** completion SHALL proceed normally

#### Scenario: No enforcement source returns true
- **GIVEN** none of the four enforcement sources returns true
- **WHEN** the user attempts to complete the stock transfer
- **THEN** completion SHALL proceed regardless of whether documents are attached

### Requirement: Users MAY upload and download documents on a stock transfer via a custom API

The system SHALL expose a custom API namespace for stock transfer document
operations, leaving the upstream `StockTransferApiController` unmodified.

- `GET /api/custom/stockTransfers/{id}/documents` SHALL return
  `{ documentRequired: boolean, documents: [{ id, name, documentType, contentType, uri }] }`.
- `POST /api/custom/stockTransfers/{id}/documents` SHALL accept multipart
  form data (`fileContents`) and persist the file via
  `Order.addToDocuments(...)` on the underlying `Order` entity.

Both actions SHALL be gated by the same authorization roles as the upstream
`StockTransferApiController`. Unauthorized callers SHALL receive HTTP 403.

#### Scenario: Authorized user lists documents
- **GIVEN** an authorized user
- **WHEN** they `GET /api/custom/stockTransfers/{id}/documents`
- **THEN** the response SHALL contain `documentRequired` and `documents[]`
- **AND** each document SHALL include `id`, `name`, `documentType`,
  `contentType`, and `uri` (download link)

#### Scenario: Authorized user uploads a document
- **GIVEN** an authorized user
- **AND** a multipart request body containing `fileContents`
- **WHEN** they `POST /api/custom/stockTransfers/{id}/documents`
- **THEN** the file SHALL be persisted on `Order.documents`
- **AND** the response SHALL be `200 { data: "Document was uploaded successfully" }`

#### Scenario: Unauthorized caller is rejected
- **GIVEN** a caller without the required role
- **WHEN** they call either endpoint
- **THEN** the response SHALL be HTTP 403

### Requirement: Uploads SHALL be size-, type-, and filename-validated on both client and server

The system SHALL apply a defense-in-depth allowlist for uploads:

- Server-side maximum size SHALL default to 10 MB (matching the upstream
  `Document.fileContents` GORM cap), tunable via
  `openboxes.custom.stockTransferDocuments.maxUploadSizeBytes`.
- Server-side content-type allowlist SHALL include PDF, common image types,
  Word, Excel, CSV, and ZIP.
- Server-side extension allowlist SHALL match the content-type allowlist.
- Server-side filename sanitization SHALL strip path components and ISO
  control characters, replace Windows-unsafe characters (`<>:"|?*`) with
  underscore, and truncate to 255 characters.
- The frontend `<Dropzone>` SHALL apply the same allowlist via `accept` and
  the same size cap via `maxSize`.

Localized error messages SHALL be returned for size, type, and filename
violations.

#### Scenario: Oversize upload is rejected
- **GIVEN** an upload larger than the configured maximum
- **WHEN** the request reaches the upload endpoint
- **THEN** the response SHALL be `400 { errorMessage: <localized> }`
- **AND** no `Document` SHALL be persisted

#### Scenario: Disallowed MIME type is rejected
- **GIVEN** an upload whose content type is not on the allowlist
- **WHEN** the request reaches the upload endpoint
- **THEN** the response SHALL be `400 { errorMessage: <localized> }`

#### Scenario: Path-traversal filename is sanitized
- **GIVEN** an upload whose filename contains path components or unsafe characters
- **WHEN** the file is persisted
- **THEN** the persisted `Document.name` and `Document.filename` SHALL
  contain only safe characters truncated to 255

### Requirement: Retried uploads SHALL be idempotent across client and server

The system SHALL prevent duplicate `Document` rows when an upload is retried
after a partial or network-interrupted failure.

- The frontend SHALL track per-file upload state and only re-send files
  that have not yet succeeded on a retry.
- The backend SHALL skip insertion when `order.documents` already contains
  a `Document` with the same sanitized filename and byte size, returning
  `200` without creating a new record.

#### Scenario: Partial batch failure leaves only failed files pending
- **GIVEN** a batch of files `[A, B, C]` where A succeeds, B fails, C succeeds
- **WHEN** the upload completes
- **THEN** only `B` SHALL remain in the panel's pending file list
- **AND** the documents list SHALL reflect A and C as persisted

#### Scenario: Network interruption after server-side persist
- **GIVEN** a request whose response is dropped after the server persisted
  the document
- **AND** the user retries the upload
- **WHEN** the retry reaches the server
- **THEN** the server SHALL detect the duplicate filename + size match on
  `order.documents`
- **AND** SHALL return `200` without creating a second `Document` row

### Requirement: The Stock Transfer Check Page SHALL be fail-closed for the document gate

The frontend SHALL default `customCanComplete` to `false` so the Complete
button is disabled until the custom panel has confirmed completion is
allowed. The panel SHALL emit `onCanCompleteChange(canComplete)` after each
load attempt:

| Panel state | Emitted value |
|-------------|---------------|
| Initial load in flight | (parent default `false`) |
| Load succeeded, not required | `true` |
| Load succeeded, required, 0 documents | `false` |
| Load succeeded, required, ≥1 document | `true` |
| Load failed (network error) | `false` (fail-closed) |

The backend `ValidationException` SHALL remain the authoritative gate even
if the frontend gate is bypassed.

#### Scenario: Initial mount, button disabled
- **GIVEN** the user has just navigated to the Stock Transfer Check Page
- **WHEN** the page renders
- **THEN** the Complete button SHALL be disabled until the panel emits
  `canComplete=true`

#### Scenario: Network error during panel load
- **GIVEN** the panel's initial fetch fails with a network error
- **WHEN** the user attempts to complete
- **THEN** the Complete button SHALL remain disabled

#### Scenario: Bypassed frontend gate is still rejected by the backend
- **GIVEN** a user who bypasses the disabled Complete button via devtools
- **AND** no documents are attached to a transfer that requires one
- **WHEN** the completion request reaches the backend
- **THEN** the backend SHALL throw `ValidationException`
- **AND** the response interceptor SHALL display exactly one localized
  error toast (no duplicate)

### Requirement: All custom feature code SHALL live under custom packages and folders

The system SHALL keep all new code for this feature isolated from upstream
files, per the project's Upstream Compatibility rules. Custom files SHALL
live under:

- Backend: `org.pih.warehouse.custom.stocktransferdocuments.*`
- Frontend: `src/js/custom/stockTransferDocuments/` (with `components/`,
  `utils/`, `__tests__/` sub-folders)

Modifications to upstream files SHALL be limited to a documented set of
surgical edits (the "Upstream Touch Point Summary" in the change's
`tasks.md`).

#### Scenario: New backend file placement
- **WHEN** a new backend Groovy file is added for this feature
- **THEN** its package SHALL be `org.pih.warehouse.custom.stocktransferdocuments.*`

#### Scenario: New frontend file placement
- **WHEN** a new frontend file is added for this feature
- **THEN** its path SHALL be under `src/js/custom/stockTransferDocuments/`

#### Scenario: Upstream file edit budget
- **WHEN** the implementation is complete
- **THEN** upstream file modifications SHALL match the per-file edit budget
  in the change's "Upstream Touch Point Summary" exactly
- **AND** no incidental reformatting, import reordering, or symbol
  renaming SHALL be present in the diff
