# custom-role-policy-isolation Specification

## Purpose
TBD - created by archiving change custom-roles-refactor. Update Purpose after archive.
## Requirements
### Requirement: Custom role policy SHALL be isolated behind custom services and helpers
The system SHALL keep custom-role authorization rules in custom backend services under `org.pih.warehouse.custom.*` and custom frontend helpers under `src/js/custom/*`. Upstream files SHALL only contain narrow delegation calls when required to connect existing OpenBoxes extension points to the custom policy. Controllers, GSPs, taglibs, and React components SHALL use reusable capability checks when a custom-role-sensitive business decision is needed, instead of duplicating direct custom role-name checks or individual per-role policy flags outside the canonical policy layer.

#### Scenario: Custom backend policy is discoverable
- **WHEN** a developer reviews backend custom-role authorization behavior
- **THEN** the role matrix, endpoint restrictions, and menu/action capability decisions SHALL be implemented under `org.pih.warehouse.custom.*`
- **AND** upstream backend files SHALL NOT contain duplicated custom-role matrices or role-specific endpoint lists

#### Scenario: Custom backend callers use capability checks
- **WHEN** controllers, interceptors, services, taglibs, or GSPs evaluate custom-role-sensitive actions such as stocklist management, inbound movement creation, requestor menu branching, or authenticated-role branching
- **THEN** they SHALL delegate to `CustomRolePolicyService` capability methods or backend permission payloads derived from that service
- **AND** they SHALL NOT repeat direct checks for `ROLE_FACILITY_STOREKEEPER`, `ROLE_REGIONAL_WAREHOUSE`, `ROLE_RPC_SUPERUSER`, or `ROLE_REPORTING_USER` for those business decisions

#### Scenario: Custom frontend policy is discoverable
- **WHEN** a developer reviews frontend custom-role action visibility
- **THEN** custom-role permission helpers SHALL live under `src/js/custom/*`
- **AND** React components outside `src/js/custom/*` SHALL consume those helpers or the canonical permission payload instead of repeating custom-role checks inline

#### Scenario: Direct role-name references are limited to policy plumbing
- **WHEN** a developer searches for custom role identifiers
- **THEN** direct custom role-name references SHALL be limited to the canonical policy service, role constants, role seeding or migrations, tests, and compatibility plumbing whose explicit purpose is role membership exposure
- **AND** custom-role-sensitive business decisions outside those locations SHALL use named capability checks

#### Scenario: Custom policy presence is checked generically
- **WHEN** code needs to decide whether the active user has any custom-role policy
- **THEN** it SHALL use a canonical policy-presence method such as `CustomRolePolicyService.hasAnyCustomPolicy`
- **AND** it SHALL NOT enumerate individual custom policy flags in the caller

### Requirement: Custom role decisions SHALL use one canonical policy model
The system SHALL evaluate custom-role route access, menu visibility, page action visibility, GSP action visibility, API access, app-context permissions, and frontend action gates from one canonical custom-role policy model. The backend `CustomRolePolicyService` SHALL be the source of truth for capability decisions, and frontend custom-role helpers SHALL consume backend-provided permissions instead of deriving the access matrix independently. Frontend helpers SHALL only control UI visibility and SHALL NOT be treated as authorization enforcement.

#### Scenario: Backend callers receive consistent decisions
- **WHEN** the interceptor, menu service, taglibs, controllers, or app-context generation ask whether a custom-role user can perform an action
- **THEN** they SHALL receive decisions derived from the same backend custom-role policy service

#### Scenario: Frontend callers receive consistent decisions
- **WHEN** React screens decide whether to render custom-role-sensitive actions
- **THEN** they SHALL use backend-provided custom-role permissions through custom frontend helpers
- **AND** the visible action SHALL match the backend enforcement decision for the same workflow

#### Scenario: Named capabilities remain consistent across layers
- **WHEN** a named capability such as `canManageStocklists`, `canCreateInboundMovement`, `canManageProducts`, or `canUseSuperuserPurchasingActions` is exposed to more than one caller
- **THEN** backend enforcement, server-rendered visibility, and React visibility SHALL derive that capability from `CustomRolePolicyService`
- **AND** any frontend helper for the capability SHALL read the canonical permission payload rather than checking role names directly

#### Scenario: Frontend helpers do not duplicate authorization logic
- **WHEN** a frontend helper decides whether to show a custom-role-sensitive action
- **THEN** it SHALL use the backend-provided custom role permission payload or default visibility behavior
- **AND** it SHALL NOT independently reimplement the custom role access matrix from role names

#### Scenario: Backend still denies hidden restricted actions
- **WHEN** a custom-role user calls a restricted action directly despite the frontend hiding that action
- **THEN** backend enforcement SHALL deny the request according to `CustomRolePolicyService`
- **AND** the frontend helper SHALL NOT be the only authorization layer

#### Scenario: Standard core roles are unaffected without custom policy
- **WHEN** a user has only standard OpenBoxes roles and no active custom role policy
- **THEN** custom-role capability checks and frontend permission helpers SHALL preserve existing standard-role behavior
- **AND** the custom policy layer SHALL NOT remove access granted by the standard OpenBoxes authorization model
- **AND** custom-only manage capabilities SHALL NOT grant product or stocklist write UI to standard users who lack the existing core-role access

#### Scenario: App context exposes canonical custom permissions
- **WHEN** the frontend requests app context for a user with or without an active custom role policy
- **THEN** the response SHALL include the canonical `customRolePermissions` payload
- **AND** the response SHALL NOT require consumers to combine separate per-role policy flags with the canonical payload to determine custom-role behavior

#### Scenario: Menu authorization delegates policy exceptions
- **WHEN** megamenu construction evaluates minimum roles, supplemental roles, or custom policy exceptions such as RPC Superuser menu access
- **THEN** the decision SHALL be delegated to a reusable service method derived from the canonical custom-role policy model
- **AND** `MegamenuService` SHALL remain responsible for menu construction and translation rather than embedding custom-role matrices

### Requirement: Custom role behavior SHALL have regression coverage
The system SHALL include automated regression tests for custom-role policy resolution, menu visibility, page action visibility, direct controller/API enforcement, location chooser behavior, and frontend permission helpers.

#### Scenario: Policy matrix is covered by fast tests
- **WHEN** backend unit tests run for the custom-role policy service
- **THEN** they SHALL verify the allowed and denied module/action decisions for Facility Storekeeper, Regional Warehouse User, RPC Superuser, and Reporting User

#### Scenario: Direct access is covered by controller or integration tests
- **WHEN** backend controller or integration tests run for restricted custom-role workflows
- **THEN** they SHALL verify direct URL/API requests are denied even when the matching UI action is hidden

#### Scenario: Frontend gates are covered by Jest tests
- **WHEN** frontend tests run for custom-role permission helpers and representative action components
- **THEN** they SHALL verify that restricted actions are hidden and allowed actions remain visible for the relevant custom-role permission payloads
