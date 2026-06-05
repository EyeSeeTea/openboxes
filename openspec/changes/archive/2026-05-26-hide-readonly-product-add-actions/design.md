## Context

Custom roles define module-level read and read/write access. Product access for roles such as `ROLE_REPORTING_USER` and `ROLE_REGIONAL_WAREHOUSE` is read-only, and backend authorization already denies direct product write actions. Some product-related pages still render add/create buttons, so users discover the restriction only after clicking and seeing an access denied alert.

The implementation must preserve upstream compatibility. Most affected UI is likely in existing upstream GSP or React product screens, so edits should be surgical and limited to permission checks around existing controls rather than broader rewrites.

## Goals / Non-Goals

**Goals:**

- Inventory product-related read pages where read-only product users can see add/create actions.
- Hide product add/create controls from users whose effective Products access is read-only.
- Keep backend/controller/API authorization unchanged or stricter so direct URL access remains denied.
- Add regression coverage for representative product-related add controls and direct write denial.

**Non-Goals:**

- Changing the role matrix or granting new product permissions.
- Replacing backend authorization with UI-only checks.
- Redesigning product pages or refactoring unrelated product UI.
- Adding new database tables, migrations, or external dependencies.

## Decisions

- Use the existing custom role access policy as the source for read-only versus read/write decisions.
  - Rationale: the same policy already controls menu/action/backend authorization, avoiding a second hard-coded role list in product screens.
  - Alternative considered: check explicitly for `ROLE_REPORTING_USER`; rejected because `ROLE_REGIONAL_WAREHOUSE` also has read-only product access and future custom roles may share the same access level.

- Guard only create/add UI affordances on product read pages, keeping read affordances visible.
  - Rationale: read-only product users should still browse product data, product groups, product components, and related product metadata.
  - Alternative considered: hide whole pages when any write action exists; rejected because it would reduce approved read access.

- Prefer small conditional checks at existing button render sites.
  - Rationale: these product screens are likely upstream-owned, and surgical edits minimize merge conflicts.
  - Alternative considered: introduce a new shared component or full product action registry; rejected unless implementation discovery shows repeated logic that can be isolated without broad upstream edits.

- Keep direct URL/API denial tests separate from UI visibility tests.
  - Rationale: hidden UI is a usability improvement, while backend authorization remains the security boundary.
  - Alternative considered: rely only on UI tests; rejected because direct access must remain protected.

## Risks / Trade-offs

- Missing one product-related page with a create button -> perform a targeted audit of product controllers/views/routes for `add`, `create`, `import`, and `new` actions before implementing.
- Existing permission helpers may not expose the needed product write flag to every GSP/React context -> extend the smallest existing view model or helper path rather than duplicating role checks in each page.
- Touching upstream views can increase future merge conflicts -> keep changes limited to conditional rendering around affected buttons and document upstream touch points when archiving.
- UI-hidden actions may still be callable through bookmarked URLs -> preserve and test backend denial for read-only product users.

## Upstream touch points

- `grails-app/taglib/org/pih/warehouse/AuthTagLib.groovy`: Added a `canManageProducts` tag so upstream GSP pages can hide product add/create affordances using existing custom-role policy state.
- `grails-app/views/productGroup/list.gsp`: Wrapped the Product Group create button with `g:canManageProducts`.
- `grails-app/views/productComponent/index.gsp`: Wrapped the Product Component create button with `g:canManageProducts`.
- `grails-app/views/product/_productGroups.gsp`: Wrapped the add-to-product-group form in `g:canManageProducts`.
- `grails-app/views/product/_productGroups.gsp`: Wrapped row-level Product Group edit and unlink actions in `g:canManageProducts`.
- `grails-app/views/productAssociation/list.gsp`: Wrapped Product Association add and import controls in `g:canManageProducts`.
- `grails-app/views/product/_productAssociations.gsp`: Wrapped row-level Product Association edit and delete actions, and create action, in `g:canManageProducts`.
- `grails-app/views/tag/_summary.gsp`: Wrapped Tag create control in `g:canManageProducts`.
- `grails-app/views/unitOfMeasureConversion/list.gsp`: Switched unit-of-measure conversion create control from admin-only to product-management permission gating.
- `grails-app/views/product/_uomDialog.gsp`: Wrapped UoM Class quick-add link and UoM create submit control in `g:canManageProducts`.
- `grails-app/views/product/_uomClassDialog.gsp`: Wrapped UoM Class create submit control in `g:canManageProducts`.
- `grails-app/views/product/_productPackages.gsp`: Wrapped Product Package create action and row-level edit/delete actions in `g:canManageProducts`.
- `src/js/components/products/ProductsListHeader.jsx`: Exported the component for focused UI visibility tests while keeping existing connected default export.
- `src/test/groovy/org/pih/warehouse/AuthTagLibCustomRolesSpec.groovy`: Added regression tests for `g:canManageProducts` rendering behavior.
- `src/test/groovy/org/pih/warehouse/custom/roles/CustomRolePolicyServiceSpec.groovy`: Added regression coverage that read-only product custom roles are denied direct product create route access.
- `src/js/tests/products/ProductsListHeader.test.jsx`: Added UI regression tests for Reporting User, Regional Warehouse User, and product write users.
