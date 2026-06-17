## 1. Custom service

- [ ] 1.1 Create `grails-app/services/org/pih/warehouse/custom/consumptionDemand/ConsumptionDemandService.groovy` with method `getMonthlyConsumption(Location location, Product product)`.
- [ ] 1.2 Implement the live SQL query: Consumption type id `2` only, scoped to location + product, bounded by `startDate`/`endDate`. **No `confirmed` filter** — `getDebitsBetweenDates` (the Consumption Report's fetch) doesn't filter on it, and consumption transactions are scoped by `transaction.inventory` (`location.inventory_id = transaction.inventory_id`), matching this query (Resolved OQ#2).
- [ ] 1.3 Apply the Consumption Report AMC formula: `amc = SUM(quantity) / windowDays * 30` (NOT demand's `÷ floor(period/30)`).
- [ ] 1.4 Resolve `amcPeriod` via `openboxes.custom.consumption.amcPeriod ?: forecasting.demandPeriod ?: 365`; compute window bounds (first day of current month − amcPeriod → end of previous month); derive `windowDays` as `toDate − fromDate` with the **same inclusive bounds the Consumption Report uses** (`command.numberOfDays`) so the `/windowDays*30` divisor reconciles exactly (Resolved OQ#2).
- [ ] 1.5 Return `0` (not null, not an exception) when no Consumption transactions exist in the window for that product/location.

## 2. Config

- [ ] 2.1 Add `openboxes.custom.consumption.showAmcInRequisition: true` and `amcPeriod: 365` under the `openboxes.custom.*` block in `docker/openboxes.yml`.
- [ ] 2.2 Add a commented `openboxes.custom.consumption.*` block in `docker/openboxes.client-template.yml` (default `showAmcInRequisition: false`).

## 3. Backend injection into StockMovementService (upstream — surgical)

- [ ] 3.1 Declare `def consumptionDemandService` at the top of `StockMovementService.groovy`.
- [ ] 3.2 In `getAddPageItem`, add an `amc` entry beside `monthlyDemand` in **both** item maps (lines ~884 and ~911), calling `consumptionDemandService.getMonthlyConsumption(requisition.destination, product)`.
- [ ] 3.3 In `calculateFieldsForElectronicRequisitionItem` (line ~990), add `amc` beside `quantityDemandRequesting` (~998) in the same way.
- [ ] 3.4 In `buildEditPageItems` (~1014), add an `amc` entry beside `quantityDemandFulfilling` (~1066) — but compute it from `requisition.destination` (NOT `origin`), so the stocklist EditPage variants show requesting-location AMC. (Resolved OQ#1; intentionally overrides the draft's "don't touch `buildEditPageItems`" note.)
- [ ] 3.5 Leave the `averageMonthlyDemand` site at line ~719 untouched — it is `getPendingRequisitionDetails(Location origin, …)`, an origin-scoped pending-requisition helper, not one of the two target screens (verified during spec review).

## 4. Feature flag — ApiController + sessionReducer (upstream — surgical)

- [ ] 4.1 In `ApiController.groovy`, read `grailsApplication.config.openboxes.custom.consumption.showAmcInRequisition` (default `false`) and include it in the session-info `render([data:[...]])` payload.
- [ ] 4.2 In `src/js/reducers/sessionReducer.jsx`, forward `showAmcInRequisition` from the API response into `state.session` (same pattern as PR #15).

## 5. Frontend columns (upstream — additive)

- [ ] 5.1 In `AddItemsPage.jsx`, add AMC column definition to `NO_STOCKLIST_FIELDS`: header = i18n `react.stockMovement.amc.label`, tooltip = Average Monthly Consumption, value = `amc`, visible only when `state.session.showAmcInRequisition` is `true`.
- [ ] 5.2 Decide (Open Question #3) which of AddItemsPage's other four configs get the column — `STOCKLIST_FIELDS_PUSH_TYPE`, `STOCKLIST_FIELDS_PULL_TYPE`, `REQUEST_FROM_WARD_STOCKLIST_FIELDS_PUSH_TYPE`, `REQUEST_FROM_WARD_STOCKLIST_FIELDS_PULL_TYPE` — based on whether TJK uses those request flows; add where confirmed.
- [ ] 5.3 In `EditPage.jsx`, add the AMC column to all three configs — `AD_HOCK_FIELDS`, `STOCKLIST_FIELDS_PUSH_TYPE`, `STOCKLIST_FIELDS_PULL_TYPE`. All three now carry `amc` (ad-hoc via `calculateFieldsForElectronicRequisitionItem`, stocklist via `buildEditPageItems` — see task 3.4). Placement: `AD_HOCK_FIELDS` shows **two** demand columns — "Demand" (`quantityDemandRequesting`) and "Demand per Month" (`quantityDemandFulfilling`) — put AMC beside the **requesting** "Demand" column. The stocklist variants show only "Demand per Month" (`quantityDemandFulfilling`); AMC there is requesting-location consumption beside fulfilling-location demand (intended — see OQ#1).
- [ ] 5.4 Decide AMC display type (Open Question #4): rounded integer to match the Demand column, or decimal per the Consumption Report's `###.#` format. Apply consistently across both screens.

## 6. i18n

- [ ] 6.1 Append `react.stockMovement.amc.label = AMC` to `grails-app/i18n/messages.properties`.
- [ ] 6.2 Append `react.stockMovement.amc.tooltip = Average Monthly Consumption` to `grails-app/i18n/messages.properties` (or inline in JSX if no tooltip i18n pattern exists in those components).

## 7. Automated tests

- [ ] 7.1 Spock unit test for `ConsumptionDemandService`: seed Consumption transactions inside and outside the window, for the correct and incorrect location/product — assert the returned value equals `Σqty / windowDays * 30`.
- [ ] 7.2 Spock test for zero-consumption edge case: no transactions in the window → AMC = `0`.
- [ ] 7.3 Jest test for `AddItemsPage`: AMC column renders when `showAmcInRequisition = true`, absent when `false`.
- [ ] 7.4 Jest test for `EditPage`: AMC column present in all three field-config variants when flag is on.

## 8. Verify

- [ ] 8.1 Seed Consumption transactions via the API (see testing checklist in draft) and confirm the AMC column displays `Σqty / windowDays * 30` on both screens.
- [ ] 8.2 Cross-check displayed AMC against the Consumption Report for the same product/location/period (Consumption type only) — values must reconcile.
- [ ] 8.3 Confirm Demand column values are unchanged on both screens.
- [ ] 8.3a Confirm the existing Needed Qty autofill on AddItemsPage still derives from `monthlyDemand` (not AMC) — pick a product, verify the auto-filled quantity still equals `monthlyDemand − QOH`/`− available`, unchanged by this change.
- [ ] 8.4 Toggle `showAmcInRequisition: false` → column hidden on both screens; set to `true` → column visible.
- [ ] 8.5 Confirm edge case: product with no consumption history → AMC shows `0`, not blank/error.
- [ ] 8.6 `git diff` check: only `ConsumptionDemandService.groovy`, the `StockMovementService` edits (service injection + `amc` at the four demand sites: 884, 911, 998, ~1066), `AddItemsPage.jsx`, `EditPage.jsx`, `ApiController.groovy`, `sessionReducer.jsx`, `docker/openboxes.yml`, `docker/openboxes.client-template.yml`, `messages.properties`, and test files. No unrelated upstream edits.

## 9. Docs

- [ ] 9.1 Update `design.md` upstream touch points to match final line numbers after implementation.
- [ ] 9.2 Set "Deploy status" in `design.md` once the branch is merged.
