# custom-roles-access

> Capability: fixed custom-role authorization policies for the four customer roles,
> including menu visibility, page action visibility, controller/API enforcement,
> seeded role records, and role constants.
>
> Source change: `openspec/changes/archive/2026-05-21-custom-roles/`

## Requirements

### Requirement: Custom operational roles SHALL be assignable role types
The system SHALL define and seed assignable role records for `ROLE_FACILITY_STOREKEEPER`, `ROLE_REGIONAL_WAREHOUSE`, `ROLE_RPC_SUPERUSER`, and `ROLE_REPORTING_USER`. The backend `RoleType` enum and frontend role constants SHALL expose the same role identifiers.

#### Scenario: Custom roles are available for assignment
- **WHEN** an administrator opens user role assignment for a location
- **THEN** the role options SHALL include Facility Storekeeper, Regional Warehouse User, RPC Superuser, and Reporting User

#### Scenario: Frontend and backend use matching role identifiers
- **WHEN** the session payload includes one of the custom role identifiers
- **THEN** frontend permission checks SHALL evaluate the same identifier used by backend authorization checks

### Requirement: Facility Storekeeper SHALL follow the approved access matrix
The system SHALL enforce the Facility Storekeeper policy:

| Module | Access |
| ------ | ------ |
| Dashboard | Read |
| Inventory | Read/Write |
| Purchasing | No access |
| Inbound | Read/Write, except Create Inbound Movement is denied |
| Outbound | No access |
| Reporting | Read |
| Products | Read |
| Stocklists | No access |

#### Scenario: Facility Storekeeper cannot access purchasing
- **WHEN** a Facility Storekeeper opens the application menu or calls a purchasing controller/API directly
- **THEN** the Purchasing menu SHALL be hidden
- **AND** the direct request SHALL be denied

#### Scenario: Facility Storekeeper cannot create inbound movements
- **WHEN** a Facility Storekeeper opens the Inbound menu or calls an inbound-movement creation endpoint directly
- **THEN** the Create Inbound Movement action SHALL be hidden
- **AND** the direct creation request SHALL be denied

#### Scenario: Facility Storekeeper cannot access outbound or stocklists
- **WHEN** a Facility Storekeeper opens the menu or calls outbound or stocklist controllers/APIs directly
- **THEN** Outbound and Stocklists menu entries SHALL be hidden
- **AND** direct outbound and stocklist requests SHALL be denied

#### Scenario: Facility Storekeeper can manage inventory
- **WHEN** a Facility Storekeeper performs an inventory read or write action
- **THEN** the action SHALL be allowed when the user is authorized for the current location

### Requirement: Regional Warehouse User SHALL follow the approved access matrix
The system SHALL enforce the Regional Warehouse User policy:

| Module | Access |
| ------ | ------ |
| Dashboard | Read |
| Inventory | Read/Write |
| Purchasing | No access |
| Inbound | Read/Write, except Create Inbound Movement is denied |
| Outbound | Read/Write |
| Reporting | Read |
| Products | Read |
| Stocklists | Read/Write |

#### Scenario: Regional Warehouse User cannot access purchasing
- **WHEN** a Regional Warehouse User opens the application menu or calls a purchasing controller/API directly
- **THEN** the Purchasing menu SHALL be hidden
- **AND** the direct request SHALL be denied

#### Scenario: Regional Warehouse User cannot create inbound movements
- **WHEN** a Regional Warehouse User opens the Inbound menu or calls an inbound-movement creation endpoint directly
- **THEN** the Create Inbound Movement action SHALL be hidden
- **AND** the direct creation request SHALL be denied

#### Scenario: Regional Warehouse User can manage outbound and stocklists
- **WHEN** a Regional Warehouse User performs outbound or stocklist read/write actions
- **THEN** the actions SHALL be allowed when the user is authorized for the current location

#### Scenario: Regional Warehouse User has read-only product access
- **WHEN** a Regional Warehouse User opens product pages or calls product read endpoints
- **THEN** read access SHALL be allowed
- **AND** product create, update, import, preference, and delete actions SHALL be hidden or denied

### Requirement: RPC Superuser SHALL follow the approved access matrix
The system SHALL enforce the RPC Superuser policy:

| Module | Access |
| ------ | ------ |
| Dashboard | Read |
| Inventory | Read/Write |
| Purchasing | Read/Write |
| Inbound | Read/Write |
| Outbound | Read/Write |
| Reporting | Read |
| Products | Read/Write |
| Stocklists | Read/Write |

#### Scenario: RPC Superuser can use operational write workflows
- **WHEN** an RPC Superuser performs inventory, purchasing, inbound, outbound, product, or stocklist write actions
- **THEN** the actions SHALL be allowed when the user is authorized for the current location

#### Scenario: RPC Superuser has read-only dashboard access
- **WHEN** an RPC Superuser calls dashboard state-changing actions
- **THEN** the request SHALL be denied

#### Scenario: RPC Superuser can see write actions for allowed modules
- **WHEN** an RPC Superuser opens list pages for purchasing, products, stock movements, or stocklists
- **THEN** create, edit, import, and related write actions SHALL be visible for modules where the role has Read/Write access

### Requirement: Reporting User SHALL follow the approved access matrix
The system SHALL enforce the Reporting User policy:

| Module | Access |
| ------ | ------ |
| Dashboard | Read |
| Inventory | Read |
| Purchasing | No access |
| Inbound | Read |
| Outbound | Read |
| Reporting | Read |
| Products | Read |
| Stocklists | Read |

#### Scenario: Reporting User cannot access purchasing
- **WHEN** a Reporting User opens the application menu or calls a purchasing controller/API directly
- **THEN** the Purchasing menu SHALL be hidden
- **AND** the direct request SHALL be denied

#### Scenario: Reporting User cannot perform write actions
- **WHEN** a Reporting User calls inventory, inbound, outbound, product, or stocklist create/update/delete/status-changing endpoints
- **THEN** the request SHALL be denied

#### Scenario: Reporting User can read operational data
- **WHEN** a Reporting User opens dashboard, inventory, inbound, outbound, reporting, product, or stocklist read views
- **THEN** the views SHALL be available when the user is authorized for the current location

### Requirement: Custom role enforcement SHALL be applied consistently across UI and backend
The system SHALL enforce custom role policy at menu visibility, page action visibility, server-rendered views, React views, controller interceptors, and API/controller endpoints. UI hiding SHALL improve usability but SHALL NOT be the only enforcement layer.

#### Scenario: Hidden action is still denied by direct URL
- **WHEN** a custom-role user calls a hidden action directly by URL or API
- **THEN** the backend SHALL deny the request according to that role's policy

#### Scenario: Allowed action remains visible and executable
- **WHEN** a custom-role user opens a module with Read/Write access
- **THEN** the allowed page actions SHALL be visible
- **AND** the matching backend request SHALL be accepted

#### Scenario: Higher core role takes precedence
- **WHEN** a user has a custom role and also has Assistant, Manager, Admin, or Superuser access in the effective current-location roles
- **THEN** the higher core role SHALL retain its existing authorization behavior
- **AND** custom role restrictions SHALL NOT reduce that higher-role access

#### Scenario: Unrelated existing roles are unchanged
- **WHEN** a user has only pre-existing OpenBoxes roles
- **THEN** authorization, menu visibility, and page actions SHALL behave as they did before this change
