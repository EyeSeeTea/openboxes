# Stock Transfer Document Upload

## What

Add document upload/download capability to stock transfers so that users can attach certificates
(e.g., quarantine release certificates) to a transfer. Optionally enforce document attachment
before completion via a new `REQUIRE_TRANSFER_DOCUMENT` activity code configurable per location.

## Why

The quarantine workflow requires that stock arriving via shipment is placed in a quarantine holding
bin. When transferring stock out of quarantine, a release certificate must be attached to the
transfer as proof of approval. Currently there is no way to attach any document to a stock transfer
even though the underlying Order domain already has a `documents` relationship — the feature simply
has no UI or API to use it.

Without this, certificates must be tracked outside the system (email, paper files), creating an
audit gap between the inventory movement and its supporting documentation.

## Scope

### In scope

- **New custom backend endpoint** `GET/POST /api/custom/stockTransfers/{id}/documents` —
  served by a new `CustomStockTransferDocumentController` + `CustomStockTransferDocumentService`
  under `org.pih.warehouse.custom.stocktransferdocuments.*`. Upstream `StockTransferApiController`
  is **not** modified.
- **New custom frontend panel** under `src/js/custom/stockTransferDocuments/`
  (split into `components/`, `utils/`, `__tests__/` per `custom-package-isolation.md`)
  — self-contained, mounted into the existing Stock Transfer Check Page via a minimal
  edit (1 import + 1 JSX line + 1 disabled-prop edit).
- New `REQUIRE_TRANSFER_DOCUMENT` activity code (single-line enum addition).
- Backend enforcement hook in `StockTransferService.completeStockTransfer()` — a single
  delegating call to the custom service; all logic lives in custom code.
- Frontend enforcement: warning message and disabled Complete button when documents are required.

### Upstream compatibility

Per `CLAUDE.md` → *Upstream Compatibility* and `.claude/rules/custom-package-isolation.md`,
this change is designed to stay as isolated as possible. Upstream touch points are
bounded and documented in `design.md` and `tasks.md`. See the Upstream Touch Point
Summary in `tasks.md` for the per-file edit budget (currently ≤ 20 lines across ≤ 8
files, with the last three rows conditional on verification outcomes).

**Note (rules-alignment pass, 2026-04-15):** this proposal was re-reviewed against
the updated `custom-package-isolation.md` rule. The review confirmed the overall
isolation strategy, and applied three small corrections: frontend sub-folder layout
(`components/`, `utils/`, `__tests__/`); backend i18n bundle location
(`grails-app/i18n/custom/`); and promoting the `custom/*` webpack alias from a
conditional to a definite upstream touch (Task 7a) after verifying it's missing.

### Out of scope

- Document upload on the stock card quick-transfer (Transaction-based flow)
- Document type management UI (users select files, not document categories)
- Document deletion from stock transfers
- Changes to the existing DocumentController upload flow

## Impact

| Area                    | Current                                  | After                                     |
|-------------------------|------------------------------------------|-------------------------------------------|
| Stock Transfer Check    | No document capability                   | Upload/download documents inline          |
| Transfer completion     | Always allowed                           | Optionally requires document attachment   |
| Quarantine workflow     | Certificate tracked outside system       | Certificate attached to transfer record   |
| Location configuration  | N/A                                      | New activity code for enforcement         |
