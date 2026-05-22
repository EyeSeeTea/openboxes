## MODIFIED Requirements

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
- **AND** every direct inbound creation request SHALL be denied, including combined-shipment creation from purchase orders

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
- **AND** every direct inbound creation request SHALL be denied, including combined-shipment creation from purchase orders

#### Scenario: Regional Warehouse User can manage outbound and stocklists
- **WHEN** a Regional Warehouse User performs outbound or stocklist read/write actions
- **THEN** the actions SHALL be allowed when the user is authorized for the current location

#### Scenario: Regional Warehouse User has read-only product access
- **WHEN** a Regional Warehouse User opens product pages or calls product read endpoints
- **THEN** read access SHALL be allowed
- **AND** product create, update, import, preference, and delete actions SHALL be hidden or denied

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
- **AND** matching write actions, including stocklist email, SHALL be hidden from server-rendered and React views

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

#### Scenario: Location-scoped custom role can choose an authorized warehouse
- **WHEN** a custom-role-only user has the role assigned only at a location and logs in without a remembered warehouse
- **THEN** the location chooser SHALL return the authorized locations for that user
- **AND** the user SHALL NOT need a global default role or remembered warehouse to select an authorized location
