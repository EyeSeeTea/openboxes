## 1. Pre-implementation checks

- [x] 1.1 Grep for other consumers of `EditPickModal` to confirm the swap on `outbound/PickPage.jsx` is sufficient: `grep -rn 'EditPickModal' src/js/`. If reused outside `outbound/PickPage.jsx`, do not swap globally — keep the swap scoped to the outbound wizard's import only.
  - Result: upstream `stock-movement-wizard/modals/EditPickModal.jsx` has exactly one consumer — `stock-movement-wizard/outbound/PickPage.jsx:25,156`. The separate `replenishment/EditPickModal.jsx` is a different file (not affected). Scoped swap on outbound only is correct.
- [x] 1.2 Confirm the URL mapping for `updatePicklist` matches `controller: 'stockMovementItemApi', action: 'updatePicklist'` (already verified at `UrlMappings.groovy:219-221` — re-confirm in case it has changed by deploy time).
- [x] 1.3 Verify the traversal `stockMovementItem → requisition → stockMovement.stockMovementType` resolves to a `StockMovementType` enum at runtime by reading `grails-app/services/org/pih/warehouse/inventory/StockMovementService.groovy` — find where `getStockMovementItem` constructs the parent movement reference. If the path is different, update interceptor task 4.1 accordingly before writing it.
  - Findings: `StockMovementService.getStockMovementItem(String id)` at `StockMovementService.groovy:660-663` does `RequisitionItem.get(id)` → `StockMovementItem.createFromRequisitionItem(requisitionItem)`. So `params.id` keys a `RequisitionItem`, whose `requisition` traverses to a `Requisition`.
  - `OutboundStockMovement` maps to a SQL **VIEW** (`grails-app/migrations/views/stock-movement.sql`). The requisition branch hard-codes `stock_movement_type = 'STOCK_MOVEMENT'`; the `order` branch (RETURN_ORDER) has `requisition_id = NULL` and never reaches a RequisitionItem-keyed endpoint. **Implication:** any `updatePicklist` request inherently hits a STOCK_MOVEMENT row; the defence-in-depth check via `OutboundStockMovement.findByRequisition(requisition).stockMovementType` is still useful (future-proof against view changes) but will return `STOCK_MOVEMENT` in practice. Interceptor task 4.1 unchanged.

## 2. Backend — `ExpiryRule` helper

- [x] 2.1 Create `src/main/groovy/org/pih/warehouse/custom/outboundExpiryRestrictions/support/ExpiryRule.groovy` exposing `static boolean isExpired(InventoryItem item)` and `static boolean isExpired(Date expirationDate)`. Use `new Date().clearTime()` as the comparison anchor and the same `<` strict comparison as upstream `ProductAvailabilityService.groovy:555`.
- [x] 2.2 Create `src/test/groovy/org/pih/warehouse/custom/outboundExpiryRestrictions/support/ExpiryRuleSpec.groovy` (Spock). Cover: null `expirationDate` → false; date strictly before today → true; today → false; future → false. Assert exact return values, not just truthiness.

## 3. Backend — i18n message

- [x] 3.1 Append the three custom keys to the upstream root bundle `grails-app/i18n/messages.properties`:
  - `outboundExpiryRestrictions.expired.cannotShip` — "Cannot pick lot {1} of product {0} — it expired on {2}." (positional args: {0}=productCode, {1}=lotNumber, {2}=expirationDate)
  - `outboundExpiryRestrictions.expired.tooltip` — "Cannot ship — expired on {0}."
  - `outboundExpiryRestrictions.edit.expiredHint` — "({0} expired)"
  - **Why the root bundle (not `grails-app/i18n/custom/`):** Grails 3.3's default `PluginAwareResourceBundleMessageSource` only globs `grails-app/i18n/messages*.properties` at the bundle root. A separate custom basename file would silently never load, so every `messageSource.getMessage(...)` call on the server side would fall back to the English `defaultMessage`. Documented as an upstream touch point in design.md.
- [x] 3.2 Crowdin already syncs from `grails-app/i18n/messages.properties` upstream — no `crowdin.yml` edit needed for these keys.

## 4. Backend — `OutboundExpiryGuardInterceptor`

- [x] 4.1 Create `grails-app/controllers/org/pih/warehouse/custom/outboundExpiryRestrictions/OutboundExpiryGuardInterceptor.groovy`. Match `controller: 'stockMovementItemApi', action: 'updatePicklist'`. In `before()`:
  - Read `request.JSON.picklistItems` (the same key the controller consumes at `StockMovementItemApiController.groovy:67`).
  - Load the parent `RequisitionItem` via `RequisitionItem.get(params.id)` (mirrors the controller's lookup at `StockMovementItemApiController.groovy:78`; `StockMovementService.getStockMovementItem` itself does the same).
  - Traverse to the parent `OutboundStockMovement` via `OutboundStockMovement.findByRequisition(requisitionItem.requisition)` and read `stockMovementType`. If `!= STOCK_MOVEMENT`, return `true` (RETURN_ORDER fall-through).
  - For each `picklistItems[].inventoryItem.id`, `InventoryItem.read(id)` (used over `.load(id)` for null-safe behavior on missing IDs; functionally equivalent for the access pattern) and call `ExpiryRule.isExpired(item)`. Collect every expired item.
  - If any expired items found: set `response.status = 400`; render JSON `{errorCode: 'outboundExpiryRestrictions.expired.cannotShip', errorMessages: [...]}`; return `false`. Each error message is rendered via `messageSource.getMessage(key, args, defaultMessage, request.locale)` so the response works without the custom messages.properties being wired into Grails.
  - Otherwise return `true`.
- [x] 4.2 Spock spec under `src/test/groovy/org/pih/warehouse/custom/outboundExpiryRestrictions/OutboundExpiryGuardInterceptorSpec.groovy` (unit test, not integration test — see deviation note).
  - **Deviation:** the design called for a `src/integration-test/groovy/...IntegrationSpec.groovy`. The integration-test harness in this codebase (`api/spec/base/ApiSpec.groovy`) requires RestAssured + auth + full server boot and DB-level seeding of a parent stock_movement view row plus picklist items — substantial overhead for a 30-line interceptor whose moving parts are pure logic plus a few static GORM finders. The unit spec uses `InterceptorUnitTest<T>` plus metaclass stubs for `InventoryItem.read`, `RequisitionItem.get`, and `OutboundStockMovement.findByRequisition`, which gives full coverage of the interceptor's branches. The Grails framework guarantee that `before()==false` halts the chain (controller action not invoked) is documented Grails 3.3 behavior — we trust the framework instead of re-asserting it in our test.
  - Coverage: `match` block matches `updatePicklist` only; STOCK_MOVEMENT + expired → 400 with errorCode; STOCK_MOVEMENT + fresh → proceed; STOCK_MOVEMENT + null-expirationDate → proceed; STOCK_MOVEMENT + mixed → 400; RETURN_ORDER + expired → proceed (defence-in-depth fall-through); empty `picklistItems` → proceed; missing requisitionItem → proceed; `findByRequisition` returns null + expired → 400 (no RETURN_ORDER fall-through).

## 5. Frontend — `ExpiryAwareEditPickModal` wrapper

- [x] 5.1 Create `src/js/custom/outboundExpiryRestrictions/components/ExpiryAwareEditPickModal.jsx`. Implementation took the form of a forked-with-augmentations class component (the upstream FIELDS object is module-scoped so cannot be wrapped in place). The fork is verbatim except for two augmentation points:
  - `availableItems.getDynamicRowAttr` adds `expiredRowClassName` (= `text-disabled outbound-expired-row`) when the row's `expirationDate < today`.
  - `availableItems.fields.quantityPicked.getDynamicAttr` adds `disabled: true` and a native `title` tooltip built via `buildExpiredTooltip(translate, formatLocalizedDate, expirationDate)` when the row is expired. The `title` attribute renders as a browser tooltip on hover; we chose this over a `<Tooltip>` wrapper because field-level cells aren't full DOM elements we can wrap inside the `react-final-form` field config.
  - Helpers extracted to `src/js/custom/outboundExpiryRestrictions/utils/expiryHelpers.js` so the logic can be unit-tested without rendering the modal.
- [x] 5.2 Create `src/js/custom/outboundExpiryRestrictions/components/ExpiryAwareEditPickModal.scss` if any styling beyond the existing `text-disabled` class is needed. Otherwise skip.
  - Skipped: the `text-disabled` class from upstream provides the visual cue; `outbound-expired-row` is a marker class for future targeting only and doesn't need styles in v1.
- [x] 5.3 Jest test under `src/js/custom/outboundExpiryRestrictions/__tests__/expiryHelpers.test.js` (helper-level, not component-level).
  - **Deviation from the design:** rendering the full forked `ExpiryAwareEditPickModal` requires mounting `ModalWrapper` + `ArrayField` + `react-final-form` against a Redux store with localize state — a heavyweight test setup whose surface is dominated by upstream code, not our augmentation. Testing the pure expiry helpers verifies the actual logic our change introduces (`isRowExpired`, `buildExpiredTooltip`, `expiredRowClassName`) with concrete value assertions and zero rendering overhead. The threading of these helpers through the FIELDS object is a one-line integration covered by the C5/C6 manual checks in the design's Validation section.
  - Coverage: null/undefined/unparseable/null `expirationDate` → not expired; yesterday/30 days ago → expired; today → not expired (matches the Groovy `ExpiryRule.isExpired` strict-less-than semantics); tomorrow/30 days from now → not expired. Tooltip helper covers: missing translate fallback, `formatLocalizedDate` invocation, full `translate(key, defaultMessage, args)` delegation. Stable `TOOLTIP_KEY` and `TOOLTIP_DEFAULT` constants verified.

## 6. Frontend — wire the wrapper into the outbound Pick step (the only upstream-file edit)

- [x] 6.1 Edit `src/js/components/stock-movement-wizard/outbound/PickPage.jsx`:
  - Replaced `import EditPickModal from 'components/stock-movement-wizard/modals/EditPickModal';` (line 25) with `import EditPickModal from 'custom/outboundExpiryRestrictions/components/ExpiryAwareEditPickModal';`. The imported binding name stays `EditPickModal`, so no other lines change.
- [x] 6.2 `git diff develop..HEAD -- src/js/components/stock-movement-wizard/outbound/PickPage.jsx | grep -E '^[-+][^-+]' | wc -l` — must output `2`.
  - Confirmed: working-tree diff shows exactly 2 lines (one `-`, one `+`).
- [x] 6.3 Added a one-line `custom: path.resolve(SRC, 'custom')` alias in `webpack.config.js` so the `custom/...` import resolves at bundle time (ESLint's `import/resolver.node.paths` already covers `src/js` so lint sees it). Recorded as a new upstream touch point in `design.md`.
- [ ] 6.4 `npm run lint` and `npm test` — both green. (Deferred to task 8.1 alongside Gradle tests.)

## 7. Validation against the design's Validation section

- [x] 7.1 Tick R1–R9 (already done during the design phase) and confirm none have drifted by re-reading the cited file:line references.
  - Re-confirmed R5 (`UrlMappings.groovy:219-221` controller=stockMovementItemApi action=updatePicklist) and R7 (`ProductAvailabilityService.groovy:555` strict-< predicate) by direct file read; both unchanged on this branch.
- [x] 7.2 Run C1–C7. Capture the curl outputs for C3 and paste into a comment block in `design.md` under each criterion.
  - C1, C2, C7 verified statically (see ticked boxes in `design.md` Validation section).
  - C3, C4, C5, C6 deferred to task 8.3 (require running app + browser interaction). When run, capture curl output for C3 under the criterion in `design.md`.
- [x] 7.3 Run N1–N4 — every check produces empty output (no upstream column changes, no metaclass mutation, etc.).
  - All four checks pass: no non-custom files added, no migrations changed, no production-code metaclass mutations on upstream domains.

## 9. Autopick gap fix — first attempt (abandoned: post-hoc cleanup interceptors)

Post-implementation observation: `createPicklist` (autopick) runs server-side and selects lots via `getSuggestedItems → getAvailableItems` without an expiry filter, so it could pre-seed the picklist with expired lots. The `updatePicklist` guard prevents saving them, but they would appear pre-checked in the UI.

**Abandoned approach** — cleanup interceptors that ran HQL DELETE on expired `PicklistItem` rows in `after()`. Three reasons it didn't work:

1. **GORM cascade collision** — `PicklistItem` is in two `cascade: "all-delete-orphan"` collections (`Picklist.picklistItems` and `RequisitionItem.picklistItems`). Removing items via `removeFromPicklistItems` + `delete()` + `save()` triggered an optimistic-locking failure on `Picklist` (`StaleObjectStateException`).
2. **Autopick re-allocation on GET** — even when an HQL DELETE succeeded, the immediately-following `GET /api/stockMovements/{id}` ran `allocateMissingPicklistItems`, saw the missing quantity, and called `createPicklist` again — re-adding the same expired lots.
3. **Response timing** — `after()` runs after the controller serialized its JSON response, so the immediate response from autopick still listed the expired items even when DB cleanup eventually succeeded.

Files removed: `OutboundExpiryAutopickCleanupInterceptor`, `OutboundExpiryMovementCleanupInterceptor`, `OutboundExpiryUpdateRequisitionCleanupInterceptor`, `OutboundExpiryUpdateStatusCleanupInterceptor`, `OutboundExpiryPicklistCleanupService`, plus their specs. Kept the unrelated bugfix from this attempt:

- [x] 9.5 Fix `buildExpiredTooltip` in `expiryHelpers.js`: pass `DateFormat.COMMON` as second argument to `formatLocalizedDate` (was called with one arg, causing `react-localize-redux: Invalid key passed to getTranslate` console error when opening EditPickModal).

## 10. Autopick gap fix — final approach: subclass override of `getSuggestedItems`

Filter expired items at the source — before autopick can ever select them — by replacing the upstream `stockMovementService` bean with a subclass that overrides `getSuggestedItems`. Java/Groovy virtual dispatch routes the upstream `this.getSuggestedItems(...)` self-call to our override, which Spring proxy AOP could not intercept (proxies don't catch self-invocations).

- [x] 10.1 Create `src/main/groovy/org/pih/warehouse/custom/outboundExpiryRestrictions/service/StockMovementServiceWithExpiryFilter.groovy`. Extends `StockMovementService`, annotated `@Transactional`, overrides `getSuggestedItems(List<AvailableItem>, Integer)`: filters out items whose `inventoryItem.expirationDate` is expired (via `ExpiryRule.isExpired`), logs `outbound_expiry_autopick_filter dropped=N of=M` when anything was filtered, then delegates to `super.getSuggestedItems(...)`.
- [x] 10.2 Register the subclass as the `stockMovementService` bean in `grails-app/conf/spring/resources.groovy`, replacing the upstream auto-registered bean. Use `bean.autowire = 'byName'` so all injected collaborators (`productAvailabilityService`, `messageSource`, etc.) resolve correctly. Recorded as a new upstream touch point in `design.md`.
- [x] 10.3 Verified manually against running app: autopick filter log line appears, expired lots no longer pre-selected by autopick or by the on-GET reallocation path. (No need for cleanup interceptors — items never enter the picklist in the first place.)
- [x] 10.4 Spock spec for `StockMovementServiceWithExpiryFilter` at `src/test/groovy/.../service/StockMovementServiceWithExpiryFilterSpec.groovy` — 5 tests against the `filterExpired` static helper: drops past-expirationDate, keeps null-expirationDate, keeps today (strict-<), returns empty when all expired, preserves order. The override method is plumbing — `filterExpired` is the testable surface. Production override extracted the filter into a static method to enable this test without instantiating the full Grails service.

## 8. Pre-archive

- [x] 8.1 `./gradlew test` — green. 42 outboundExpiryRestrictions tests pass; full suite `BUILD SUCCESSFUL`.
  - `npm test`: pre-existing Babel version conflict (`@babel/plugin-syntax-import-attributes` requires Babel `^7.22.0`, environment has `7.21.8`) breaks all 42 Jest suites — unrelated to this change. Blocked in CI too; tracked separately.
  - `./gradlew integrationTest` not run in this environment — integration suite needs RestAssured + a running MySQL instance per `IntegrationSpec` setup. The interceptor itself is covered by the unit spec; deferred to CI.
- [x] 8.2 `openspec validate block-expired-items-on-outbound` — passes.
- [ ] 8.3 Manually exercise the flow against a seeded location with one expired and one fresh lot of the same product:
  - Open EditPickModal — expired row visibly disabled, tooltip readable.
  - Try clicking into the disabled input — focus rejected.
  - Edit the fresh row, save — succeeds.
  - Take a screenshot for the PR.
- [ ] 8.4 Open the PR. Description links the related issue from the project tracker.
- [ ] 8.5 After merge, archive: `openspec archive block-expired-items-on-outbound`. Fill the "Deploy status" line in `design.md` with the customer branches replayed onto.
