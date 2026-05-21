## 1. Role Model and Seed Data

- [x] 1.1 Add `ROLE_FACILITY_STOREKEEPER`, `ROLE_REGIONAL_WAREHOUSE`, `ROLE_RPC_SUPERUSER`, and `ROLE_REPORTING_USER` to the backend `RoleType` enum.
- [x] 1.2 Add matching role constants to the frontend role type constants.
- [x] 1.3 Add localized display labels for the four custom role types.
- [x] 1.4 Add custom Liquibase migrations that insert assignable `Role` rows for each custom role.
- [x] 1.5 Register the custom migrations in the custom changelog.

## 2. Authorization Predicates and Session State

- [x] 2.1 Extend user/session authorization helpers to expose custom role membership for the current location.
- [x] 2.2 Implement highest-core-role-wins behavior so Assistant, Manager, Admin, and Superuser access is not reduced by custom roles.
- [x] 2.3 Add frontend session permission derivation for custom role policies.
- [x] 2.4 Update reusable frontend permission hooks to evaluate custom roles consistently with backend policy.

## 3. Backend Enforcement

- [x] 3.1 Add RoleInterceptor allow/deny handling for Facility Storekeeper, Regional Warehouse User, RPC Superuser, and Reporting User.
- [x] 3.2 Deny direct purchasing and supplier access for Facility Storekeeper, Regional Warehouse User, and Reporting User.
- [x] 3.3 Deny Create Inbound Movement routes for Facility Storekeeper and Regional Warehouse User.
- [x] 3.4 Deny outbound and stocklist routes for Facility Storekeeper.
- [x] 3.5 Deny write/status-changing inventory, inbound, outbound, product, and stocklist routes for Reporting User.
- [x] 3.6 Allow RPC Superuser read/write access across inventory, purchasing, inbound, outbound, products, and stocklists while preserving read-only Dashboard behavior.
- [x] 3.7 Update selected controllers/APIs whose direct checks need explicit custom-role handling.

## 4. Menu and Server-Rendered UI

- [x] 4.1 Filter megamenu sections and links according to the role matrix.
- [x] 4.2 Hide Purchasing for Facility Storekeeper, Regional Warehouse User, and Reporting User.
- [x] 4.3 Hide Create Inbound Movement for Facility Storekeeper and Regional Warehouse User.
- [x] 4.4 Hide Outbound and Stocklists for Facility Storekeeper.
- [x] 4.5 Update GSP/taglib action visibility for order and stocklist pages.

## 5. React UI Enforcement

- [x] 5.1 Hide purchasing create/edit/delete actions for roles without Purchasing access.
- [x] 5.2 Hide product write/import/configuration actions for read-only product roles.
- [x] 5.3 Hide inbound, outbound, and stock movement write actions according to each role policy.
- [x] 5.4 Hide or show stocklist create/edit/delete actions according to each role policy.
- [x] 5.5 Keep RPC Superuser write actions visible for modules where the matrix grants Read/Write access.
- [x] 5.6 Keep Reporting User views read-only across inventory, inbound, outbound, products, and stocklists.

## 6. Validation

- [x] 6.1 Verify each role against the Dashboard, Inventory, Purchasing, Inbound, Outbound, Reporting, Products, and Stocklists matrix.
- [x] 6.2 Verify prohibited direct URL/API requests are denied even when UI actions are hidden.
- [x] 6.3 Verify users with only existing OpenBoxes roles retain previous behavior.
- [x] 6.4 Verify mixed custom role plus higher core role follows existing higher-role authorization behavior.
