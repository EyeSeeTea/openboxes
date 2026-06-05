## ADDED Requirements

### Requirement: Product read-only roles SHALL NOT see product create affordances
The system SHALL hide product-related add/create UI controls from users whose effective Products access is read-only. This SHALL include server-rendered and React product screens for product records and related product metadata such as product components and product groups. Backend authorization SHALL continue to deny direct product write requests for those users.

#### Scenario: Reporting User opens product metadata pages
- **WHEN** a Reporting User opens product-related read pages, including product components and product groups
- **THEN** add/create controls for product records and related product metadata SHALL be hidden
- **AND** read-only navigation and data views SHALL remain available when the user is authorized for the current location

#### Scenario: Regional Warehouse User opens product metadata pages
- **WHEN** a Regional Warehouse User opens product-related read pages, including product components and product groups
- **THEN** add/create controls for product records and related product metadata SHALL be hidden
- **AND** read-only navigation and data views SHALL remain available when the user is authorized for the current location

#### Scenario: Product write user opens product metadata pages
- **WHEN** a user with Products Read/Write access opens product-related pages
- **THEN** add/create controls for product records and related product metadata SHALL remain visible according to existing authorization behavior

#### Scenario: Hidden product create action is requested directly
- **WHEN** a user whose effective Products access is read-only calls a product-related create endpoint directly by URL or API
- **THEN** the backend SHALL deny the request according to that role's policy
