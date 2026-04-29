# Stock Transfer Document Upload

## What

Add document upload/download capability to stock transfers so that users can attach certificates
(e.g., quarantine release certificates, delivery proofs) to a transfer. Optionally enforce
document attachment before completion via two new activity codes configurable per location:

- **`REQUIRE_TRANSFER_OUT_DOCUMENT`** — applied to either a parent depot location OR a
  specific bin (e.g., a Quarantine bin), enforces the rule when stock is transferred **out
  of** that location/bin (outbound). Use case: release certificate when removing stock from
  quarantine.
- **`REQUIRE_TRANSFER_IN_DOCUMENT`** — applied to either a parent depot location OR a
  specific bin, enforces the rule when stock is transferred **into** that location/bin
  (inbound). Use case: acceptance certificate when placing stock into a controlled bin.

The check walks both the order-header `origin` / `destination` **and** every
`orderItem.originBinLocation` / `orderItem.destinationBinLocation`. If any of those four
sources reports `supports(<flag>)`, completion is blocked until at least one document is
attached. This means admins can apply the flag to a specific bin without having to enable
it on the entire parent depot.

Documents persist on the underlying `Order.documents` relation. A read-only Documents tab
was added to `stockTransfer/show.gsp` (via a custom template) so users see their uploads
after completing a transfer.

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
  is **not** modified. Storage delegates to `Order.addToDocuments(...)` — the same operation
  upstream `DocumentController.uploadDocument` performs for orders.
- **New custom frontend panel** under `src/js/custom/stockTransferDocuments/`
  (split into `components/`, `utils/`, `__tests__/` per `custom-package-isolation.md`)
  — self-contained, mounted into the existing Stock Transfer Check Page via a minimal
  edit (1 import + 1 JSX mount + 1 disabled-prop edit + 1 default-state field).
- Two new activity codes (`REQUIRE_TRANSFER_OUT_DOCUMENT`, `REQUIRE_TRANSFER_IN_DOCUMENT`)
  added to the upstream `ActivityCode` enum and to `ActivityCode.list()`.
- Backend enforcement hook in `StockTransferService.completeStockTransfer()` — a single
  delegating call to the custom service; all logic lives in custom code. The custom
  validator checks four sources:
  - `order.origin.supports(REQUIRE_TRANSFER_OUT_DOCUMENT)` — parent depot enforces outbound
  - `orderItems[].originBinLocation.supports(REQUIRE_TRANSFER_OUT_DOCUMENT)` — any item's source bin enforces outbound (e.g., a Quarantine bin)
  - `order.destination.supports(REQUIRE_TRANSFER_IN_DOCUMENT)` — parent depot enforces inbound
  - `orderItems[].destinationBinLocation.supports(REQUIRE_TRANSFER_IN_DOCUMENT)` — any item's destination bin enforces inbound
  If any of the four returns true and no document is attached, it throws `ValidationException`.
- Strict frontend gate: parent `StockTransferCheckPage` defaults `customCanComplete: false`
  so the Complete button is disabled until the panel finishes loading and explicitly reports
  that completion is allowed (fail-closed). Backend `ValidationException` errors from
  bypassed-frontend completions are surfaced by the existing `apiClient` global response
  interceptor (which already pops a notification toast) — no extra error-handling code in
  the component is needed.
- Post-completion redirect stays at `stockTransfer/show`. A read-only Documents tab was
  added to `stockTransfer/show.gsp` via a custom template so users see their uploads.
- i18n keys for the two new activity codes and all custom-feature React strings appended
  to upstream `grails-app/i18n/messages.properties` (low merge-conflict risk, zero
  config plumbing).

### Upstream compatibility

Per `CLAUDE.md` → *Upstream Compatibility* and `.claude/rules/custom-package-isolation.md`,
this change is designed to stay as isolated as possible. Upstream touch points are
bounded and documented in `design.md` and `tasks.md`. See the Upstream Touch Point
Summary in `tasks.md` for the per-file edit budget (currently ≤ 20 lines across ≤ 8
files, with the last three rows conditional on verification outcomes).

**Note (rules-alignment pass, 2026-04-15):** this proposal was re-reviewed against
the updated `custom-package-isolation.md` rule. The review confirmed the overall
isolation strategy, and applied three small corrections: frontend sub-folder layout
(`components/`, `utils/`, `__tests__/`); backend i18n bundle location; and promoting
the `custom/*` webpack alias from a conditional to a definite upstream touch
(Task 7a) after verifying it's missing.

**Note (post-initial-commit revisions, 2026-04-15):** five follow-up changes after
the first manual UI smoke test:
1. Activity code split into `REQUIRE_TRANSFER_OUT_DOCUMENT` (origin) +
   `REQUIRE_TRANSFER_IN_DOCUMENT` (destination) — single flag was too coarse.
2. Frontend `<Translate>` switched from raw `react-localize-redux` to the project's
   `utils/Translate` wrapper — raw `react-localize-redux` doesn't fall back to
   `defaultMessage`, so labels showed as raw keys.
3. Frontend gate changed from fail-open to **fail-closed** — `customCanComplete`
   defaults to `false` so the Complete button is disabled until the panel confirms.
   No new error-surfacing code: the global `apiClient` interceptor already pops a
   notification toast for any non-2xx response (including the backend
   `ValidationException`), so adding a custom `Alert.error` in the component would be
   a duplicate.
4. Post-completion redirect stays at `stockTransfer/show/<id>`. A read-only
   Documents tab was added to the GSP via a custom template.
5. The custom Grails i18n bundle approach (`grails-app/i18n/custom/`) was abandoned
   in favour of appending keys to upstream `grails-app/i18n/messages.properties`
   directly. Grails 3.3 doesn't auto-load arbitrary `i18n/` sub-folders, so the
   subfolder approach would have required an additional `messageSource` override
   in `resources.groovy`. Appending ~12 lines to `messages.properties` is a smaller
   net upstream touch with no plumbing.
6. **`isDocumentRequired` is now bin-aware.** The first pass only checked
   `order.origin` / `order.destination` (the parent depot). A second smoke test
   surfaced that admins want to enable the activity code on a **specific bin** (e.g.,
   a Quarantine bin), not the whole parent depot. The check now walks both the order
   header **and** every `orderItem.originBinLocation` / `orderItem.destinationBinLocation`.
   See design.md → *Custom Document Service* for the full rule. This is the load-bearing
   change for the quarantine-release workflow.

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
