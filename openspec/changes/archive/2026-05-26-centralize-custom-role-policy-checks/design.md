## Context

OpenBoxes custom roles are already represented by custom backend policy code and a custom frontend permission helper, but some integration points still risk checking `RoleType` names directly. Those direct checks make future role behavior harder to audit because authorization can drift between controllers, GSPs, taglibs, menu configuration, and React action visibility. Frontend helpers also need to avoid becoming a second authorization model; they should mirror backend policy responses only for UI visibility.

The existing `custom-role-policy-isolation` capability requires a canonical custom-role policy model. This change continues that refactor by making named capability methods on `CustomRolePolicyService` the preferred backend contract and by keeping frontend decisions derived from backend-provided permission payloads.

## Goals / Non-Goals

**Goals:**

- Centralize custom-role decisions behind `CustomRolePolicyService` capability methods.
- Replace scattered direct custom role-name checks in touched controllers, GSPs, taglibs, and React components with reusable capability checks.
- Preserve backend enforcement as the source of truth for custom-role behavior.
- Keep frontend permission helpers focused on visibility decisions derived from backend policy responses.
- Keep upstream-file edits surgical and limited to delegation points required by existing OpenBoxes extension points.
- Add regression coverage for representative capability checks, their UI/backend consumers, and standard core roles with no active custom policy.

**Non-Goals:**

- Change the approved custom role access matrix.
- Add new custom roles or rename existing role identifiers.
- Introduce new database tables, migrations, or external dependencies.
- Rewrite the broader OpenBoxes authorization model.
- Move every upstream authorization concern into custom code when it is unrelated to custom roles.

## Decisions

1. Use capability methods as the integration contract.

Callers that need custom-role-sensitive decisions will ask methods such as `canManageStocklists`, `canCreateInboundMovement`, `canManageProducts`, `canUseSuperuserPurchasingActions`, and any newly needed equivalent capability methods. This keeps role-name comparisons inside the custom policy service instead of spreading `ROLE_FACILITY_STOREKEEPER`, `ROLE_REGIONAL_WAREHOUSE`, `ROLE_RPC_SUPERUSER`, or `ROLE_REPORTING_USER` checks across application layers.

Alternative considered: continue allowing each caller to check role names directly. That is simpler locally but makes the policy harder to audit and increases the chance that UI visibility and backend enforcement diverge.

2. Keep backend policy evaluation canonical.

Backend enforcement, GSP/taglib visibility, menu visibility, and app-context permission payloads will derive custom-role decisions from `CustomRolePolicyService`. React components will consume the canonical permission payload through `src/js/custom/roles/customRolePermissions.js` rather than duplicating the role matrix in JavaScript. Frontend helpers are not an authorization boundary; they exist to hide or show actions based on permissions already evaluated by the backend.

Alternative considered: implement parallel frontend role-name logic for each action. That would reduce backend changes for some screens but would make hidden actions easier to desynchronize from direct URL/API enforcement.

3. Preserve standard core role behavior when no custom policy is active.

Capability checks should defer to existing OpenBoxes behavior for standard users without an active custom role policy. Custom-only manage capabilities should default to false when no custom policy is active so they do not grant product or stocklist write UI to standard non-admin users; existing core-role checks remain responsible for standard access. Regression tests should cover at least one standard core role path to prove the custom policy layer is not restricting or expanding users who are outside the custom role matrix.

Alternative considered: only test the four custom roles. That verifies the matrix but misses regressions where policy defaults accidentally affect existing Assistant, Manager, Admin, or Superuser behavior.

4. Limit upstream touch points to delegation.

When existing upstream files must be changed, the edit should be a narrow call into custom services, taglibs, or frontend helpers. New reusable policy logic belongs under `org.pih.warehouse.custom.roles` or `src/js/custom/roles`.

Alternative considered: refactor upstream authorization structures broadly. That would create larger merge conflicts with upstream and is unnecessary for isolating custom-role behavior.

5. Treat role-name checks as acceptable only inside policy plumbing.

Direct custom role-name comparisons are expected inside `CustomRolePolicyService`, role seeding/migration files, role constants, and compatibility plumbing such as methods whose purpose is to expose specific role membership. They should not appear in controllers, GSP action checks, taglibs, or React components when a capability decision is available.

Alternative considered: ban all direct custom role-name references. That would be too strict because constants, migrations, tests, and the policy service itself need to name the roles explicitly.

## Risks / Trade-offs

- Missed direct checks in rarely used views or controllers -> Use targeted searches for custom role constants and replace representative consumers with policy calls.
- Frontend action visibility could still drift from backend enforcement -> Keep React helpers backed by `session.customRolePermissions` and add tests for representative allowed and denied payloads.
- Frontend helpers could be mistaken for authorization -> Document and test them as visibility helpers only; backend enforcement remains mandatory.
- Standard roles could be unintentionally restricted by custom defaults -> Add regression coverage for users with no active custom role policy.
- Capability method names can become too granular -> Add methods only for reusable business capabilities, not one-off button names.
- Upstream merge conflicts from necessary delegation edits -> Keep edits minimal and document upstream touch points in this design.

## Migration Plan

1. Audit direct references to custom role constants outside custom policy, constants, migrations, and tests.
2. Add or reuse `CustomRolePolicyService` capability methods for recurring decisions.
3. Replace scattered backend checks with capability method calls or permission payload lookups.
4. Replace React role-name checks with `src/js/custom/roles/customRolePermissions.js` helpers.
5. Review duplicated frontend/backend permission logic and remove frontend derivation of authorization decisions.
6. Run targeted backend and frontend tests for custom-role policy behavior and standard core role fallbacks.

Rollback is code-only: revert the refactor commits. No data migration or configuration rollback is expected.

## Upstream Touch Points

- `grails-app/controllers/**`: only where existing direct custom role-name checks must delegate to custom policy capabilities.
- `grails-app/services/**`: only where existing menu/location/app-context behavior must delegate to custom policy capabilities.
- `grails-app/taglib/**` and `grails-app/views/**`: only where server-rendered action visibility must delegate to custom policy capabilities.
- `src/js/components/**`: only where existing React screens must call custom permission helpers instead of direct role-name checks.

## Open Questions

- Which remaining direct role-name checks are compatibility helpers that should stay as-is versus business capability decisions that should move behind `CustomRolePolicyService`?
