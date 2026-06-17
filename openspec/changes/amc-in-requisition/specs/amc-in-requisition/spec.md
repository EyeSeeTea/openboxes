## ADDED Requirements

### Requirement: AMC column on Requisition create screen
The system SHALL display an **AMC (Average Monthly Consumption)** column beside the existing Demand
column on the Requisition / stock-movement **Create / Add-items page** (requester view). The column
SHALL be read-only and SHALL be visible only when `openboxes.custom.consumption.showAmcInRequisition`
is `true` in the deployment config.

#### Scenario: AMC column appears when flag is on
- **GIVEN** `showAmcInRequisition` is `true`
- **WHEN** a user opens the Add Items page of a stock movement
- **THEN** an "AMC" column is rendered beside the "Demand" column for every line item

#### Scenario: AMC column is hidden when flag is off
- **GIVEN** `showAmcInRequisition` is `false` (default)
- **WHEN** a user opens the Add Items page of a stock movement
- **THEN** no AMC column is rendered; the Demand column is unchanged

### Requirement: AMC column on Requisition edit screen (fulfiller)
The system SHALL display the same AMC column beside Demand on the **Edit / Pick page** (fulfiller
view) in all field-config variants (`AD_HOCK_FIELDS`, `STOCKLIST_FIELDS_PUSH_TYPE`,
`STOCKLIST_FIELDS_PULL_TYPE`), gated by the same flag.

#### Scenario: AMC column appears in all edit-page variants
- **GIVEN** `showAmcInRequisition` is `true`
- **WHEN** a fulfiller views the Edit page for an ad-hoc, push-type, or pull-type stocklist
- **THEN** the AMC column is rendered beside the Demand column in every variant

### Requirement: AMC value is computed from Consumption transactions only
The system SHALL compute AMC using only transactions with `transaction_type_id = '2'`
(`Constants.CONSUMPTION_TRANSACTION_TYPE_ID`). Transfer-out, expiry, damage, and loss transactions
SHALL NOT contribute to the AMC value.

#### Scenario: Only consumption transactions count
- **GIVEN** a product has both Consumption and Transfer-out transactions at the requesting location within the window
- **WHEN** AMC is computed
- **THEN** only the Consumption quantities are summed; Transfer-out quantities are excluded

### Requirement: AMC is scoped to the requesting location
The system SHALL compute AMC using transactions at `requisition.destination` (the requesting
location) on both screens. Fulfilling-location transactions SHALL NOT be included.

#### Scenario: AMC reflects requesting-location consumption
- **GIVEN** a product has Consumption transactions at the requesting location
- **AND** different Consumption transactions at the fulfilling location
- **WHEN** AMC is shown on either screen
- **THEN** the value reflects only the requesting location's consumption

### Requirement: AMC formula matches the Consumption Report
The system SHALL calculate AMC as `SUM(quantity) / windowDays * 30`, where `windowDays` is the
real day-count of the window (not `floor(period / 30)`). This SHALL match the formula used in
`ConsumptionController.groovy:589` so the AMC column cross-checks cleanly against the Consumption
Report.

#### Scenario: AMC matches the Consumption Report
- **GIVEN** a product with known Consumption transactions within the AMC window
- **WHEN** AMC is displayed in the Requisition column
- **THEN** its value equals `Σqty_in_window / windowDays * 30`
- **AND** the same value appears in the Consumption Report for the same product/location/period
  filtered to Consumption type only

#### Scenario: No consumption results in AMC of zero
- **GIVEN** a product has no Consumption transactions at the requesting location within the window
- **WHEN** AMC is computed
- **THEN** the AMC column shows `0` (not blank, not an error)

### Requirement: AMC period is independently configurable
The system SHALL read the AMC window duration from `openboxes.custom.consumption.amcPeriod`,
falling back to `openboxes.forecasting.demandPeriod` (the upstream demand period key) and then to
`365` days. This SHALL keep AMC and Demand period-aligned by default but allow independent tuning
per deployment.

#### Scenario: Default period aligns with demand period
- **GIVEN** `openboxes.custom.consumption.amcPeriod` is not set
- **AND** `openboxes.forecasting.demandPeriod` is set to N
- **WHEN** AMC is computed
- **THEN** the window covers N days before the start of the current month

#### Scenario: Custom period overrides demand period
- **GIVEN** `openboxes.custom.consumption.amcPeriod` is set to M
- **AND** `openboxes.forecasting.demandPeriod` is set to a different N
- **WHEN** AMC is computed
- **THEN** the window covers M days before the start of the current month

### Requirement: Feature flag is delivered to the frontend via session info
The system SHALL expose `openboxes.custom.consumption.showAmcInRequisition` via the `ApiController`
session-info payload and SHALL forward it into `state.session.showAmcInRequisition` in the Redux
store. Column definitions in both JSX components SHALL read this flag before rendering.

#### Scenario: Flag delivered to frontend at session load
- **GIVEN** the backend config sets `showAmcInRequisition: true`
- **WHEN** the frontend loads the session info via `fetchSessionInfo()` (`GET /api/getAppContext`, dispatching `FETCH_SESSION_INFO`)
- **THEN** `sessionReducer` reads `payload.data.data.showAmcInRequisition` and `state.session.showAmcInRequisition` is `true`

#### Scenario: Flag defaults to false
- **GIVEN** `showAmcInRequisition` is not set in the deployment config
- **WHEN** the frontend loads the session info
- **THEN** `state.session.showAmcInRequisition` is `false` and no AMC column is rendered

### Requirement: Demand column is unchanged
The existing Demand column (computed from `ForecastingService.getDemand()`) SHALL remain identical
in value, calculation, and position. Adding the AMC column SHALL NOT affect it.

#### Scenario: Demand value is identical before and after this change
- **WHEN** a product's demand value is read before and after this change is deployed
- **THEN** the values are identical

#### Scenario: Existing Needed-Qty autofill stays demand-based
- **GIVEN** AddItemsPage auto-fills Needed Qty from `monthlyDemand` (`quantityRequested = monthlyDemand − quantityOnHand`)
- **WHEN** this change adds the AMC column
- **THEN** the autofill still derives from `monthlyDemand`, not from `amc` (no swap)

### Requirement: Custom-package isolation
All new business logic SHALL live under `org.pih.warehouse.custom.consumptionDemand`. Upstream
file edits SHALL be additive one-liners (service injection + `amc` field assignment in
`StockMovementService`, flag read in `ApiController`, flag forward in `sessionReducer`, column
definition additions in JSX) and SHALL NOT refactor, reformat, or restructure upstream code.

#### Scenario: New Groovy code is isolated
- **WHEN** the change is reviewed
- **THEN** all new Groovy business logic resides in
  `grails-app/services/org/pih/warehouse/custom/consumptionDemand/ConsumptionDemandService.groovy`

#### Scenario: Upstream edits are additive and documented
- **WHEN** the change is reviewed
- **THEN** every upstream file edit is listed in `design.md` upstream touch points
- **AND** no upstream method is restructured, renamed, reformatted, or removed
