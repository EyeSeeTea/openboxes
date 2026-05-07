## ADDED Requirements

### Requirement: Edit-step API response includes a `quantityPickable` field

The response of `GET /api/stockMovements/{id}/stockMovementItems?stepNumber=3` SHALL include, for every row, an integer `quantityPickable` field equal to the sum of `availableItems[].quantityAvailable` whose `inventoryItem.expirationDate` is null OR strictly greater than or equal to today's date in the server's timezone.

`quantityPickable` SHALL never exceed `quantityAvailable`. `quantityAvailable` retains its existing semantics (the unfiltered total) so consumers other than the Edit step are unaffected.

The field SHALL be computed in `StockMovementService.buildEditPageItems(...)` from the same `availableItems` already loaded for the row — no additional database round trip.

#### Scenario: Response carries quantityPickable for an outbound stock movement

- **WHEN** a client GETs `/api/stockMovements/{id}/stockMovementItems?stepNumber=3` for an outbound stock movement whose origin location has both expired and fresh lots of one of the requested products
- **THEN** the response body's `data[]` array contains a row whose `quantityPickable` equals the sum of `quantityAvailable` of non-expired lots only, while the same row's `quantityAvailable` reflects the unfiltered total

#### Scenario: Response carries quantityPickable for a stock request

- **WHEN** the same endpoint is called for a stock request (request wizard) for an origin with expired stock
- **THEN** the response carries `quantityPickable` populated with the same semantics as the outbound case

#### Scenario: All-expired product reports zero pickable

- **WHEN** a row's only available stock is expired
- **THEN** `quantityPickable` is `0` (not `null`) and `quantityAvailable` is the unfiltered total

#### Scenario: All-fresh product reports equal pickable and available

- **WHEN** a row has no expired lots
- **THEN** `quantityPickable == quantityAvailable`

#### Scenario: Null-expirationDate lots count as pickable

- **WHEN** a row's available items include lots with `inventoryItem.expirationDate == null`
- **THEN** those lots contribute to `quantityPickable` (the rule treats null as not-expired)

### Requirement: Edit-step "lower-than-requested" validator uses pickable, not raw available

The frontend validator that emits `react.stockMovement.errors.lowerQty.label` ("Revise quantity! Quantity available is lower than requested") in both `outbound/EditPage.jsx` and `request/EditPage.jsx` SHALL compare `quantityRequested` against `quantityPickable`, not `quantityAvailable`. The same swap SHALL be applied to the row-level (`font-weight-bold`) and cell-level (`text-danger`) styling conditions.

The validator SHALL fire when the user can't fulfill `quantityRequested` from non-expired stock, blocking advancement to the next wizard step until the user revises the quantity or selects a substitution.

#### Scenario: Insufficient pickable stock blocks Next

- **WHEN** an Edit-step row shows `quantityRequested = 30`, `quantityAvailable = 60`, `quantityPickable = 10`
- **THEN** the row renders with `font-weight-bold` + `text-danger`, the `quantityRevised` cell shows the lowerQty error message, and clicking "Next" / "Save" surfaces the validation error and does not advance the wizard

#### Scenario: Sufficient pickable stock proceeds normally

- **WHEN** an Edit-step row shows `quantityRequested = 30`, `quantityAvailable = 60`, `quantityPickable = 50`
- **THEN** no validation error is emitted on this row and "Next" advances

#### Scenario: Inflated quantityAvailable does not mask the gap

- **WHEN** a row shows `quantityRequested = 30`, `quantityAvailable = 80` (which under the old rule would have suppressed the validator), `quantityPickable = 10`
- **THEN** the validator still fires because the comparison is against `quantityPickable`, not `quantityAvailable`

#### Scenario: Substituted rows are exempt

- **WHEN** a row's `statusCode === 'SUBSTITUTED'`
- **THEN** the validator does not fire on that row regardless of pickable vs requested (preserving the existing exemption)

### Requirement: Edit-step "Available" column surfaces the expired breakdown inline, with the expired count rendered red

When `quantityAvailable > quantityPickable` on a row, the Edit-step "Available" cell SHALL render the pickable value followed by the parenthesised expired tail wrapped in a `<span>` carrying Bootstrap's `text-danger` class. The tail's text comes from the i18n key `outboundExpiryRestrictions.edit.expiredHint` (default `({0} expired)`). When `quantityAvailable === quantityPickable`, the cell SHALL render a single number (existing behaviour preserved).

The pickable count itself follows the row's existing colour rules: red (`text-danger`) when the row triggers the validator, default colour otherwise. The red expired tail is independent — it draws attention to *why* the available number is lower than the user expected, regardless of whether the row triggers the validator.

#### Scenario: Mixed expired and fresh stock — expired count is red

- **WHEN** a row has `quantityAvailable = 83`, `quantityPickable = 33`
- **THEN** the Available cell renders `33 (50 expired)`, the parenthesised `(50 expired)` portion is wrapped in `<span className="text-danger">`, and the leading `33` follows the row's normal colour rules (red if validator fires, default otherwise)

#### Scenario: All-fresh row renders unchanged

- **WHEN** a row has `quantityAvailable = 50`, `quantityPickable = 50`
- **THEN** the Available cell text reads `50` with no parenthesised hint and no `text-danger` span

#### Scenario: All-expired row reads zero with a red expired count

- **WHEN** a row has `quantityAvailable = 50`, `quantityPickable = 0`
- **THEN** the Available cell renders `0 (50 expired)`, the `(50 expired)` portion is wrapped in `<span className="text-danger">`, the row is bold (validator fires for any non-zero requested quantity), and the leading `0` is also red because the row is in the validator-triggered state

### Requirement: No upstream domain or column changes; surgical upstream-file touches only

The implementation SHALL NOT add, modify, or remove any column on upstream tables and SHALL NOT extend upstream domain classes via metaclass, AST, or subclassing. The new field is computed at request time from the existing `inventory_item.expiration_date`.

The only upstream files modified SHALL be:
- `grails-app/services/org/pih/warehouse/inventory/StockMovementService.groovy` (add import + add computation + add map entry; ~4 lines)
- `src/js/components/stock-movement-wizard/outbound/EditPage.jsx` (validator + 2 styling conditions + 1 inline display; ~5 modified lines)
- `src/js/components/stock-movement-wizard/request/EditPage.jsx` (3 field configs × 2 conditions + 1 validator + inline display; ~10 modified lines)

The `EditPageItem` Groovy class at `src/main/groovy/org/pih/warehouse/api/StockMovementItem.groovy:589` SHALL NOT be modified — it is dead code in this code path.

#### Scenario: No new columns added to upstream tables

- **WHEN** the change is applied
- **THEN** `git diff develop..HEAD -- grails-app/migrations/` shows no `addColumn` against any upstream table

#### Scenario: Upstream file diffs are bounded to the documented touch points

- **WHEN** listing files modified by the change against `develop`
- **THEN** the only modified upstream files are the three listed above; net combined diff is under 30 lines

### Requirement: Outbound returns and inbound flows are unaffected

This change SHALL NOT modify the outbound returns wizard (`src/js/components/returns/outbound/`), the inbound receiving wizard, the putaway wizard, the stock-transfer wizard, or any flow that does not call `GET /api/stockMovements/{id}/stockMovementItems?stepNumber=3`.

#### Scenario: Outbound returns wizard is byte-identical

- **WHEN** the change is applied
- **THEN** `git diff develop..HEAD -- src/js/components/returns/` is empty

#### Scenario: Inbound receiving wizard is byte-identical

- **WHEN** the change is applied
- **THEN** `git diff develop..HEAD -- src/js/components/receiving/` is empty

#### Scenario: API consumers other than the Edit step are unaffected

- **WHEN** any consumer reads `quantityAvailable` from any endpoint other than `/api/stockMovements/{id}/stockMovementItems?stepNumber=3`
- **THEN** the value is unchanged from upstream behaviour (`quantityAvailable` is not redefined globally)
