## Why

The current custom-role implementation spreads authorization decisions across many upstream backend and frontend files, increasing merge risk and making policy mismatches likely. The review also identified missing coverage and concrete enforcement gaps, so the implementation needs a focused refactor before the custom-role surface grows further.

## What Changes

- Centralize custom-role authorization decisions in a dedicated backend policy service under `org.pih.warehouse.custom.*`.
- Reduce upstream edits to narrow hook points that delegate to the custom policy service for interceptor checks, menu visibility, app-context/session permissions, GSP action visibility, and API/controller enforcement.
- Move custom frontend permission helpers under `src/js/custom/*` and have React screens consume a canonical custom-role permission payload instead of repeating role-specific checks inline.
- Close known authorization inconsistencies, including inbound movement creation through combined-shipment endpoints and reporting-user stocklist email visibility.
- Preserve existing behavior for standard OpenBoxes roles and for users whose higher core roles should override custom-role restrictions.
- Add backend and frontend regression coverage for custom-role policy resolution, menu/action visibility, direct URL/API enforcement, location chooser behavior, and frontend permission gates.

## Capabilities

### New Capabilities

- `custom-role-policy-isolation`: Covers the maintainability contract for custom-role code isolation, canonical policy evaluation, and test coverage expectations.

### Modified Capabilities

- `custom-roles-access`: Tightens existing custom-role access requirements so every restricted workflow is denied consistently through UI, controller, and API paths, including combined-shipment inbound creation and stocklist email actions.

## Impact

- Affected backend areas include `RoleInterceptor`, `MegamenuService`, `AuthTagLib`, app-context/session payload generation, relevant controllers/APIs, and new custom service/tests under `org.pih.warehouse.custom.*`.
- Affected frontend areas include role constants, custom permission helpers under `src/js/custom/*`, and React action/menu visibility call sites that currently duplicate role-specific conditions.
- Test impact includes new or revised backend unit/integration tests and Jest/React Testing Library tests for custom-role UI gates.
- No database schema or external dependency changes are expected.
