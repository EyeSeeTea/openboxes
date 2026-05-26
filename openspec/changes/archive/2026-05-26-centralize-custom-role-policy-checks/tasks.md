## 1. Audit Existing Custom Role Checks

- [x] 1.1 Search backend and frontend code for direct custom role identifiers outside policy, constants, migrations, and tests.
- [x] 1.2 Classify each direct role-name check as policy plumbing, compatibility membership exposure, or a business capability decision that should be refactored.
- [x] 1.3 Identify missing reusable capability methods needed by controllers, GSPs, taglibs, menu/app-context generation, or React action gates.
- [x] 1.4 Review duplicated permission logic between backend policy responses and frontend helpers, and mark frontend-derived authorization decisions for removal.

## 2. Backend Policy Centralization

- [x] 2.1 Add or reuse `CustomRolePolicyService` capability methods for recurring decisions such as stocklist management, inbound movement creation, product management, purchasing, superuser purchasing actions, and outbound creation.
- [x] 2.2 Replace backend business-decision role-name checks in controllers, services, interceptors, taglibs, and GSP-visible helpers with `CustomRolePolicyService` capability calls.
- [x] 2.3 Ensure any app-context or session permission payloads expose backend-derived custom role capabilities needed by frontend helpers.
- [x] 2.4 Keep unavoidable upstream edits limited to delegation calls and avoid reformatting unrelated code.

## 3. Frontend Permission Centralization

- [x] 3.1 Extend or reuse `src/js/custom/roles/customRolePermissions.js` helpers for any frontend capability checks needed by the refactor.
- [x] 3.2 Replace React component direct custom role-name checks with custom permission helpers backed by the canonical permission payload.
- [x] 3.3 Confirm frontend defaults preserve existing behavior for users without custom role permission payloads.
- [x] 3.4 Ensure frontend helpers are documented and implemented as UI visibility helpers only, with backend enforcement remaining authoritative.

## 4. Regression Coverage

- [x] 4.1 Add or update backend unit tests for `CustomRolePolicyService` capability decisions across Facility Storekeeper, Regional Warehouse User, RPC Superuser, and Reporting User.
- [x] 4.2 Add or update backend tests for representative consumers to verify hidden or denied actions delegate to the centralized policy.
- [x] 4.3 Add or update Jest tests for custom frontend permission helpers and representative action visibility.
- [x] 4.4 Add or update regression tests proving standard core roles are unaffected when no custom role policy is active.

## 5. Verification

- [x] 5.1 Re-run targeted searches for custom role identifiers and confirm remaining direct references are limited to allowed policy plumbing locations.
- [x] 5.2 Run targeted backend tests for custom role policy behavior.
- [x] 5.3 Run targeted frontend tests for custom role permission helpers or affected components.
- [x] 5.4 Update the change design's upstream touch points if implementation modifies additional upstream files.
