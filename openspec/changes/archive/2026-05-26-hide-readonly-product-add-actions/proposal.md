## Why

Users with read-only access to the Products menu, such as `ROLE_REPORTING_USER`, can still see product-related "Add" actions on screens like product components and product groups. Clicking those actions results in an access denied alert, so hiding unavailable actions up front will reduce confusion and align the UI with the enforced permissions.

## What Changes

- Identify product-area pages where read-only product users can see create/add controls despite lacking write permission.
- Hide or disable product-related add/create controls for users whose Products menu access is read-only.
- Preserve server-side and API authorization as the source of truth; this change improves UI affordances only.
- Add regression coverage for representative product-related pages/actions so read-only roles do not see unavailable add controls.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `custom-roles-access`: Product-menu read-only access must not expose create/add UI actions for product-related resources.

## Impact

- Product-related GSP and/or React screens that render add/create controls, including product components and product groups.
- Existing permission/menu helpers or role-access UI checks used to determine whether a user can modify Products-area resources.
- Tests around custom role access, product page action visibility, and direct access denial behavior.
