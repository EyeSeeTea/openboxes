## Why

OpenBoxes needs customer-specific operational roles that match warehouse responsibilities more closely than the built-in hierarchical roles. The implemented branch adds four fixed custom role types and enforces their access matrix across menu visibility, page actions, controller guards, and API endpoints.

## What Changes

- Add `Facility Storekeeper`, `Regional Warehouse User`, `RPC Superuser`, and `Reporting User` as assignable custom role types.
- Seed database `Role` records for the new role types through custom Liquibase migrations.
- Enforce role-specific access for Dashboard, Inventory, Purchasing, Inbound, Outbound, Reporting, Products, and Stocklists.
- Hide menus and page-level create/edit/delete actions that the current custom role cannot use.
- Guard direct URL and API access so hidden actions cannot be reached by bypassing the UI.
- Preserve the higher-role override model: Assistant, Manager, Admin, and Superuser retain their existing access when present with a custom role.
- No generic permission framework or admin-configurable permission matrix is introduced.

## Capabilities

### New Capabilities
- `custom-roles-access`: Fixed custom-role authorization policies for the four customer roles, including menu visibility, page action visibility, controller/API enforcement, seeded role records, and role constants.

### Modified Capabilities
- None.

## Impact

- Affected backend code includes `RoleType`, `User`, `UserService`, `RoleInterceptor`, selected controllers/APIs, `MegamenuService`, `LocationService`, `AuthTagLib`, runtime RBAC configuration, messages, and custom database migrations.
- Affected frontend code includes role constants, session permission derivation, permission hooks, and list/header/table components for purchasing, products, stock movements, stocklists, and product suppliers.
- The change is intentionally instance-specific and does not add new external dependencies or replace the existing OpenBoxes authorization architecture.
