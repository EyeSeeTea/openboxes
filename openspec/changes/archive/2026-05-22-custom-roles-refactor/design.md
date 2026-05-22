## Context

Custom roles were added to enforce a fixed access matrix for Facility Storekeeper, Regional Warehouse User, RPC Superuser, and Reporting User. The current branch implements that matrix by spreading role checks through interceptors, services, taglibs, GSPs, React components, reducers, and hooks. That makes the fork harder to replay onto upstream and creates multiple sources of truth for the same permission question.

The refactor must respect the EyeSeeTea fork rules: custom backend code belongs under `org.pih.warehouse.custom.*`, custom frontend code belongs under `src/js/custom/*`, and upstream files should only contain narrow delegation hooks where no extension point exists.

## Goals / Non-Goals

**Goals:**

- Make one backend custom-role policy service the canonical source for custom-role decisions.
- Keep custom-role code discoverable through isolated custom package paths.
- Minimize and document the remaining upstream touchpoints needed to enforce policy.
- Export one canonical custom-role permission payload for frontend and GSP visibility checks.
- Cover the custom-role matrix with regression tests across policy resolution, UI visibility, and direct controller/API enforcement.
- Fix the known policy gaps for combined-shipment inbound creation and reporting-user stocklist email.

**Non-Goals:**

- Redesign OpenBoxes' general RBAC model or replace existing core-role behavior.
- Change the approved access matrix beyond the tightening described in the specs.
- Add database schema changes or new external dependencies.
- Rewrite unrelated upstream controllers, services, or React screens for style cleanup.

## Decisions

1. Introduce a custom backend policy service under `org.pih.warehouse.custom.roles`.

   The service will expose focused methods for policy questions such as route access, menu visibility, module/action capability, app-context permissions, and effective custom-role evaluation for a user/location. This keeps custom-role rules in one place and makes tests direct. Alternative considered: continue adding conditions to `RoleInterceptor`, `MegamenuService`, `AuthTagLib`, and React components. That preserves the current fragmentation and is the source of the review findings.

2. Treat upstream files as hook points only.

   `RoleInterceptor`, `MegamenuService`, `AuthTagLib`, app-context generation, and any unavoidable controller/API checks should delegate to the custom policy service without embedding role matrices or endpoint lists inline. Alternative considered: move all enforcement into the interceptor only. That would reduce some duplication but would not cover menu generation, GSP action visibility, or frontend permissions cleanly.

3. Define endpoint policy as data near the custom service.

   Controller/action and API route restrictions should be represented as structured Groovy data or small policy classes under the custom package, with tests for the route list. This is preferable to scattered `if` chains because gaps like `createCombinedShipments` become easier to spot and review.

4. Export canonical frontend permissions from the backend.

   The backend should include a compact custom-role permission map in the existing app-context/session payload. React helpers under `src/js/custom/roles` should read that payload and expose intent-based predicates such as "can create inbound movement" or "can send stocklist email." Alternative considered: duplicate role-name checks in every component. That creates UI/backend drift.

5. Preserve core-role precedence.

   The policy service should short-circuit when the effective current-location roles include existing higher core roles that already authorize the workflow. Custom restrictions should apply only to users whose effective access is governed by the custom roles. This preserves the existing spec and limits regression risk.

6. Use layered tests.

   Policy unit tests should cover the access matrix and route/action classifications. Integration or controller tests should cover direct URL/API denial for high-risk endpoints. Frontend tests should cover the permission helper and selected action visibility call sites. This gives useful coverage without requiring every page to be rendered in a browser test.

## Risks / Trade-offs

- Incomplete upstream hook replacement -> Mitigation: inventory every current custom-role condition from the branch before coding and either move it behind the policy service or document why it remains.
- Route-policy drift as upstream changes controller/action names -> Mitigation: keep route restrictions as named data with tests for known restricted endpoints and add regression cases for review findings.
- Frontend payload mismatch with legacy session fields -> Mitigation: keep existing fields during the refactor and add the new permission map as an additive payload until callers migrate.
- Tests may be slow if every scenario is integration-level -> Mitigation: put the full matrix in fast service/helper tests and reserve integration tests for direct access enforcement and location-chooser behavior.
- Core-role precedence could be misapplied for users with mixed roles -> Mitigation: explicitly test custom-only, custom-plus-higher-core, and unrelated-role users at the current location.

## Migration Plan

1. Add the custom backend policy service and tests without changing existing behavior.
2. Route `RoleInterceptor`, menu filtering, app-context/session permission payloads, and taglib visibility checks through the service.
3. Move frontend role helpers under `src/js/custom/roles` and update selected React call sites to use the canonical permission payload.
4. Remove duplicated role-specific conditions that have been replaced by service/helper calls.
5. Add regression tests for combined-shipment inbound creation, reporting-user stocklist email, location chooser access, and representative menu/action visibility.
6. Run focused backend and frontend test suites, then the broader project test commands as practical.

Rollback is a code rollback only; no database migration is expected.

## Open Questions

- Whether the existing location chooser pre-warehouse flow fails for location-scoped custom-role-only users must be validated during implementation and covered by a regression test either way.
- The final list of unavoidable upstream touchpoints should be recorded during implementation so future upstream replays know where the fork hooks live.
