## 1. Pre-implementation checks

- [x] 1.1 Re-confirm `StockMovementService.buildEditPageItems` insertion site by reading `grails-app/services/org/pih/warehouse/inventory/StockMovementService.groovy:1013-1095`. Verify that `quantityAvailable` (line 1050) and `quantityOnHand` (line 1051) are still computed inline and that the row map literal still starts at line 1054. If the structure has shifted on this branch, adjust task 2.1 line references.
- [x] 1.2 Re-confirm the validator location and predicate in both wizard files: `outbound/EditPage.jsx:411-417` and `request/EditPage.jsx:1094-1106`. They should both still test `quantityRequested > quantityAvailable` against `react.stockMovement.errors.lowerQty.label`.
- [x] 1.3 Re-confirm the field-config blocks in `request/EditPage.jsx`: rows at 55, 367, 667 and cells at 202, 502, 802. If line numbers drift, capture the new ones for tasks 3 and 4.
- [x] 1.4 Confirm `ExpiryRule` is on this branch: `git log --oneline -- src/main/groovy/org/pih/warehouse/custom/outboundExpiryRestrictions/support/ExpiryRule.groovy` should show `12531d258`. The change depends on it.

## 2. Backend — `quantityPickable` in `buildEditPageItems`

- [x] 2.1 In `grails-app/services/org/pih/warehouse/inventory/StockMovementService.groovy`:
  - Add `import org.pih.warehouse.custom.outboundExpiryRestrictions.support.ExpiryRule` to the import block (kept alphabetised with the existing `org.pih.warehouse.custom.*` imports if any; otherwise immediately after the existing `org.pih.warehouse.api.*` imports).
  - Insert the `quantityPickable` computation immediately after line 1051, alongside `quantityAvailable` and `quantityOnHand`:
    ```groovy
    def quantityPickable = availableItems
            ?.findAll { it.quantityAvailable > 0 && !ExpiryRule.isExpired(it.inventoryItem?.expirationDate) }
            ?.sum { it.quantityAvailable }
    ```
  - Insert one entry into the row map literal, immediately after the existing `quantityAvailable` line (~1065):
    ```groovy
    quantityPickable        : (quantityPickable && quantityPickable > 0 ? quantityPickable : 0),
    ```
  - Net: 1 import + 3 added lines. No other changes to `buildEditPageItems`.
- [x] 2.2 Spock spec at `src/test/groovy/org/pih/warehouse/custom/outboundExpiryRestrictions/StockMovementServiceQuantityPickableSpec.groovy` (this is a custom test file even though the production change is on an upstream service — keep the spec under the custom test tree per `rules/custom-package-isolation.md`):
  - Test against the static helper logic: build a list of `AvailableItem` fixtures with mixed expirationDates and assert that summing the non-expired ones matches the expected `quantityPickable`. If extracting a helper from `buildEditPageItems` is cheap, prefer that — otherwise mock `ExpiryRule.isExpired` and assert the filter predicate.
  - Cover: all-expired → 0, all-fresh → sum, mixed → fresh sum, null-expirationDate → counted as fresh, today's date → counted as fresh (strict-`<` semantics).

## 3. Frontend — outbound wizard updates

- [x] 3.1 In `src/js/components/stock-movement-wizard/outbound/EditPage.jsx`:
  - Line ~53 (row className condition): change `rowValues.quantityAvailable < rowValues.quantityRequested` to `rowValues.quantityPickable < rowValues.quantityRequested`.
  - Line ~137 (cell className condition for the Available cell): change `fieldValue.quantityAvailable < fieldValue.quantityRequested` to `fieldValue.quantityPickable < fieldValue.quantityRequested`. Also handle the leading `!fieldValue.quantityAvailable` short-circuit — change to `!fieldValue.quantityPickable`.
  - Line ~415-416 (validator): change `item.quantityRequested > item.quantityAvailable` to `item.quantityRequested > item.quantityPickable`.
  - Line ~147 (cell `formatValue`): switch from the inline string-returning lambda to the extracted helper from task 4.1: `formatValue: renderAvailableCell(translate)`. The renderer returns a `<>...</>` fragment with `<span className="text-danger">` around the parenthesised expired tail. Confirm `LabelField` accepts a JSX-returning `formatValue` (most field-element types in `src/js/components/form-elements/` do — verify by grep if uncertain). If it doesn't, swap to a small dedicated cell renderer wrapping `LabelField`.
- [x] 3.2 No new test file. The validator behaviour is exercised manually in task 6.

## 4. Frontend — request wizard updates

- [x] 4.1 In `src/js/components/stock-movement-wizard/request/EditPage.jsx`, apply the same swap and JSX `formatValue` change as task 3.1 for each of the three field-config blocks:
  - **AD_HOCK_FIELDS:** row condition at ~55, cell condition at ~202, cell `formatValue` near ~210.
  - **STOCKLIST_FIELDS_PUSH_TYPE:** row condition at ~367, cell condition at ~502, cell `formatValue` nearby.
  - **STOCKLIST_FIELDS_PULL_TYPE:** row condition at ~667, cell condition at ~802, cell `formatValue` nearby.

  To avoid duplicating the JSX block four times (3 in request + 1 in outbound), extract the renderer into the existing custom helpers file at `src/js/custom/outboundExpiryRestrictions/utils/expiryHelpers.js` — exported as `renderAvailableCell({ translate })` (curried so the wizard can pass its connected `translate`, matching how `buildExpiredTooltip` is consumed today). Both `EditPage.jsx` files import it. Helper signature: `(translate) => (value) => ReactNode | string`. Add a Jest test for the helper in `src/js/custom/outboundExpiryRestrictions/__tests__/expiryHelpers.test.js` covering all-fresh / mixed / all-expired cases.
- [x] 4.2 The shared validator at line ~1094-1106: change `item.quantityRequested > item.quantityAvailable` to `item.quantityRequested > item.quantityPickable`. Single line.
- [x] 4.3 No new test file. Manual exercise in task 6.

## 5. i18n

- [x] 5.1 Add to `grails-app/i18n/custom/outboundExpiryRestrictions-messages.properties`:
  ```
  outboundExpiryRestrictions.edit.expiredHint=({0} expired)
  ```
  No new file — this is the same custom messages file from the prior change.
- [x] 5.2 Confirm `crowdin.yml` already covers this glob (`/**/grails-app/i18n/custom/*-messages.properties`) — added in the prior change. No edit needed.

## 6. Pre-archive

- [x] 6.1 `./gradlew test` — green; the new Spock spec passes.
- [x] 6.2 `openspec validate expiry-aware-edit-availability` — passes.
- [x] 6.3 Manually exercise in three flows against a seeded warehouse with one product that has 10 fresh + 50 expired lots and a stockMovement requesting quantity 30:
  - **Outbound stock movement Edit step:** "Available" cell reads `10 (50 expired)`, the row is bold red, "Next" is blocked with the "Revise quantity!" message. Lower the request to 5 → row renders normally, "Next" advances.
  - **Request wizard (PULL stocklist) Edit step:** same scenario, same outcome.
  - **Request wizard (PUSH stocklist) Edit step:** same scenario, same outcome (or skip if no PUSH stocklist seeded — note in the validation log).
  - **Request wizard (ad-hoc, no stocklist):** same scenario, same outcome.
  - **Note:** during validation, the picking guard from `block-expired-items-on-outbound` was incorrectly rejecting payloads when expired lots were merely visible in the Pick modal but not actually being picked. Fixed in a follow-up commit (`fix(outbound-expiry): ignore picklistItems with no quantityPicked`). Pick + Save now works as expected for the test workflow.
- [x] 6.4 Run `git diff develop..HEAD -- src/js/components/returns/ src/js/components/receiving/` — empty, confirming the two flows we said are unaffected stayed unaffected. (Verified against the parent commit `12531d258` for this change — `git diff 12531d258..HEAD -- src/js/components/returns/ src/js/components/receiving/` is empty. Diff against `develop` shows pre-existing branch commits unrelated to this change.)
- [x] 6.5 Take a screenshot of the Available cell with the parenthesised hint for the PR description.
- [ ] 6.6 Open the PR. Description links the related issue from the project tracker. Notes that this builds on `block-expired-items-on-outbound` (commit `12531d258`).
- [ ] 6.7 After merge, archive: `openspec archive expiry-aware-edit-availability`. Fill the "Deploy status" line in `design.md` with the customer branches replayed onto.
