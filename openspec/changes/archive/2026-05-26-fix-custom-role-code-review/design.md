## Context

The custom roles PR introduces four policy-driven operational roles and wires them through backend authorization, menu visibility, app-context payloads, server-rendered taglibs, and React action gates. Manual review found several places where upstream files enumerate individual custom roles directly, omit `ROLE_REPORTING_USER`, or duplicate role-resolution logic that should live behind the custom policy layer.

This fork must keep custom behavior isolated where possible. Because these role policies necessarily hook into upstream controllers, taglibs, and services, the implementation should minimize upstream edits and centralize the custom logic in `CustomRolePolicyService` and frontend helpers under `src/js/custom/roles`.

## Goals / Non-Goals

**Goals:**

- Replace repeated custom-role flag checks with canonical `hasAnyCustomPolicy` or named capability methods.
- Keep the app-context payload focused on `customRolePermissions` instead of redundant per-role policy booleans.
- Make `ROLE_REPORTING_USER` behavior consistent with the other custom policies in authenticated/requestor routing decisions.
- Centralize menu authorization helper logic outside `MegamenuService` so menu construction delegates policy decisions.
- Clarify the intended hierarchy rule: custom roles are policy roles at priority `4`, below `ROLE_ASSISTANT`, and higher core roles retain standard behavior.
- Add regression coverage for location chooser behavior, policy precedence, role priority expansion, and menu helper decisions.

**Non-Goals:**

- Redesign the custom role access matrix.
- Add new roles or migrations.
- Change standard OpenBoxes authorization for users without an active custom policy.
- Rework frontend action visibility beyond consuming the canonical permissions payload.

## Decisions

1. Use `CustomRolePolicyService.hasAnyCustomPolicy` for requestor/authenticated branching.

Rationale: The branch should answer “is a custom policy active?” rather than enumerate every custom role. This fixes the missing `ROLE_REPORTING_USER` case and avoids future omissions.

Alternative considered: Add `hasReportingUserPolicy` alongside the existing three checks. That is smaller but preserves the duplication and future maintenance risk.

2. Keep `customRolePermissions` as the session payload contract.

Rationale: `customRolePermissions.activeCustomRolePolicy` identifies the active policy, and the remaining properties express behavior. Returning separate `has{role}Policy` fields duplicates state and encourages consumers to branch on role names instead of capabilities.

Alternative considered: Keep both payloads for compatibility. This is unnecessary unless existing committed frontend code consumes the individual flags.

3. Move menu role decisions into the policy layer or a reusable service helper.

Rationale: `MegamenuService` should build and translate menus. Custom exceptions such as RPC Superuser seeing Assistant-gated purchasing/product sections are authorization decisions and belong with the custom role policy.

Alternative considered: Keep private helper methods in `MegamenuService`. This works functionally but repeats role-resolution logic and blurs service responsibilities.

4. Treat custom role priority `4` as an explicit policy-role hierarchy level.

Rationale: Priority `4` makes custom roles stronger than `ROLE_AUTHENTICATED` but weaker than `ROLE_ASSISTANT`, preventing automatic access to Assistant-gated write actions. Policy-specific allowances are then granted by `CustomRolePolicyService`.

Alternative considered: Give RPC Superuser a higher core-role-like priority. That would make it pass many generic checks implicitly and undermine the explicit policy matrix.

5. Preserve higher core role precedence.

Rationale: The existing spec states that Assistant, Manager, Admin, or Superuser effective access should not be reduced by an additional custom role. Implementation should keep this behavior but document and test it, especially because `ROLE_ASSISTANT` in `HIGHER_CORE_ROLE_NAMES` is easy to misread.

Alternative considered: Let custom roles restrict mixed-role users. That would be more restrictive but would contradict the current access spec and surprise users who already hold core roles.

## Risks / Trade-offs

- Risk: Removing individual app-context flags could break an unnoticed frontend consumer. Mitigation: search for all usages before removal and keep only if a current committed consumer exists.
- Risk: Location chooser semantics for default custom roles may be broader than intended. Mitigation: add tests for default-role and location-role-only users and preserve the current approved behavior explicitly.
- Risk: Moving menu helper logic could accidentally change menu visibility. Mitigation: add focused `MegamenuService` or `CustomRolePolicyService` tests for minimum role, supplemental role, and RPC Superuser exceptions.
- Risk: Upstream-file edits can increase merge conflicts. Mitigation: keep upstream changes surgical and document upstream touch points in this design.

## Migration Plan

No database migration is required. Deploy as a code-only change. Rollback is the previous custom-role PR behavior.

Implementation should proceed in small steps: add reusable policy/helper methods first, switch callers, remove redundant payload fields if unused, then add regression tests.

## Open Questions

- Should default custom roles see all enabled login/location-chooser locations, or should custom roles only grant access through explicit location assignments? The current implementation appears to allow default custom roles to behave like default Assistant for location visibility; tests should lock whichever behavior is approved.

## Upstream Touch Points

- `grails-app/controllers/org/pih/warehouse/api/ApiController.groovy`: session payload and requestor menu branching.
- `grails-app/controllers/org/pih/warehouse/api/LocationApiController.groovy`: location chooser role handling.
- `grails-app/controllers/org/pih/warehouse/user/DashboardController.groovy`: authenticated/requestor redirect and megamenu branching.
- `grails-app/services/org/pih/warehouse/api/MegamenuService.groovy`: menu authorization delegation.
- `grails-app/services/org/pih/warehouse/core/LocationService.groovy`: login location role handling.
- `grails-app/taglib/org/pih/warehouse/AuthTagLib.groovy`: authenticated/higher-than-authenticated checks.
- `src/main/groovy/org/pih/warehouse/core/RoleType.groovy`: comments/tests around role priority and expansion.

Deploy status: not deployed; proposal only.
