## Context

OpenBoxes computes a **Demand** figure for each item in a stock-movement (requisition) by querying
issued requisition history via `ForecastingService.getDemand()`. Sites that record stock usage as
**Consumption transactions** (transaction type id `2`) rather than by raising depot requisitions
see `Demand = 0`. Those sites need an **AMC (Average Monthly Consumption)** column beside Demand
so requesters and fulfillers can compare against actual consumption when deciding quantities.

The upstream codebase already surfaces consumption data via the **Consumption Report**
(`ConsumptionController.groovy`), which uses the formula `amc = totalConsumption / windowDays * 30`
(where `windowDays` = actual day-count of the window). That formula is intentionally re-used here
so the AMC column matches the Consumption Report cross-check. The upstream demand formula
(`total ÷ floor(period / 30)`) differs by ~1.4% at 365 days — using the wrong formula would make
the two numbers irreconcilable.

## Goals / Non-Goals

**Goals:**
- Surface AMC beside Demand on both the Requisition create (Add Items) and edit (Fulfiller) pages.
- AMC = requesting-location consumption only, using Consumption transactions only (type id `2`).
- Formula matches the Consumption Report: `totalConsumption / windowDays * 30`.
- Feature flag `openboxes.custom.consumption.showAmcInRequisition` (default `false`) keeps the
  column opt-in per deployment.
- All new logic isolated under `org.pih.warehouse.custom.consumptionDemand`; upstream edits are
  surgical, additive one-liners, each documented below.

**Non-Goals:**
- Changing or replacing the Demand column (remains as-is).
- Showing AMC for the fulfilling location.
- Auto-fill or swapping quantities based on AMC. **Note:** AddItemsPage already auto-fills Needed Qty
  from demand (`quantityRequested = monthlyDemand − quantityOnHand` / `− quantityAvailable`, see
  `AddItemsPage.jsx:1516,1537` and `calculateQtyRequestedFrom: 'monthlyDemand'` at line 42). That calc
  stays on `monthlyDemand` — we do **not** redirect it to `amc`. AMC is a read-only display column only.
- A global/cross-feature effect (stock card, forecast report, requisition all consumption-based). This
  change is **localized to the requisition flow only** (per approved scope; the senior dev's
  global-vs-local question is resolved as local).
- Modifying the upstream Consumption Report or ForecastingService.
- Supporting non-Consumption transaction types (transfer-out, expiry, damage, loss) — consumption
  type `2` only (the senior dev's "do we include expired/damaged/lost" is resolved as no).

## Decisions

**1. Formula: Consumption Report's `total / windowDays × 30`, not demand's `÷ floor(period/30)`.**
Chosen so the AMC column reconciles with the Consumption Report cross-check. The ~1.4% divergence
at 365 days grows for other ranges, making the two numbers misleadingly different if demand's
formula were used. `windowDays` = actual day-count of the window, consistent with the period.
Reference: `grails-app/controllers/org/pih/warehouse/reporting/ConsumptionController.groovy:588-589`
(`Float getMonthlyQuantity() { totalConsumptionQuantity / command.numberOfDays * 30 }`).

**2. Window: `openboxes.custom.consumption.amcPeriod`, defaulting to `forecasting.demandPeriod`.**
Keeps AMC and Demand period-aligned out of the box while allowing independent tuning per
deployment. Window bounds mirror demand: first day of current month − amcPeriod → end of previous
month. Declared under `openboxes.custom.*` namespace (sibling of `adjustments` / `dhis2` blocks)
in `docker/openboxes.yml` + client-template — **not** in upstream `application.yml` (Upstream
Compatibility rule 6).

```groovy
Integer amcPeriod = config.openboxes.custom.consumption.amcPeriod
                 ?: config.openboxes.forecasting.demandPeriod  // upstream key — align by default
                 ?: 365
```

**3. Location: requesting location only (`requisition.destination`).**
Both screens (create and edit) show requesting-location AMC. No fulfilling-location AMC column.
The fulfiller sees what the destination site actually consumes, which is the decision-relevant
figure for quantity approval.

**4. Feature flag via `ApiController` → `sessionReducer` channel (PR #15 pattern).**
`openboxes.custom.consumption.showAmcInRequisition` (default `false`) is read in `ApiController`
and emitted in the session-info `render` payload, then forwarded into `state.session` by
`sessionReducer.jsx`. The column definitions in both JSX components read the flag before
rendering. Other deployments see no visible change.

**5. New custom service, surgical injection into `StockMovementService`.**
`ConsumptionDemandService` owns the query and formula entirely. `StockMovementService` gets the
service injection plus additive `amc` entries beside the demand field at the relevant item-map
construction sites. Verified sites (current `develop`):
- `getAddPageItem` builds **two** item maps, each with `monthlyDemand` at lines **884** and **911**
  — both need an `amc` entry (the draft's "~870/892/895" was approximate).
- `calculateFieldsForElectronicRequisitionItem` (line **990**) sets `quantityDemandRequesting` at
  line **998** — add `amc` beside it (feeds `AD_HOCK_FIELDS` on EditPage).
- `buildEditPageItems` (line **1014**) sets `quantityDemandFulfilling` (lines 1055/1066) — **add
  `amc` here too**, using `requisition.destination` (NOT `origin`), so the stocklist EditPage variants
  (`STOCKLIST_FIELDS_PUSH_TYPE` / `PULL_TYPE`) can show requesting-location AMC. See Resolved
  Question OQ#1 — this intentionally overrides the draft's "don't touch `buildEditPageItems`" note.
- The `averageMonthlyDemand` site at line **719** is `getPendingRequisitionDetails(Location origin, …)`,
  an origin-scoped pending-requisition helper — **not** one of the two target screens; leave untouched.
Nothing else in the upstream service is touched.

**6. i18n goes into the root bundle (`messages.properties`).**
The root bundle is the only i18n bundle loaded by the React runtime. This is the accepted
exception to custom-package isolation and matches the upstream convention.

## Architecture: ConsumptionDemandService query

```sql
SELECT SUM(te.quantity)
FROM   transaction_entry te
JOIN   transaction t     ON t.id = te.transaction_id
JOIN   location l        ON l.inventory_id = t.inventory_id
JOIN   inventory_item ii ON ii.id = te.inventory_item_id
WHERE  t.transaction_type_id = '2'         -- Constants.CONSUMPTION_TRANSACTION_TYPE_ID
  AND  ii.product_id          = :productId
  AND  l.id                   = :locationId
  AND  t.transaction_date BETWEEN :startDate AND :endDate
```

Returns `amc = SUM / windowDays * 30` (or `0` when no rows match).

## Risks / Trade-offs

- **N+1 per line item** — `getAddPageItem` is called once per requisition line; the consumption
  query runs per call. Mirrors the existing demand query pattern. The query is a single SUM on
  columns expected to be indexed; batch or cache if performance testing reveals an issue.
- **Formula divergence from demand** — intentional and documented here; reviewers may flag it.
  This design.md is the paper trail.
- **Upstream touch points are merge conflict candidates** — the two `StockMovementService`
  one-liners and the JSX field-config arrays are the most likely conflict sites on future upstream
  pulls. Each is listed below.

## Migration Plan

Additive. No DB schema change, no data migration, no Liquibase changeset. The service reads
existing `transaction_entry` rows. Feature flag defaults to `false` — safe to deploy to all
branches with no visible change; set `showAmcInRequisition: true` per client in
`docker/openboxes.yml`.

## Upstream touch points

| File | Edit | Reason |
|---|---|---|
| `grails-app/services/org/pih/warehouse/inventory/StockMovementService.groovy` | Inject `def consumptionDemandService`; add `amc` field (all using `requisition.destination`) at both `getAddPageItem` map sites (884 + 911), `calculateFieldsForElectronicRequisitionItem` (998), and `buildEditPageItems` (~1066) | Surface requesting-location AMC alongside demand in item maps on both screens (incl. stocklist edit variants — see OQ#1) |
| `src/js/components/stock-movement-wizard/request/AddItemsPage.jsx` | Add AMC column def to `NO_STOCKLIST_FIELDS` (and ward variants if present) | Show column on requester create page |
| `src/js/components/stock-movement-wizard/request/EditPage.jsx` | Add AMC column def to `AD_HOCK_FIELDS`, `STOCKLIST_FIELDS_PUSH_TYPE`, `STOCKLIST_FIELDS_PULL_TYPE` | Show column on fulfiller edit page (all variants) |
| `grails-app/controllers/org/pih/warehouse/api/ApiController.groovy` | Read + emit `showAmcInRequisition` in session-info `render` payload | Deliver feature flag to frontend |
| `src/js/reducers/sessionReducer.jsx` | Forward `showAmcInRequisition` into `state.session` | Column reads flag from Redux state |
| `docker/openboxes.yml` | Add `openboxes.custom.consumption.showAmcInRequisition: true` + `amcPeriod: 365` under `openboxes.custom.*` | TJK client config |
| `docker/openboxes.client-template.yml` | Add commented `openboxes.custom.consumption.*` block | Template for other deployments |
| `grails-app/i18n/messages.properties` | Add `react.stockMovement.amc.label = AMC` and tooltip key | i18n for AMC column (root bundle required) |

Custom (isolated, no upstream conflict):
- `grails-app/services/org/pih/warehouse/custom/consumptionDemand/ConsumptionDemandService.groovy`
  — live query + AMC formula.
- `src/js/custom/amcInRequisition/utils/amcColumn.js` — pure column helpers (`formatAmc`,
  `withAmcColumn`, `stripAmcColumn`) imported by both AddItemsPage and EditPage (matches the existing
  `custom/outboundExpiryRestrictions/utils/expiryHelpers` import precedent in EditPage). Unit-tested.
- `src/integration-test/groovy/org/pih/warehouse/custom/consumptionDemand/ConsumptionDemandServiceIntegrationSpec.groovy`
  — formula + window + zero-consumption tests.
- `src/js/custom/amcInRequisition/__tests__/amcColumn.test.jsx` — column-gating + formatter tests.

**Implementation note (line numbers):** the `StockMovementService` edits are the service injection
(beside `forecastingService`, ~line 104) plus an `amc` entry at the four demand sites — both
`getAddPageItem` maps (after `monthlyDemand`), `calculateFieldsForElectronicRequisitionItem` (after
`quantityDemandRequesting`), and `buildEditPageItems` (after `quantityDemandFulfilling`, scoped to
`requisition.destination`). On the frontend, AMC is added to the four **demand-bearing** AddItemsPage
configs (`NO_STOCKLIST_FIELDS`, `STOCKLIST_FIELDS_PULL_TYPE`,
`REQUEST_FROM_WARD_STOCKLIST_FIELDS_PULL_TYPE`, `REQUEST_FROM_WARD_FIELDS`) and all three EditPage
configs; the two push-type configs are excluded (no Demand column — see Resolved OQ#3).

## Deploy status

- Implemented on: `feat/tjk/amc-in-requisition` (current working branch).
- Replayed onto other customer branches: none yet.
- Submitted upstream: no.

## Resolved Questions

- **OQ#1 — EditPage stocklist variants (RESOLVED: add AMC to all three EditPage configs; compute
  requesting-location AMC in `buildEditPageItems`).** Verified the three configs:
  `AD_HOCK_FIELDS` carries both `quantityDemandRequesting` (line 137) and `quantityDemandFulfilling`
  (216), fed by `calculateFieldsForElectronicRequisitionItem`; `STOCKLIST_FIELDS_PUSH_TYPE` (517) and
  `STOCKLIST_FIELDS_PULL_TYPE` (818) carry only `quantityDemandFulfilling`, fed by
  `buildEditPageItems`. The approved scope is explicit — requesting-location AMC on **both** screens,
  and stocklist push/pull is the primary fulfiller flow — so AMC must appear on the stocklist edit
  variants too. That requires adding `amc: consumptionDemandService.getMonthlyConsumption(
  requisition.destination, product)` in `buildEditPageItems` (using `destination`, NOT `origin`).
  **This overrides the draft's "do NOT add AMC in `buildEditPageItems`" note** — that note assumed the
  builder only carries fulfilling demand, but requesting-location AMC there is consistent with scope.
  Note the resulting column pairing on stocklist edit screens is Demand(fulfilling) | AMC(requesting),
  which is intended: the fulfiller judges the request against what the destination actually consumes.
- **OQ#2 — Row-selection parity with the Consumption Report (RESOLVED: drafted query already
  reconciles; no `confirmed` filter).** `InventoryService.getDebitsBetweenDates` (line 2609) filters
  only on `transactionType.transactionCode = DEBIT`, optional `transactionType in [...]`, location, and
  `transactionDate between` — there is **no `confirmed` filter**, so the custom query must **not** add
  one either. Consumption transactions have `destination IS NULL` and are scoped by
  `transaction.inventory`, which matches our `location.inventory_id = transaction.inventory_id` join.
  Filtering on `transaction_type_id = '2'` (Consumption) is a strict subset of the report's DEBIT
  filter, so no extra `transactionCode` predicate is needed. **Action:** keep the drafted query as-is,
  and compute `windowDays` exactly as the report does (`numberOfDays = toDate − fromDate`, same
  inclusive bounds) so the divisor matches.

## Resolved Questions (continued)

- **OQ#3 — Which AddItemsPage variants get the column (RESOLVED: the four demand-bearing configs;
  push-type configs excluded).** Implementation found AddItemsPage actually has **six** line-item
  configs, not five — the task list omitted `REQUEST_FROM_WARD_FIELDS` (the ward ad-hoc flow). TJK
  confirmed it uses ward requests. AMC is therefore added to the four configs that display a Demand
  column: `NO_STOCKLIST_FIELDS` (monthlyDemand), `STOCKLIST_FIELDS_PULL_TYPE`
  (demandPerReplenishmentPeriod), `REQUEST_FROM_WARD_STOCKLIST_FIELDS_PULL_TYPE`
  (demandPerReplenishmentPeriod), and `REQUEST_FROM_WARD_FIELDS` (monthlyDemand). The two **push-type**
  configs (`STOCKLIST_FIELDS_PUSH_TYPE`, `REQUEST_FROM_WARD_STOCKLIST_FIELDS_PUSH_TYPE`) display
  `quantityAllowed` (replenish-to-par max), **not** Demand, so AMC has no Demand column to sit beside
  there — excluded. Easy to add later if a push flow needs it.
- **OQ#4 — AMC display type (RESOLVED: one decimal place).** AMC renders to one decimal place
  (`Math.round(value * 10) / 10`) to match the Consumption Report's `###.#` format, so the column
  reconciles with the report and small monthly rates don't collapse to `0`. (Superseded an earlier
  rounded-integer decision, which hid sub-1.0 monthly rates during live QA.) The **service still
  returns the raw** `SUM / windowDays * 30` BigDecimal — rounding is display-only (in the JSX
  `formatValue`), so the Consumption-Report reconciliation (spec scenario / task 7.1) holds against
  the unrounded value.
- **OQ#5 — N+1 query cost (RESOLVED: accept per-line-item, gated by the feature flag).** The
  consumption query runs once per line item, mirroring the existing demand-query pattern in the same
  methods. No batch overload added; it is a single indexed SUM. **`getMonthlyConsumption` now
  short-circuits to `0` without querying when `openboxes.custom.consumption.showAmcInRequisition` is
  off** (the default), so deployments that don't surface the column pay zero query cost — mirroring how
  `ForecastingService.getDemand` short-circuits on `forecasting.enabled`. (Added after code review
  flagged that the flag previously gated only the frontend render, not the backend computation.)
  Revisit with a `getMonthlyConsumption(Location, List<Product>)` batch overload only if performance
  testing on large requisitions with the flag *on* shows a problem (noted under Risks).

(Resolved during spec review: the `averageMonthlyDemand` site at line ~719 is
`getPendingRequisitionDetails(Location origin, …)` — an origin-scoped pending-requisition helper, not
one of the two target screens; leave it untouched. See task 3.5.)
