## Why

The Requisition / stock-movement flow shows a **Demand** figure computed from issued requisition
history (`ForecastingService.getDemand()` → `product_demand_details`). Sites that record usage as
**consumption transactions** rather than raising depot requisitions see **Demand = 0**, even though
they clearly consume stock. Requesters and fulfillers have no visibility into actual consumption
when making quantity decisions — they see an empty Demand column and must cross-check the
Consumption Report manually.

## What Changes

- A new read-only **AMC (Average Monthly Consumption)** column is added **beside** the existing
  Demand column on both the requester Create/Add-items page and the fulfiller Edit/Pick page. Both
  columns remain visible: Demand from requisition history, AMC from consumption transactions.
- AMC is derived from **true consumption only** (`TransactionType` id `2`,
  `Constants.CONSUMPTION_TRANSACTION_TYPE_ID`), scoped to the **requesting location**
  (`requisition.destination`) on both screens.
- The column is gated behind a fork-custom feature flag
  (`openboxes.custom.consumption.showAmcInRequisition`, default `false`) so other
  EST/customer deployments are unaffected on upstream pulls.
- All new logic is isolated under the `org.pih.warehouse.custom.consumptionDemand` package and the
  `openboxes.custom.consumption.*` config namespace — upstream file edits are surgical, additive
  one-liners only.

## Capabilities

### New Capabilities
- `amc-in-requisition`: the Requisition create and edit screens display an AMC column (Average
  Monthly Consumption) alongside the existing Demand column, computed from consumption transactions
  at the requesting location, period-aligned with demand by default but independently tunable via
  `openboxes.custom.consumption.amcPeriod`.

### Modified Capabilities
<!-- None — Demand calculation and display are unchanged. -->

## Impact

- **New custom code** (isolated, merge-safe):
  - `grails-app/services/org/pih/warehouse/custom/consumptionDemand/ConsumptionDemandService.groovy`
    — live query + AMC formula (`totalConsumption / windowDays * 30`, matching the Consumption
    Report).
- **Upstream touch points** (surgical, additive — each documented in design.md):
  - `grails-app/services/.../StockMovementService.groovy` — inject service + add `amc` field at
    `getAddPageItem` and `calculateFieldsForElectronicRequisitionItem`.
  - `src/js/components/stock-movement-wizard/request/AddItemsPage.jsx` — add AMC column to
    `NO_STOCKLIST_FIELDS` (and ward variants if shown there).
  - `src/js/components/stock-movement-wizard/request/EditPage.jsx` — add AMC column to
    `AD_HOCK_FIELDS`, `STOCKLIST_FIELDS_PUSH_TYPE`, `STOCKLIST_FIELDS_PULL_TYPE`.
  - `grails-app/controllers/.../api/ApiController.groovy` — emit `showAmcInRequisition` in the
    session-info payload.
  - `src/js/reducers/sessionReducer.jsx` — forward `showAmcInRequisition` into `state.session`.
  - `docker/openboxes.yml` + `docker/openboxes.client-template.yml` — add
    `openboxes.custom.consumption.*` config block.
  - `grails-app/i18n/messages.properties` — add `react.stockMovement.amc.label` + tooltip key
    (root bundle exception to custom-package isolation — only bundle loaded at runtime).
