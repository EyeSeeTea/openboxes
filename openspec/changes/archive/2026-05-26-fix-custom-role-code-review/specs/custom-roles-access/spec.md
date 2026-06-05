## MODIFIED Requirements

### Requirement: Custom operational roles SHALL be assignable role types
The system SHALL define and seed assignable role records for `ROLE_FACILITY_STOREKEEPER`, `ROLE_REGIONAL_WAREHOUSE`, `ROLE_RPC_SUPERUSER`, and `ROLE_REPORTING_USER`. The backend `RoleType` enum and frontend role constants SHALL expose the same role identifiers. These custom roles SHALL be treated as policy roles below `ROLE_ASSISTANT` and above `ROLE_AUTHENTICATED` in the core role ordering so they do not automatically satisfy Assistant-level authorization checks.

#### Scenario: Custom roles are available for assignment
- **WHEN** an administrator opens user role assignment for a location
- **THEN** the role options SHALL include Facility Storekeeper, Regional Warehouse User, RPC Superuser, and Reporting User

#### Scenario: Frontend and backend use matching role identifiers
- **WHEN** the session payload includes one of the custom role identifiers
- **THEN** frontend permission checks SHALL evaluate the same identifier used by backend authorization checks

#### Scenario: Custom roles do not expand to Assistant access
- **WHEN** authorization expands `ROLE_ASSISTANT` or compares custom roles against the core hierarchy
- **THEN** Facility Storekeeper, Regional Warehouse User, RPC Superuser, and Reporting User SHALL NOT automatically satisfy Assistant, Manager, Admin, or Superuser access
- **AND** any write access above Browser/Authenticated behavior SHALL be granted explicitly by the custom policy service

#### Scenario: Role priority is documented
- **WHEN** a developer reviews the `RoleType` enum
- **THEN** the custom role priority SHALL be documented as a policy-role level below Assistant and above Authenticated
- **AND** tests SHALL cover that role expansion preserves this hierarchy

### Requirement: Custom role enforcement SHALL be applied consistently across UI and backend
The system SHALL enforce custom role policy at menu visibility, page action visibility, server-rendered views, React views, controller interceptors, and API/controller endpoints. UI hiding SHALL improve usability but SHALL NOT be the only enforcement layer. Requestor/authenticated routing decisions SHALL treat every active custom policy consistently, including `ROLE_REPORTING_USER`.

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
- **AND** this precedence SHALL be covered by regression tests so `ROLE_ASSISTANT` handling is explicit

#### Scenario: Unrelated existing roles are unchanged
- **WHEN** a user has only pre-existing OpenBoxes roles
- **THEN** authorization, menu visibility, and page actions SHALL behave as they did before this change

#### Scenario: Location-scoped custom role can choose an authorized warehouse
- **WHEN** a custom-role-only user has the role assigned only at a location and logs in without a remembered warehouse
- **THEN** the location chooser SHALL return the authorized locations for that user
- **AND** the user SHALL NOT need a global default role or remembered warehouse to select an authorized location

#### Scenario: Default custom role location visibility is explicit
- **WHEN** a custom-role-only user has Facility Storekeeper, Regional Warehouse User, RPC Superuser, or Reporting User assigned as a default role
- **THEN** location chooser behavior SHALL follow the approved default-role rule for custom roles
- **AND** automated tests SHALL distinguish default custom roles from location-scoped custom roles

#### Scenario: Active custom policies are not treated as plain authenticated requestors
- **WHEN** a user has highest role `ROLE_AUTHENTICATED` or otherwise enters authenticated/requestor menu branching while an active custom policy exists
- **THEN** the system SHALL use custom-policy-aware branching rather than the plain requestor menu behavior
- **AND** Facility Storekeeper, Regional Warehouse User, RPC Superuser, and Reporting User SHALL be handled consistently
