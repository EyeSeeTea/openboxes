## 1. Inventory Current Custom Role Surface

- [x] 1.1 Search backend files for custom-role identifiers, custom-role helper methods, and role-specific controller/action checks.
- [x] 1.2 Search frontend files for custom-role identifiers, permission helpers, and inline role checks.
- [x] 1.3 Document the upstream hook points that must remain after refactor and the duplicated logic that should move into custom code.

## 2. Backend Policy Isolation

- [x] 2.1 Create a custom-role policy service under `org.pih.warehouse.custom.roles`.
- [x] 2.2 Move the custom-role matrix and module/action capabilities into the custom policy service or custom policy data classes.
- [x] 2.3 Add route classification for restricted controller/API workflows, including `stockMovement.createCombinedShipments` and `StockMovementApiController.createCombinedShipments`.
- [x] 2.4 Implement effective custom-role resolution for the current location while preserving higher core-role precedence.
- [x] 2.5 Add policy service methods for route access, menu visibility, page action visibility, GSP action visibility, and app-context permissions.

## 3. Backend Hook Migration

- [x] 3.1 Update `RoleInterceptor` to delegate custom-role route decisions to the custom policy service.
- [x] 3.2 Update menu filtering to delegate custom-role menu decisions to the custom policy service.
- [x] 3.3 Update taglib or GSP action visibility hooks to delegate custom-role action decisions to the custom policy service.
- [x] 3.4 Update app-context/session payload generation to expose canonical custom-role permissions for frontend callers.
- [x] 3.5 Remove duplicated custom-role matrices and endpoint lists from upstream backend files after each hook is delegated.
- [x] 3.6 Validate the location chooser flow for location-scoped custom-role-only users without a remembered warehouse and adjust policy integration if needed.

## 4. Frontend Policy Isolation

- [x] 4.1 Create custom frontend role permission helpers under `src/js/custom/roles`.
- [x] 4.2 Update React role/action visibility call sites to consume the canonical permission payload through custom helpers.
- [x] 4.3 Hide inbound creation actions for Facility Storekeeper and Regional Warehouse users, including combined-shipment creation from purchase orders.
- [x] 4.4 Hide stocklist email actions for Reporting User wherever the stocklist summary/action UI is rendered.
- [x] 4.5 Remove duplicated custom-role checks from non-custom frontend files once callers use the custom helpers.

## 5. Backend Test Coverage

- [x] 5.1 Add unit tests for the custom-role policy matrix for all four custom roles.
- [x] 5.2 Add unit tests for higher core-role precedence and unrelated existing-role behavior.
- [x] 5.3 Add tests for route classification and direct denial of restricted workflows, including combined-shipment inbound creation.
- [x] 5.4 Add tests for menu visibility and server-rendered action visibility decisions.
- [x] 5.5 Add a regression test for location chooser access by a location-scoped custom-role-only user without a remembered warehouse.

## 6. Frontend Test Coverage

- [x] 6.1 Add Jest tests for custom frontend permission helpers using representative permission payloads.
- [x] 6.2 Add React component tests for representative hidden and visible actions for custom-role users.
- [x] 6.3 Add regression coverage that Reporting User does not see the stocklist email action.
- [x] 6.4 Add regression coverage that Facility Storekeeper and Regional Warehouse users do not see inbound creation or combined-shipment creation actions.

## 7. Verification

- [x] 7.1 Run focused backend tests for custom-role policy and affected controllers/services.
- [x] 7.2 Run focused frontend Jest tests for custom-role helpers and affected components.
- [x] 7.3 Run broader backend and frontend test commands as practical for the branch.
- [x] 7.4 Review remaining upstream-file diffs and confirm custom-role logic is isolated except for narrow delegation hooks.
