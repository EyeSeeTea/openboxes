## 1. Audit Product Add Actions

- [x] 1.1 Search product-related GSP, controller, route, and React code for add/create/import/new controls and endpoints.
- [x] 1.2 Confirm which product pages are available to read-only Products users and list visible create affordances, including product components and product groups.
- [x] 1.3 Identify the existing permission helper, view model, taglib, or frontend state that best represents Products Read/Write access.

## 2. UI Implementation

- [x] 2.1 Hide product record add/create controls when the current user has read-only Products access.
- [x] 2.2 Hide product metadata add/create controls, including product component and product group actions, when the current user has read-only Products access.
- [x] 2.3 Verify users with Products Read/Write access still see existing product add/create controls.
- [x] 2.4 Keep read-only product navigation, list, and detail views available to read-only Products users.

## 3. Authorization Verification

- [x] 3.1 Verify direct product create/update/delete/import URLs and APIs remain denied for read-only Products users.
- [x] 3.2 Add or update backend authorization tests for representative direct product write requests if coverage is missing.

## 4. Regression Tests

- [x] 4.1 Add or update UI/view tests for Reporting User product pages to assert product add/create controls are hidden.
- [x] 4.2 Add or update UI/view tests for Regional Warehouse User product pages to assert product add/create controls are hidden.
- [x] 4.3 Add or update UI/view tests for a Products Read/Write role to assert product add/create controls remain visible.

## 5. Validation And Documentation

- [x] 5.1 Run the targeted backend and/or frontend tests covering the changed files.
- [x] 5.2 Record upstream touch points in this change's design document before archiving.
- [x] 5.3 Confirm no generated frontend bundles or unrelated upstream reformatting are included in the final diff.
