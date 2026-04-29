## 1. Pre-implementation checks

- [ ] 1.1 Grep for other consumers of `EditPickModal` to confirm the swap on `outbound/PickPage.jsx` is sufficient: `grep -rn 'EditPickModal' src/js/`. If reused outside `outbound/PickPage.jsx`, do not swap globally — keep the swap scoped to the outbound wizard's import only.
- [ ] 1.2 Confirm the URL mapping for `updatePicklist` matches `controller: 'stockMovementItemApi', action: 'updatePicklist'` (already verified at `UrlMappings.groovy:219-221` — re-confirm in case it has changed by deploy time).
- [ ] 1.3 Verify the traversal `stockMovementItem → requisition → stockMovement.stockMovementType` resolves to a `StockMovementType` enum at runtime by reading `grails-app/services/org/pih/warehouse/inventory/StockMovementService.groovy` — find where `getStockMovementItem` constructs the parent movement reference. If the path is different, update interceptor task 4.1 accordingly before writing it.

## 2. Backend — `ExpiryRule` helper

- [ ] 2.1 Create `src/main/groovy/org/pih/warehouse/custom/outboundExpiryRestrictions/support/ExpiryRule.groovy` exposing `static boolean isExpired(InventoryItem item)` and `static boolean isExpired(Date expirationDate)`. Use `new Date().clearTime()` as the comparison anchor and the same `<` strict comparison as upstream `ProductAvailabilityService.groovy:555`.
- [ ] 2.2 Create `src/test/groovy/org/pih/warehouse/custom/outboundExpiryRestrictions/support/ExpiryRuleSpec.groovy` (Spock). Cover: null `expirationDate` → false; date strictly before today → true; today → false; future → false. Assert exact return values, not just truthiness.

## 3. Backend — i18n message

- [ ] 3.1 Create `grails-app/i18n/custom/outboundExpiryRestrictions-messages.properties` with keys:
  - `outboundExpiryRestrictions.expired.cannotShip` — "Cannot pick lot {0} of product {1} — it expired on {2}."
  - `outboundExpiryRestrictions.expired.tooltip` — "Cannot ship — expired on {0}."
- [ ] 3.2 Confirm `crowdin.yml` picks the new file up. Add a glob entry if not.

## 4. Backend — `OutboundExpiryGuardInterceptor`

- [ ] 4.1 Create `grails-app/controllers/org/pih/warehouse/custom/outboundExpiryRestrictions/OutboundExpiryGuardInterceptor.groovy`. Match `controller: 'stockMovementItemApi', action: 'updatePicklist'`. In `before()`:
  - Read `request.JSON.picklistItems` (the same key the controller consumes at `StockMovementItemApiController.groovy:67`).
  - Load `StockMovementItem` via `stockMovementService.getStockMovementItem(params.id)`.
  - Traverse to the parent `OutboundStockMovement` and read `stockMovementType`. If `!= STOCK_MOVEMENT`, return `true` (let the request proceed; this is the RETURN_ORDER fall-through).
  - For each `picklistItems[].inventoryItem.id`, `InventoryItem.load(id)` and call `ExpiryRule.isExpired(item)`. Collect every expired item.
  - If any expired items found: set `response.status = 400`; render JSON `{errorCode: 'outboundExpiryRestrictions.expired.cannotShip', errorMessages: [...formatted per i18n key...]}`; return `false`.
  - Otherwise return `true`.
- [ ] 4.2 Spock integration test under `src/integration-test/groovy/org/pih/warehouse/custom/outboundExpiryRestrictions/OutboundExpiryGuardInterceptorIntegrationSpec.groovy`:
  - Seed a `StockMovementItem` parented to a STOCK_MOVEMENT with one expired and one fresh `InventoryItem`. POST a picklist with the expired one → 400 with the expected `errorCode`. POST with only the fresh one → 200/proceed.
  - Seed a RETURN_ORDER stockMovementItem with the same expired payload → 200/proceed (defence-in-depth).
  - Seed an item with `expirationDate=null` → 200/proceed.
  - Verify the controller's action is **not** invoked when the interceptor rejects (use a spy on `stockMovementService.updatePicklistItem` and assert `0 * _.updatePicklistItem(...)` on rejection).

## 5. Frontend — `ExpiryAwareEditPickModal` wrapper

- [ ] 5.1 Create `src/js/custom/outboundExpiryRestrictions/components/ExpiryAwareEditPickModal.jsx`. Re-export the upstream `EditPickModal` with two augmentations:
  - Wrap the `availableItems.getDynamicRowAttr` so a row whose `expirationDate < today` adds the `text-disabled` class on top of upstream's existing logic.
  - Wrap the `availableItems.fields.quantityPicked.getDynamicAttr` so `disabled` is also true when `expirationDate < today`. Pass the existing upstream conditions through.
  - Add a `<Tooltip>` (the existing `react-tippy`) wrapper on each expired row showing the i18n `outboundExpiryRestrictions.expired.tooltip` formatted with the row's `expirationDate`.
- [ ] 5.2 Create `src/js/custom/outboundExpiryRestrictions/components/ExpiryAwareEditPickModal.scss` if any styling beyond the existing `text-disabled` class is needed. Otherwise skip.
- [ ] 5.3 Jest test under `src/js/custom/outboundExpiryRestrictions/__tests__/ExpiryAwareEditPickModal.test.jsx` using `@testing-library/react`:
  - Render with one expired and one fresh row. Assert the expired row has the `text-disabled` class and its `quantityPicked` input has `disabled` attribute. Assert the fresh row's input is not disabled.
  - `userEvent.type(expiredInput, '5')` produces no value change (`expect(expiredInput).toHaveValue(null)` or equivalent).
  - Tooltip text on the expired row matches the i18n default.

## 6. Frontend — wire the wrapper into the outbound Pick step (the only upstream-file edit)

- [ ] 6.1 Edit `src/js/components/stock-movement-wizard/outbound/PickPage.jsx`:
  - Replace `import EditPickModal from 'components/stock-movement-wizard/modals/EditPickModal';` (currently at line 25) with `import EditPickModal from 'custom/outboundExpiryRestrictions/components/ExpiryAwareEditPickModal';`. Keep the imported binding name `EditPickModal` so no other lines need changes.
- [ ] 6.2 `git diff develop..HEAD -- src/js/components/stock-movement-wizard/outbound/PickPage.jsx | grep -E '^[-+][^-+]' | wc -l` — must output `2`.
- [ ] 6.3 `npm run lint` and `npm test` — both green.

## 7. Validation against the design's Validation section

- [ ] 7.1 Tick R1–R9 (already done during the design phase) and confirm none have drifted by re-reading the cited file:line references.
- [ ] 7.2 Run C1–C7. Capture the curl outputs for C3 and paste into a comment block in `design.md` under each criterion.
- [ ] 7.3 Run N1–N4 — every check produces empty output (no upstream column changes, no metaclass mutation, etc.).

## 8. Pre-archive

- [ ] 8.1 `./gradlew test` and `./gradlew integrationTest` and `npm test` — all green.
- [ ] 8.2 `openspec validate block-expired-items-on-outbound` — passes.
- [ ] 8.3 Manually exercise the flow against a seeded location with one expired and one fresh lot of the same product:
  - Open EditPickModal — expired row visibly disabled, tooltip readable.
  - Try clicking into the disabled input — focus rejected.
  - Edit the fresh row, save — succeeds.
  - Take a screenshot for the PR.
- [ ] 8.4 Open the PR. Description links the related issue from the project tracker.
- [ ] 8.5 After merge, archive: `openspec archive block-expired-items-on-outbound`. Fill the "Deploy status" line in `design.md` with the customer branches replayed onto.
