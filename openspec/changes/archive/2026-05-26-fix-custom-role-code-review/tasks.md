## 1. Policy Helpers

- [x] 1.1 Add or expose a canonical `hasAnyCustomPolicy` helper through the service layer used by upstream callers.
- [x] 1.2 Add reusable menu role decision helpers for minimum-role and supplemental-role checks, including RPC Superuser exceptions.
- [x] 1.3 Add a short code comment documenting why custom role priority `4` is below Assistant and above Authenticated.

## 2. Backend Caller Cleanup

- [x] 2.1 Update `ApiController.getMenuConfig` to use a generic custom-policy presence check instead of enumerating individual custom policies.
- [x] 2.2 Update `ApiController.getAppContext` to avoid exposing redundant per-role policy flags unless a current committed consumer requires them.
- [x] 2.3 Update `DashboardController` authenticated/requestor branching to use a generic custom-policy presence check consistently, including Reporting User.
- [x] 2.4 Update `AuthTagLib` authenticated/higher-than-authenticated tags to use a generic custom-policy presence check.
- [x] 2.5 Update `MegamenuService` to delegate menu authorization decisions to reusable policy/service helpers rather than private duplicated role-resolution logic.

## 3. Location Chooser Behavior

- [x] 3.1 Confirm the approved behavior for default custom roles in location chooser and login location flows.
- [x] 3.2 Update `LocationApiController` and `LocationService` only if default custom roles should not behave like default Assistant for location visibility.
- [x] 3.3 Preserve location-scoped custom role selection for users without a default chooser role.

## 4. Regression Tests

- [x] 4.1 Add backend tests proving Reporting User is handled by generic custom-policy branching and not omitted from authenticated/requestor decisions.
- [x] 4.2 Add backend tests for app-context custom permissions and absence of redundant role flags if removed.
- [x] 4.3 Add backend tests for menu minimum-role and supplemental-role helper decisions, including RPC Superuser menu exceptions.
- [x] 4.4 Add backend tests for default custom roles versus location-scoped custom roles in location chooser/login location behavior.
- [x] 4.5 Add backend tests proving higher core roles, including Assistant, take precedence over custom policy restrictions.
- [x] 4.6 Add tests proving `RoleType.expand(RoleType.ROLE_ASSISTANT)` does not accidentally include custom roles and that priority `4` remains below Assistant.

## 5. Verification

- [x] 5.1 Run focused backend tests for custom role policy, taglib, location chooser, and menu behavior.
- [x] 5.2 Run frontend tests if app-context/session payload changes affect React consumers.
- [x] 5.3 Search for remaining direct custom-role checks outside canonical policy plumbing and justify or remove any business-decision duplicates.
- [x] 5.4 Verify no generated bundles or unrelated upstream-file formatting changes are included.
