## Context

Three UI paths today let an operator record a `DAMAGED` stock adjustment that writes directly to `Transaction` / `TransactionEntry`. Because `Transaction.groovy` has no `documents` relationship, there is no place to attach photo evidence, a signed damage report, or a disposal vendor's invoice — the adjustment is recorded with only a free-text comment.

The fork already implements the `stock-transfer-documents` capability (archived 2026-04-29), which gates stock transfers on attached documents per-location/per-bin via the `REQUIRE_TRANSFER_OUT_DOCUMENT` / `REQUIRE_TRANSFER_IN_DOCUMENT` activity codes. The intended damage workflow at Tajikistan is:

1. Operator transfers the damaged stock from its origin bin into a **Damaged** bin in the same warehouse.
2. The transfer requires proof documents (already enforced by `stock-transfer-documents`).
3. A separate write-off transaction debits the Damaged bin downstream.

That workflow is voluntary today because the three direct-adjustment paths are still open. This change closes them via a config flag.

### The paths (verified line references — all callers identified by grep, see Validation §)

| # | UI/API path | Reaches | Source of `DAMAGED` |
|---|---|---|---|
| 1 | Stock card row → **Adjust Stock** modal | `InventoryItemController.adjustStock:751` → `inventoryService.adjustStock()` | `_adjustStock.gsp:71` calls `ReasonCode.listInventoryAdjustmentReasonCodes()` **directly via `<g:select from="...">` — bypasses the taglib** |
| 2 | Product menu → **Create Adjustment** form (per-line reason) | `InventoryController.createAdjustment:800` → forwards to `createTransaction:828`; saves via `saveAdjustmentTransaction:874` | `<g:selectInventoryAdjustmentReasonCode>` taglib (`grails-app/taglib/org/pih/warehouse/SelectTagLib.groovy:229-233`) → `ReasonCode.listInventoryAdjustmentReasonCodes()` |
| 3 | Product menu → **Create Damaged Transaction** (header type) | `InventoryController.createDamaged:823` sets `params.transactionType = Constants.DAMAGE_TRANSACTION_TYPE_ID` and forwards to `createTransaction:828`; renders `_inventoryConsumed.gsp` | Hardcoded TransactionType id — not a reason-code dropdown |
| 4 | JSON API `/api/reasonCodes?activityCode=ADJUST_INVENTORY` | `ReasonCodeApiController.list():33` | `ReasonCode.listInventoryAdjustmentReasonCodes()` |

**Path 1 cannot be closed by the taglib edit alone** — the Adjust Stock GSP calls the static enum method directly through a plain `<g:select from="...">` rather than through `<g:selectInventoryAdjustmentReasonCode>`. The fix is to convert that one call site to use the taglib (D2.a below), giving us one chokepoint for paths 1 + 2.

**Path 4** has no known React/JS consumer for the `ADJUST_INVENTORY` branch (`grep -rn "ADJUST_INVENTORY" src/js/` returns nothing; the cycle-count React code uses a different list, `listCycleCountReasonCodes()`). Filtering it anyway is defense-in-depth so a future React consumer doesn't accidentally see DAMAGED.

**Path 3** needs the interceptor + GSP `<g:if>` guard.

## Goals / Non-Goals

**Goals:**
- A single config flag (`openboxes.custom.adjustments.damaged.enabled`, default `false`) closes all four paths when set to `true`.
- The custom code lives entirely under `org.pih.warehouse.custom.damagedAdjustments` per the custom-package isolation rule.
- Upstream file edits are surgical and itemized below.
- Default value (`false`) preserves current upstream behavior — no behavior change on any branch that doesn't opt in.

**Non-Goals:**
- **Programmatic creation of damaged transactions** (e.g., a future write-off-from-Damaged-bin service) is NOT blocked. Only user-initiated UI paths are gated.
- **Configuring the policy at runtime** via an admin UI. The flag lives in YAML; a restart applies it. No `Setting` domain class exists in OpenBoxes and adding one is overkill for a deploy-time policy.
- **Per-location or per-role gating.** The flag is global per Grails instance. If finer granularity is needed later, the existing `ActivityCode`/`Location.supports()` mechanism is the natural extension point — but that scope is explicitly out for now.
- **Removing the "Damaged" `TransactionType` row from the database.** The seeded TransactionType (id=5) stays so programmatic write-offs and historical reporting continue to work.

## Decisions

### D1. Config flag namespace: `openboxes.custom.*` (not `openboxes.*`)

The flag is `openboxes.custom.adjustments.damaged.enabled`. Existing fork-added flags (e.g. for IMS) follow the same prefix so `grep -rn 'openboxes.custom.' grails-app/ src/main/` produces the full list of EST-fork toggles, distinct from upstream's `openboxes.forecasting.enabled` / `openboxes.bom.enabled` / etc. (`grails-app/conf/application.yml:380,385`).

**Alternative considered:** put it under `openboxes.adjustments.damaged.enabled` directly under the existing namespace. Rejected because it pollutes the upstream namespace without signaling that this is a fork-only addition. Future upstream merges might collide with a same-name key.

### D2. Wrap `ReasonCode.listInventoryAdjustmentReasonCodes()` with a custom service

Add `CustomReasonCodeService` under `grails-app/services/org/pih/warehouse/custom/damagedAdjustments/`. It exposes:

```groovy
List<ReasonCode> listInventoryAdjustmentReasonCodes() {
    List<ReasonCode> base = ReasonCode.listInventoryAdjustmentReasonCodes()
    boolean damagedEnabled = grailsApplication.config.openboxes.custom.adjustments.damaged.enabled ?: false
    return damagedEnabled ? base : base.findAll { it != ReasonCode.DAMAGED }
}
```

The taglib at `SelectTagLib.groovy:229-233` is edited by **one line**: swap the static call for a service call.

```groovy
// Before
attrs.from = ReasonCode.listInventoryAdjustmentReasonCodes()
// After
attrs.from = customReasonCodeService.listInventoryAdjustmentReasonCodes()
```

This closes path 2 (Create Adjustment per-line dropdown) at the source.

#### D2.a — Convert `_adjustStock.gsp` to use the taglib (closes path 1)

`grails-app/views/inventoryItem/_adjustStock.gsp:69-74` currently bypasses the taglib:

```gsp
<g:select name="reasonCode"
          value="${params.reasonCode}"
          from="${org.pih.warehouse.core.ReasonCode.listInventoryAdjustmentReasonCodes()}"
          noSelection="['':'']"
          data-placeholder="${g.message(code: 'default.selectAnOption.label', default: 'Select an Option')}"
          class="chzn-select-deselect"/>
```

Replace with `<g:selectInventoryAdjustmentReasonCode>` so it routes through the taglib (and therefore through the custom service). The taglib only sets `attrs.from` and `attrs.optionValue` before delegating to `g.select`, so all other attributes (`name`, `value`, `noSelection`, `data-placeholder`, `class`) pass through unchanged:

```gsp
<g:selectInventoryAdjustmentReasonCode name="reasonCode"
                                       value="${params.reasonCode}"
                                       noSelection="['':'']"
                                       data-placeholder="${g.message(code: 'default.selectAnOption.label', default: 'Select an Option')}"
                                       class="chzn-select-deselect"/>
```

This is a surgical upstream edit (one tag swap, no other line touched) and consolidates paths 1 + 2 onto one chokepoint (D2's service-wrapped taglib).

#### D2.b — Filter the JSON API for the `ADJUST_INVENTORY` branch (closes path 4)

`ReasonCodeApiController.list():33` currently calls `ReasonCode.listInventoryAdjustmentReasonCodes()` directly inside the `ActivityCode.ADJUST_INVENTORY` branch. Add `def customReasonCodeService` to the controller and change line 33 from `reasonCodes.addAll(getReasonCodes(ReasonCode.listInventoryAdjustmentReasonCodes()))` to `reasonCodes.addAll(getReasonCodes(customReasonCodeService.listInventoryAdjustmentReasonCodes()))`. Other branches (`SUBSTITUTE_REQUISITION_ITEM`, `MODIFY_REQUISITION_ITEM`, `CYCLE_COUNT`, default) stay untouched — only the inventory-adjustment branch needs filtering.

Defense-in-depth: no React consumer of this branch exists today, but filtering it now prevents a future regression if one is added.

**Alternative considered (and rejected) for D2:**
- Override the static enum method via metaclass — explicitly forbidden by `rules/upstream-entity-extension.md` (runtime mutation, invisible in source).
- Register a custom taglib with the same `g:` namespace and same tag name to shadow the upstream one — Grails behavior with two same-namespace same-name taglibs is undefined (load-order dependent), and the resolution is fragile across Grails 3.3 versions.
- Edit the GSPs to call a different taglib namespace (e.g. `<custom:selectInventoryAdjustmentReasonCode>`) — multiplies the upstream-file edits, more merge surface.

The one-line `SelectTagLib.groovy` edit is the smallest surface change. It is documented in the Upstream Touch Points table below.

### D3. Guard `createDamaged` with a Grails 3 URL interceptor (not a `beforeInterceptor` on the upstream controller)

A standalone interceptor avoids editing `InventoryController.groovy` at all. Pattern matches the existing `RoleInterceptor.groovy` in this repo (`grails-app/controllers/org/pih/warehouse/RoleInterceptor.groovy:103-114`):

```groovy
package org.pih.warehouse.custom.damagedAdjustments

class DamagedAdjustmentInterceptor {
    def grailsApplication

    DamagedAdjustmentInterceptor() {
        match(controller: 'inventory', action: 'createDamaged')
    }

    boolean before() {
        boolean enabled = grailsApplication.config.openboxes.custom.adjustments.damaged.enabled ?: false
        if (!enabled) {
            log.info "Damaged adjustment blocked by openboxes.custom.adjustments.damaged.enabled=false"
            redirect(controller: 'errors', action: 'handleForbidden')
            return false
        }
        return true
    }
}
```

**Why not `beforeInterceptor` on the controller:** putting the guard inline in `InventoryController.groovy` would be an edit to a 1000+-line upstream controller. A separate interceptor under `org.pih.warehouse.custom.damagedAdjustments` is zero upstream-file edits.

**Why redirect to `errors/handleForbidden` and not `response.sendError(403)`:** the upstream `RoleInterceptor` (line 138, 161) uses the same redirect pattern. Consistency = better UX (the user sees the standard forbidden page instead of a raw 403) and less divergence to maintain.

### D4. Hide the menu link in `product/_actions.gsp` with a `<g:if>`

Wrap the existing `<div class="action-menu-item">` containing the `createDamaged` link (`grails-app/views/product/_actions.gsp:91-96`) in:

```gsp
<g:if test="${grailsApplication.config.openboxes.custom.adjustments.damaged.enabled}">
    <div class="action-menu-item">
        <g:link controller="inventory" action="createDamaged" params="['product.id':productInstance?.id]">
            ...
        </g:link>
    </div>
</g:if>
```

The `grailsApplication` variable is already accessible in GSPs in this repo — verified by `grails-app/views/order/show.gsp:126` (`${orderInstance?.currencyCode?:grailsApplication.config.openboxes.locale.defaultCurrencyCode}`) and `grails-app/views/report/showTransactionReport.gsp:283` (`${grailsApplication.config.openboxes.ajaxRequest.timeout}`).

### D5. Default lives in code (`?: false`); per-instance overrides in `docker/openboxes.yml`

The default is the `?: false` fallback in `CustomReasonCodeService` / `DamagedAdjustmentInterceptor` and the falsy GSP test in `_actions.gsp`. When the key is absent from every config source, `grailsApplication.config.openboxes.custom.adjustments.damaged.enabled` resolves to `null` and the fallback yields `false` (damaged adjustments blocked).

Per-instance YAML:
- `docker/openboxes.client-template.yml` documents the opt-in (commented-out block).
- `docker/openboxes.yml` on `release/est/tjk/0.9.7` sets `openboxes.custom.adjustments.damaged.enabled: true`.

**Why _not_ a YAML default in `application.yml`?** Original draft added a nested leaf there to mirror the `openboxes.forecasting.enabled` / `openboxes.bom.enabled` convention (one-line discoverability for admins). Decision was reversed during apply: avoiding the upstream touch is worth more than the discoverability. Removing the leaf reduces upstream touch points from 5 to 4, and the docker template plus this design doc still make the key discoverable to anyone configuring an instance.

## Upstream Touch Points

Per `rules/custom-package-isolation.md` and Upstream Compatibility rule 7, the following upstream files are modified. Each edit is minimal and listed here so future upstream merges know where to look.

| File | Edit | Reason |
|---|---|---|
| `grails-app/taglib/org/pih/warehouse/SelectTagLib.groovy` | One line: in `selectInventoryAdjustmentReasonCode` (line 230), change `attrs.from = ReasonCode.listInventoryAdjustmentReasonCodes()` to `attrs.from = customReasonCodeService.listInventoryAdjustmentReasonCodes()`. Add `def customReasonCodeService` near the top of the class. | The taglib is the chokepoint for GSPs that render the inventory-adjustment reason dropdown via the taglib (path 2: Create Adjustment per-line). Closes that path. |
| `grails-app/views/inventoryItem/_adjustStock.gsp` | Replace the `<g:select name="reasonCode" from="${... .listInventoryAdjustmentReasonCodes()}" ...>` block at lines 69-74 with `<g:selectInventoryAdjustmentReasonCode name="reasonCode" ...>` — same other attributes, just route through the taglib instead of bypassing it. | Closes path 1 (Adjust Stock modal). The Adjust Stock GSP currently calls the static enum method directly via `<g:select from="...">`, so the taglib edit alone is insufficient. Converting this one call site to the taglib consolidates paths 1 + 2 onto a single chokepoint. |
| `grails-app/controllers/org/pih/warehouse/api/ReasonCodeApiController.groovy` | Add `def customReasonCodeService` and change line 33 (`ActivityCode.ADJUST_INVENTORY` branch) from `getReasonCodes(ReasonCode.listInventoryAdjustmentReasonCodes())` to `getReasonCodes(customReasonCodeService.listInventoryAdjustmentReasonCodes())`. No other branches changed. | Closes path 4 (JSON API). No known React consumer today, but filtering prevents a future regression where a new frontend caller exposes DAMAGED in an inventory-adjustment dropdown. |
| `grails-app/views/product/_actions.gsp` | Wrap the `<div class="action-menu-item">` at lines 91-96 (the `createDamaged` link) in `<g:if test="${grailsApplication.config.openboxes.custom.adjustments.damaged.enabled}">…</g:if>` | Hides path 3's menu entry when the flag is off. The interceptor (`DamagedAdjustmentInterceptor`) provides the server-side enforcement; the GSP hide is the UX layer. |

**Custom code added (no merge risk):**
- `grails-app/services/org/pih/warehouse/custom/damagedAdjustments/CustomReasonCodeService.groovy` — wraps `ReasonCode.listInventoryAdjustmentReasonCodes()`.
- `grails-app/controllers/org/pih/warehouse/custom/damagedAdjustments/DamagedAdjustmentInterceptor.groovy` — guards `inventory/createDamaged`.
- `src/test/groovy/org/pih/warehouse/custom/damagedAdjustments/CustomReasonCodeServiceSpec.groovy` — unit test for the service.
- `src/integration-test/groovy/org/pih/warehouse/custom/damagedAdjustments/DamagedAdjustmentInterceptorSpec.groovy` — integration test for the interceptor.

## Risks / Trade-offs

| Risk | Mitigation |
|---|---|
| Edit to `SelectTagLib.groovy:230` may conflict on the next upstream pull if PIH changes that file. | The change is one line in one method. Conflict resolution is trivial: keep the custom-service call, drop any unrelated upstream edits to the same method. Documented here so the next merge knows where to look. |
| Flag is global per Grails instance — a multi-tenant deploy can't have one tenant with damaged adjustments allowed and another blocked. | Out of scope by D2/Non-Goals. If multi-tenancy becomes a requirement, migrate to a per-location `ActivityCode` (already the per-location toggle pattern — see `stock-transfer-documents`). |
| If an EST-level feature in the future also needs to wrap `listInventoryAdjustmentReasonCodes()`, two custom services would compete. | The custom service is a thin pass-through. If more filters are needed, extend `CustomReasonCodeService` with additional `?:` fallbacks per flag — keep one wrapper, multiple flags. |
| The `Damaged` TransactionType row remains in the DB, so a sufficiently-determined user with database access could insert a Transaction with that type directly. | Acceptable. The policy is enforced at the application layer, not at the schema layer. DB-level access is already privileged. |
| The GSP `<g:if>` hides the link but doesn't remove the underlying route — a user who knows the URL `/inventory/createDamaged?product.id=...` can still try it. | The interceptor blocks the URL server-side. The `<g:if>` is purely UX; defense-in-depth is the interceptor. |
| New URL interceptor changes interceptor ordering. | Grails 3 interceptors run in `order` field order (default 0). The `DamagedAdjustmentInterceptor` doesn't depend on session state beyond config, so default order is fine. RoleInterceptor uses `LOWEST_PRECEDENCE` to run last; ours can use the default to run earlier. |

## Migration Plan

1. **Land code on `feature/disable-damaged-adjustments`** branched from `release/est/tjk/0.9.7`. PR back into tjk.
2. **Default `false` everywhere.** No behavior change on any branch until a customer opts in via their `docker/openboxes.yml`.
3. **Tajikistan opts in** by setting `openboxes.custom.adjustments.damaged.enabled: true` in `docker/openboxes.yml` on `release/est/tjk/0.9.7`. Restart required (file-based config).
4. **Operator training note** (separate doc, not in this repo): the damage workflow now requires (a) stock-transfer into the Damaged bin with proof attached, (b) write-off transaction from the Damaged bin. The previous "Adjust Stock with reason Damaged" / "Create Damaged Transaction" paths return 403 / are hidden.
5. **Rollback:** flip the flag back to `false` and restart. No DB changes to revert. Code remains in place but inert.

### Other client branches

- **sp:** unaffected by default. If sp wants the same policy, set the flag in their `docker/openboxes.yml` after the next EST → sp merge brings the code in.
- **EST shared layer:** the code lives on the feature branch initially. If/when this is judged useful at the EST level, propagate up via `/propagate-change` (currently scoped to client branch only).

## Open Questions

- **Q1: Should `createConsumed` and `createExpired` get the same treatment?** Both follow the identical header-type pattern as `createDamaged` (`InventoryController.groovy:809,818`). The user's stated policy only mentions "damaged" — confirm in implementation review whether the same rationale (need documents → must use stock transfer) applies to expired/consumed stock. If yes, generalize the flag namespace to `openboxes.custom.adjustments.<reasonCode>.enabled`.
- **Q2: Error page UX.** `errors/handleForbidden` shows a generic forbidden page. Worth adding a more specific message ("Damaged adjustments are disabled at this site. Use a stock transfer with documents.")? Would require either a custom error action or a flash-message redirect. Decide during apply.
- **Q3: Should the i18n key `inventory.inventoryDamaged.label` get a tooltip explaining why the link is missing when hidden?** Probably no — hiding without explanation is the standard pattern in this app. Listed here only for explicit decision.

## Validation

Each item is verifiable from a real artifact captured during apply or by reading the indicated file. **Do not check** a box until the artifact named is produced.

- [ ] `grep -n "openboxes.custom.adjustments.damaged.enabled" grails-app/conf/application.yml` returns exactly one line, indented under `openboxes:` block, value `false`.
- [ ] Spock test `CustomReasonCodeServiceSpec` proves: (a) when flag is `false` the returned list does NOT contain `ReasonCode.DAMAGED`; (b) when flag is `true` the returned list equals `ReasonCode.listInventoryAdjustmentReasonCodes()` byte-for-byte. Test must use real `grailsApplication` config override, not a mocked service.
- [ ] Integration test `DamagedAdjustmentInterceptorSpec` makes an HTTP request to `/openboxes/inventory/createDamaged?product.id=<seeded id>` and asserts a redirect to `errors/handleForbidden` when the flag is `false`, AND a 200 (or whatever `createTransaction.gsp` renders) when the flag is `true`.
- [ ] `grep -n "customReasonCodeService" grails-app/taglib/org/pih/warehouse/SelectTagLib.groovy` returns exactly two lines (the `def` injection and the `attrs.from` call). No other taglib in `SelectTagLib.groovy` should reference the custom service.
- [ ] `grep -n "selectInventoryAdjustmentReasonCode" grails-app/views/inventoryItem/_adjustStock.gsp` returns one line; `grep -n "ReasonCode.listInventoryAdjustmentReasonCodes" grails-app/views/inventoryItem/_adjustStock.gsp` returns zero lines (the direct static call is gone).
- [ ] `grep -n "customReasonCodeService" grails-app/controllers/org/pih/warehouse/api/ReasonCodeApiController.groovy` returns exactly two lines (the `def` injection and the line-33 call). Other `ReasonCode.listXxxReasonCodes()` calls in the same file are unchanged.
- [ ] `grep -A5 'class="action-menu-item"' grails-app/views/product/_actions.gsp | grep -B1 'createDamaged'` shows the `createDamaged` link inside a `<g:if test="${grailsApplication.config.openboxes.custom.adjustments.damaged.enabled}">` wrapper.
- [ ] `./gradlew compileGroovy` passes (custom service + interceptor compile cleanly with Groovy 2.4 / Java 8).
- [ ] Manual smoke test (with flag `true` in `docker/openboxes.yml`): (a) Adjust Stock modal dropdown lacks DAMAGED; (b) Create Adjustment per-line dropdown lacks DAMAGED; (c) Product menu lacks the "Create Damaged" item; (d) direct URL `/openboxes/inventory/createDamaged?product.id=...` returns the forbidden page; (e) `curl /openboxes/api/reasonCodes?activityCode=ADJUST_INVENTORY` returns a JSON body whose `data` array does not contain a DAMAGED entry. Capture screenshots/output into the PR.

## Unverified Assumptions — resolved during proposal review

All five assumptions from the original draft have been resolved by grep + repo inspection. Notes below describe the evidence and any design changes triggered by the findings.

1. **Grails 3 URL interceptor wiring resolves custom packages.** **Resolved by spike + precedent.** The existing `grails-app/controllers/org/pih/warehouse/custom/stockTransferDocuments/CustomStockTransferDocumentController.groovy` already lives under `org.pih.warehouse.custom.*` and works in production. Grails auto-discovers controllers and interceptors by `grails-app/controllers/` location, not by package, so the same applies to `DamagedAdjustmentInterceptor.groovy` under `org.pih.warehouse.custom.damagedAdjustments`. Additionally verified empirically: a stub `HelloInterceptor.groovy` placed at exactly that path compiled cleanly via `./gradlew compileGroovy` (`BUILD SUCCESSFUL in 11s`), producing `build/classes/groovy/main/org/pih/warehouse/custom/damagedAdjustments/HelloInterceptor.class` — Grails recognized the file as part of the project's artefact compilation. Stub was removed after verification.
2. **GSP `<g:if test="${grailsApplication.config...}">` evaluates a newly-added nested config path.** **Resolved by analogy.** The existing keys read from GSP (`openboxes.locale.defaultCurrencyCode` at `views/order/show.gsp:126`; `openboxes.ajaxRequest.timeout` at `views/report/showTransactionReport.gsp:283`) are themselves 3-level nested paths through `ConfigObject`. The Groovy semantics of `ConfigObject` resolve any nesting depth uniformly — there's no path-length restriction. Adding a 5-level key (`openboxes.custom.adjustments.damaged.enabled`) is the same operation. No spike needed.
3. **`createDamaged` is the only user-initiated UI write path that sets the DAMAGE TransactionType.** **Resolved.** `grep -rn 'DAMAGE_TRANSACTION_TYPE_ID' grails-app/ src/main/` returns 5 hits: the constant declaration (`Constants.groovy:95`), the user-initiated write path (`InventoryController.createDamaged:824` — the one we gate), two read-only service callers that aggregate transaction history (`RefreshInventoryTransactionsSummaryEventService.groovy:22`, `InventoryTransactionSummaryService.groovy:219`), and one read-only comparison in a reporting controller (`ConsumptionController.groovy:174`). Only `createDamaged` is a user-initiated write — the interceptor's existing `match(controller: 'inventory', action: 'createDamaged')` is sufficient.
4. **All consumers of `ReasonCode.listInventoryAdjustmentReasonCodes()` are reached by the taglib edit.** **Resolved with a design correction.** `grep -rn 'listInventoryAdjustmentReasonCodes' grails-app/ src/main/` returns 4 hits: the declaration (`ReasonCode.groovy:141`), the taglib (`SelectTagLib.groovy:230` — the one we edit), **`_adjustStock.gsp:71` which bypasses the taglib and calls the static method directly via `<g:select from="...">`**, and `ReasonCodeApiController.groovy:33` which exposes the list via JSON. The original design's "single chokepoint" claim was wrong. Fix: D2.a converts `_adjustStock.gsp` to use the taglib (one tag swap), and D2.b filters the API controller's `ADJUST_INVENTORY` branch (one method call swap). After these edits the chokepoint claim holds again. The cycle-count React code that consumes reason codes uses a different list (`listCycleCountReasonCodes()`) — unaffected.
5. **The flag-default-in-YAML convention reliably overrides at runtime via `docker/openboxes.yml`.** **Resolved.** `grails-app/conf/application.yml:32,35` registers `${catalina.base}/.grails/openboxes.yml` and `~/.grails/openboxes.yml` in `grails.config.locations` (the config override chain). `docker/docker-compose.yml` mounts `./openboxes.yml` to `/app/.grails/openboxes.yml:ro`, and the Dockerfile (`docker/Dockerfile:16`) sets the `openboxes` user's home to `/app` — so `~/.grails/openboxes.yml` resolves to the mounted file. Grails' config loader merges YAML by deep-merging maps, so a new nested key in the override file overrides the same-named key in `application.yml` regardless of nesting depth. The Tajikistan override (`openboxes.custom.adjustments.damaged.enabled: true`) will work the same way as `openboxes.forecasting.enabled: false` overrides do today.

## Confidence: 9/10

**Rationale.** All five unverified assumptions resolved by a mix of grep + repo inspection + one compile spike. The "single chokepoint" claim was wrong in the first draft, but the grep that surfaced the issue (Assumption 4) also surfaced both leaks (`_adjustStock.gsp:71`, `ReasonCodeApiController:33`), and the design now covers them via D2.a and D2.b. The upstream-touch-points count grew from 3 to 5, all itemized; none of the added edits is more than a one-line swap.

Assumption 1 (Grails 3 interceptor discovery on custom subpackages) was lifted from "convention says yes" to "verified" by dropping a stub `HelloInterceptor` at `grails-app/controllers/org/pih/warehouse/custom/damagedAdjustments/` and running `./gradlew compileGroovy` — `BUILD SUCCESSFUL in 11s`, `.class` file produced at the expected path, no warnings traceable to the stub. Combined with `CustomStockTransferDocumentController.groovy` as a runtime precedent for the same package layout, that's strong evidence the production interceptor will be discovered.

**Why not 10?** One residual risk: **D2.a changes the runtime semantics of `_adjustStock.gsp` even when the flag is `true`.** The current `<g:select from="${ReasonCode.listInventoryAdjustmentReasonCodes()}">` and the taglib-routed `<g:selectInventoryAdjustmentReasonCode>` produce the same set of options, but they go through different code paths (`g.select` directly vs. through the taglib's `attrs.optionValue` setter). A visual or behavioral regression at flag=true is unlikely — the taglib literally calls `g.select(attrs)` after setting `from` and `optionValue` — but it isn't proven until the flag=true screenshot in task 7.4 is captured. That's the cheapest residual risk to leave for apply.

A 10/10 score would also require booting Grails with the production interceptor (not just the stub) and confirming `before()` actually fires for `inventory/createDamaged`. That's task 5.3's integration spec, which produces the same evidence and is part of normal apply work — so spending another spike on it now would be redundant.
