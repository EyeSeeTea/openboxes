## 1. Pre-apply verification

Most assumptions from the original design draft were resolved by grep + repo inspection during proposal review (see `design.md` § "Unverified Assumptions — resolved during proposal review"). Only the interceptor-discovery spike remains worth running before writing production code.

- [x] 1.1 ~~Grep for `DAMAGE_TRANSACTION_TYPE_ID` callers.~~ **Done during proposal review.** Five hits; only `InventoryController.createDamaged:824` is a user-initiated write path. Interceptor `match()` stays as scoped (single action).
- [x] 1.2 ~~Grep for `listInventoryAdjustmentReasonCodes` callers.~~ **Done during proposal review.** Four hits; surfaced two additional chokepoints (`_adjustStock.gsp:71`, `ReasonCodeApiController.groovy:33`) — design updated with D2.a and D2.b, and the touch-points table now lists 5 upstream files instead of 3.
- [ ] 1.3 **Optional spike (lifts confidence 8→9):** drop a stub interceptor file at `grails-app/controllers/org/pih/warehouse/custom/damagedAdjustments/HelloInterceptor.groovy` with `match(controller: 'errors', action: 'handleNotFound')` and a `before()` that logs. Run `./gradlew bootRun`, hit a 404 URL, confirm the log line appears. Confirms Grails 3 interceptor scanner picks up files under `org.pih.warehouse.custom.*`. Delete the stub before proceeding.
- [x] 1.4 ~~YAML override spike.~~ **Done during proposal review.** Verified by reading `application.yml:32,35` (config locations chain) + `docker/docker-compose.yml` (volume mount of `openboxes.yml` to `/app/.grails/openboxes.yml`) + `docker/Dockerfile:16` (user home = `/app`). Override chain is well-formed; new nested keys merge into `ConfigObject` the same way as existing ones.

## 2. Config wiring

- [ ] 2.1 Add the nested key `openboxes.custom.adjustments.damaged.enabled: false` to `grails-app/conf/application.yml` under the existing `openboxes:` block (line 329 region). Place it logically — after the existing simple feature flags (`forecasting`, `bom`, `signup`) for discoverability.
- [ ] 2.2 Document the flag in `docker/openboxes.client-template.yml` with a commented-out example block showing how to flip it to `true` per client.
- [ ] 2.3 In `docker/openboxes.yml` (Tajikistan instance config), set `openboxes.custom.adjustments.damaged.enabled: true`.

## 3. Custom service: CustomReasonCodeService

- [ ] 3.1 Create `grails-app/services/org/pih/warehouse/custom/damagedAdjustments/CustomReasonCodeService.groovy` with one method `List<ReasonCode> listInventoryAdjustmentReasonCodes()` that reads the flag from `grailsApplication.config` and filters `ReasonCode.DAMAGED` out of `ReasonCode.listInventoryAdjustmentReasonCodes()` when `false`.
- [ ] 3.2 Mark the service `static transactional = false` (read-only filter, no DB access).
- [ ] 3.3 Create `src/test/groovy/org/pih/warehouse/custom/damagedAdjustments/CustomReasonCodeServiceSpec.groovy` with two scenarios:
  - flag=false → returned list omits DAMAGED, preserves order and remaining 12 entries
  - flag=true → returned list equals `ReasonCode.listInventoryAdjustmentReasonCodes()` byte-for-byte
- [ ] 3.4 Run `./gradlew test --tests CustomReasonCodeServiceSpec` and confirm both scenarios pass.

## 4. Chokepoint edits (UPSTREAM TOUCHES — 3 files)

### 4a. Taglib

- [ ] 4.1 In `grails-app/taglib/org/pih/warehouse/SelectTagLib.groovy`, add `def customReasonCodeService` to the class field section (near the top, alongside any existing `def` injections — if none exist, add at the top of the class body before the first taglib closure).
- [ ] 4.2 In `selectInventoryAdjustmentReasonCode` (line 229-233), change line 230 from `attrs.from = ReasonCode.listInventoryAdjustmentReasonCodes()` to `attrs.from = customReasonCodeService.listInventoryAdjustmentReasonCodes()`.
- [ ] 4.3 Confirm `git diff grails-app/taglib/org/pih/warehouse/SelectTagLib.groovy` shows exactly these two changes — no whitespace edits, no reformatting elsewhere in the file (Boy Scout suspended for upstream files).

### 4b. Adjust Stock GSP — route through the taglib (closes path 1)

- [ ] 4.4 In `grails-app/views/inventoryItem/_adjustStock.gsp`, replace the `<g:select name="reasonCode" ... from="${org.pih.warehouse.core.ReasonCode.listInventoryAdjustmentReasonCodes()}" ...>` block at lines 69-74 with `<g:selectInventoryAdjustmentReasonCode name="reasonCode" value="${params.reasonCode}" noSelection="['':'']" data-placeholder="${g.message(code: 'default.selectAnOption.label', default: 'Select an Option')}" class="chzn-select-deselect"/>`. Preserve the surrounding `<td>` and `<tr>` markup unchanged.
- [ ] 4.5 Confirm `grep -n 'ReasonCode.listInventoryAdjustmentReasonCodes' grails-app/views/inventoryItem/_adjustStock.gsp` returns zero lines — the direct static call is gone.

### 4c. Reason-codes API — filter ADJUST_INVENTORY branch (closes path 4)

- [ ] 4.6 In `grails-app/controllers/org/pih/warehouse/api/ReasonCodeApiController.groovy`, add `def customReasonCodeService` near the existing `def locationService` declaration (line 19 area).
- [ ] 4.7 Change line 33 from `reasonCodes.addAll(getReasonCodes(ReasonCode.listInventoryAdjustmentReasonCodes()))` to `reasonCodes.addAll(getReasonCodes(customReasonCodeService.listInventoryAdjustmentReasonCodes()))`. **Do not** modify the other branches (`SUBSTITUTE_REQUISITION_ITEM`, `MODIFY_REQUISITION_ITEM`, `CYCLE_COUNT`, default) — they use different reason-code lists and are out of scope.
- [ ] 4.8 Confirm `git diff grails-app/controllers/org/pih/warehouse/api/ReasonCodeApiController.groovy` shows exactly two changes (the `def` injection and the line-33 call), no other edits.

## 5. Interceptor: DamagedAdjustmentInterceptor

- [ ] 5.1 Create `grails-app/controllers/org/pih/warehouse/custom/damagedAdjustments/DamagedAdjustmentInterceptor.groovy` matching `controller: 'inventory', action: 'createDamaged'`. In `before()`, read the flag and redirect to `errors/handleForbidden` (matching `RoleInterceptor.groovy:138,161`) returning `false` when the flag is `false`. Log an INFO line.
- [ ] 5.2 Create `src/integration-test/groovy/org/pih/warehouse/custom/damagedAdjustments/DamagedAdjustmentInterceptorSpec.groovy` with two scenarios:
  - flag=false → GET `/inventory/createDamaged?product.id=<seeded>` returns a 302 redirect to `errors/handleForbidden`
  - flag=true → same request returns 200 (or the upstream rendered view)
- [ ] 5.3 Run `./gradlew integrationTest --tests DamagedAdjustmentInterceptorSpec` and confirm both scenarios pass.
- [ ] 5.4 If task 1.1 found additional actions that create DAMAGE-type transactions (e.g. `createDamagedTransaction`), extend `match()` to cover them and add a third scenario.

## 6. GSP edit: product/_actions.gsp (UPSTREAM TOUCH)

- [ ] 6.1 In `grails-app/views/product/_actions.gsp`, wrap the `<div class="action-menu-item">` block at lines 91-96 (the `createDamaged` link) in `<g:if test="${grailsApplication.config.openboxes.custom.adjustments.damaged.enabled}">…</g:if>`. Keep the same indentation as the surrounding `<div>` blocks.
- [ ] 6.2 Confirm `git diff grails-app/views/product/_actions.gsp` shows exactly the wrapping change — no other edits.

## 7. Verification

- [ ] 7.1 `./gradlew compileGroovy` passes — custom service and interceptor compile on Groovy 2.4 / Java 8.
- [ ] 7.2 `./gradlew test` passes — no regression in upstream unit tests.
- [ ] 7.3 `./gradlew integrationTest` passes — including the new interceptor spec.
- [ ] 7.4 Boot `./gradlew bootRun` with `openboxes.custom.adjustments.damaged.enabled: true` (manually toggle) and capture:
  - Adjust Stock modal dropdown (DAMAGED present, taglib-routed render visually identical to upstream — resolves the residual D2.a regression risk noted in design's Confidence section)
  - Create Adjustment per-line dropdown (DAMAGED present)
  - Product menu (Damaged link present)
  - `curl -s '/openboxes/api/reasonCodes?activityCode=ADJUST_INVENTORY' | jq '.data[].id'` includes `DAMAGED`
- [ ] 7.5 Flip flag to `false`, restart, capture matching artifacts:
  - Adjust Stock modal dropdown (DAMAGED absent)
  - Create Adjustment per-line dropdown (DAMAGED absent)
  - Product menu (Damaged link absent)
  - Direct URL `/openboxes/inventory/createDamaged?product.id=<any>` (redirects to forbidden page)
  - `curl -s '/openboxes/api/reasonCodes?activityCode=ADJUST_INVENTORY' | jq '.data[].id'` does NOT include `DAMAGED`
- [ ] 7.6 Attach all artifacts (4 screenshots + 2 curl outputs from flag=true and flag=false) to the PR description.

## 8. Pre-commit self-review (per CLAUDE.md)

- [ ] 8.1 Upstream-isolation: confirm `git diff --stat release/est/tjk/0.9.7..HEAD` shows custom files under `org.pih.warehouse.custom.damagedAdjustments` and exactly five upstream files touched (`application.yml`, `SelectTagLib.groovy`, `_adjustStock.gsp`, `ReasonCodeApiController.groovy`, `_actions.gsp`). Any additional upstream touches need justification in `design.md`.
- [ ] 8.2 Functional Groovy: no `for` loops in the new service or interceptor. Use `.findAll { it != ReasonCode.DAMAGED }` for the filter.
- [ ] 8.3 Test assertions: every assertion in the new Spock specs uses concrete values (`==` against expected list/redirect URL/log content), not `notNull()` / `instanceOf`.
- [ ] 8.4 No `@Autowired`. Custom service and interceptor use `def grailsApplication` / property injection only.
- [ ] 8.5 No DB migration files in the diff. This change is config + filter only.
- [ ] 8.6 i18n: no new user-facing strings introduced. (The error page is upstream's standard forbidden page.) If task 1.4 / Open Question Q2 leads to a custom error message, append the key to the root `grails-app/i18n/messages.properties` per `rules/custom-package-isolation.md` § "i18n exception".

## 9. PR and propagation

- [ ] 9.1 Open PR from `feature/disable-damaged-adjustments` to `release/est/tjk/0.9.7`. Title: `feat(adjustments): config-driven flag to disable damaged stock adjustments`. Body links to this OpenSpec change folder.
- [ ] 9.2 After merge, archive this change via `/opsx:archive disable-damaged-adjustments`. Confirm the archive directory includes the final `design.md` (with checked Validation boxes) so the upstream touch points are in the patch manifest.
- [ ] 9.3 Note in the archive's `design.md` Migration Plan section: "Deploy status: tjk only. sp/EST not yet adopted — opt in by setting flag in their own `docker/openboxes.yml` after next EST→client merge brings the code in."
