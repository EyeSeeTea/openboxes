## ADDED Requirements

### Requirement: Custom role policy SHALL be isolated behind custom services and helpers
The system SHALL keep custom-role authorization rules in custom backend services under `org.pih.warehouse.custom.*` and custom frontend helpers under `src/js/custom/*`. Upstream files SHALL only contain narrow delegation calls when required to connect existing OpenBoxes extension points to the custom policy.

#### Scenario: Custom backend policy is discoverable
- **WHEN** a developer reviews backend custom-role authorization behavior
- **THEN** the role matrix, endpoint restrictions, and menu/action capability decisions SHALL be implemented under `org.pih.warehouse.custom.*`
- **AND** upstream backend files SHALL NOT contain duplicated custom-role matrices or role-specific endpoint lists

#### Scenario: Custom frontend policy is discoverable
- **WHEN** a developer reviews frontend custom-role action visibility
- **THEN** custom-role permission helpers SHALL live under `src/js/custom/*`
- **AND** React components outside `src/js/custom/*` SHALL consume those helpers or the canonical permission payload instead of repeating custom-role checks inline

### Requirement: Custom role decisions SHALL use one canonical policy model
The system SHALL evaluate custom-role route access, menu visibility, page action visibility, GSP action visibility, API access, and frontend action gates from one canonical custom-role policy model.

#### Scenario: Backend callers receive consistent decisions
- **WHEN** the interceptor, menu service, taglibs, controllers, or app-context generation ask whether a custom-role user can perform an action
- **THEN** they SHALL receive decisions derived from the same backend custom-role policy service

#### Scenario: Frontend callers receive consistent decisions
- **WHEN** React screens decide whether to render custom-role-sensitive actions
- **THEN** they SHALL use backend-provided custom-role permissions through custom frontend helpers
- **AND** the visible action SHALL match the backend enforcement decision for the same workflow

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
