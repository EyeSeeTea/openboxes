## ADDED Requirements

### Requirement: Expired lots are unselectable in the outbound Pick step

When a user opens the **Edit Pick** modal during the Pick step of the outbound stock-movement wizard for a stock movement whose `stockMovementType` is `STOCK_MOVEMENT`, every available-item row whose lot is expired SHALL render with disabled styling and SHALL refuse keyboard or mouse input on the `quantityPicked` field. The lot's `expirationDate` SHALL remain visible.

An inventory item is expired when its `expirationDate` is non-null and strictly less than the current date in the server's timezone.

#### Scenario: Expired lot row is visibly disabled

- **WHEN** the user opens the Edit Pick modal on a stockMovementItem whose available items include a lot whose `expirationDate` is in the past
- **THEN** that row is rendered with disabled styling (the existing `text-disabled` class), the `quantityPicked` input has the `disabled` attribute, and a tooltip on the row reads "Cannot ship — expired" with the formatted expiration date

#### Scenario: Non-expired lot remains pickable

- **WHEN** the same modal renders a row whose lot's `expirationDate` is null, today, or in the future
- **THEN** the row is rendered normally and the `quantityPicked` input accepts input as upstream

#### Scenario: Mixed bin shows expired and fresh side by side

- **WHEN** a single bin has multiple lots — one expired, one fresh — for the same stockMovementItem
- **THEN** both rows are rendered; the expired row is disabled, the fresh row is selectable

### Requirement: Outbound returns and inbound flows are unaffected

Outbound `RETURN_ORDER` movements live in a separate component tree (`src/js/components/returns/outbound/`) and do not reach the outbound Pick step. The change SHALL NOT modify the returns wizard, the inbound wizard, the stock-transfer wizard, or any picker outside `EditPickModal` rendered for the outbound `STOCK_MOVEMENT` Pick step.

#### Scenario: Returns wizard is byte-identical

- **WHEN** the user creates an outbound return through `CreateOutboundReturn.jsx`
- **THEN** the file's diff against develop is empty and the rendering of expired lots in any picker the returns wizard uses is unchanged from upstream

#### Scenario: Inbound wizard is byte-identical

- **WHEN** the user receives stock through any inbound wizard step
- **THEN** the inbound wizard's pickers, lot pickers, and item-edit modals render expired lots exactly as upstream

### Requirement: Server rejects expired-lot picklist submissions for STOCK_MOVEMENT

The backend SHALL reject any HTTP request to `POST /openboxes/api/stockMovementItems/<id>/updatePicklist` when (a) the parent `OutboundStockMovement.stockMovementType` is `STOCK_MOVEMENT` and (b) the request body's `picklistItems[]` contains at least one item referencing an `inventoryItem.id` whose lot is expired.

The rejection SHALL respond with HTTP `400 Bad Request` and a JSON body containing an i18n message keyed `outboundExpiryRestrictions.expired.cannotShip`, interpolated with the offending lot's `productCode`, `lotNumber`, and `expirationDate`.

The guard SHALL NOT run for `RETURN_ORDER` movements, inbound movements, or any controller other than `stockMovementItemApi`.

#### Scenario: API call submitting an expired lot is rejected

- **WHEN** a client POSTs to `/openboxes/api/stockMovementItems/<id>/updatePicklist` for a stockMovementItem whose parent movement is `STOCK_MOVEMENT`, with a `picklistItems[]` entry referencing an `inventoryItem.id` whose lot expired yesterday
- **THEN** the response is HTTP 400 with `{"errorCode": "outboundExpiryRestrictions.expired.cannotShip", "errorMessages": [...]}` and `stockMovementService.updatePicklistItem` is not called

#### Scenario: API call with all fresh lots is accepted

- **WHEN** the same shape of request references only fresh lots
- **THEN** the request proceeds to the upstream controller action and persists normally

#### Scenario: API call with null expirationDate is accepted

- **WHEN** at least one referenced inventory item has `expirationDate == null`
- **THEN** the request proceeds (the rule treats null as not-expired)

#### Scenario: API call against a RETURN_ORDER stockMovementItem is accepted

- **WHEN** the parent movement's `stockMovementType` is `RETURN_ORDER` (defence-in-depth — the UI does not reach this endpoint for returns, but the test confirms no false-positive)
- **THEN** the request proceeds regardless of expired lots in the payload

### Requirement: No upstream domain or column changes

The implementation SHALL NOT add, modify, or remove any column on upstream tables (`inventory_item`, `stock_movement`, `requisition_item`, etc.) and SHALL NOT extend upstream domain classes via metaclass, AST, or subclassing. All custom code SHALL live under `org.pih.warehouse.custom.outboundExpiryRestrictions` (backend) and `src/js/custom/outboundExpiryRestrictions/` (frontend).

The only upstream file edited SHALL be `src/js/components/stock-movement-wizard/outbound/PickPage.jsx`, with a diff equivalent to two lines of change (one import, one component reference).

#### Scenario: No new columns added to upstream tables

- **WHEN** the change is applied
- **THEN** `git diff develop..HEAD -- grails-app/migrations/` shows no `addColumn` against any upstream table

#### Scenario: All new files are under custom paths

- **WHEN** listing files added by the change
- **THEN** every new `.groovy` / `.java` file path contains `org/pih/warehouse/custom/outboundExpiryRestrictions/`, every new `.jsx` / `.scss` file path contains `src/js/custom/outboundExpiryRestrictions/`, and the only upstream file modified is `src/js/components/stock-movement-wizard/outbound/PickPage.jsx`
