## Context

OpenBoxes authorization is built around fixed `RoleType` values, seeded `Role` rows, location-scoped user roles, a core role hierarchy, runtime RBAC rules, controller/interceptor checks, menu filtering, and page-level permission checks. It does not provide a database-driven permission matrix that can create a new operational role by configuration alone.

This change retroactively documents the implemented branch that adds four customer-specific operational roles: Facility Storekeeper, Regional Warehouse User, RPC Superuser, and Reporting User. The roles need different access than the upstream hierarchy provides, so the implementation extends the fixed role model and applies explicit policies at the existing enforcement points.

## Goals / Non-Goals

**Goals:**
- Add fixed custom role types and seed assignable role records.
- Enforce the provided access matrix across backend guards and frontend/server-rendered UI.
- Keep existing OpenBoxes roles and higher-role behavior unchanged.
- Preserve current architecture and avoid broad upstream refactors.
- Make direct URL/API access match menu and page-action visibility.

**Non-Goals:**
- Build a generic permission framework.
- Make permissions admin-configurable per controller/action.
- Replace `RoleInterceptor` or the existing RBAC rule configuration.
- Convert every OpenBoxes screen to a new authorization abstraction.
- Resolve unrelated authorization debt outside the modules named in the role matrix.

## Decisions

1. Represent custom roles as fixed `RoleType` values.

   Rationale: OpenBoxes authorization already keys behavior off `RoleType`. Adding enum values, frontend constants, i18n labels, and seeded `Role` rows gives admins assignable roles without introducing a second authorization model.

   Alternative considered: create new `Role` database rows using existing role types. This was rejected because a new row with `ROLE_MANAGER` or `ROLE_BROWSER` still authorizes like that existing role type and cannot express manager-minus-purchasing or read-only-reporting behavior.

2. Enforce custom role policies through explicit predicates and allow/deny lists.

   Rationale: The matrix cuts across module boundaries and does not map cleanly to the existing hierarchy. Explicit predicates in user/session authorization code keep the policy readable and avoid accidental access through broad manager-style checks.

   Alternative considered: fold the roles into the core hierarchy. This was rejected because the custom roles are not strictly higher or lower than existing roles; each is a distinct operational mix.

3. Apply policy at both presentation and backend layers.

   Rationale: Menu and button hiding is necessary for usability but insufficient for security. Controller/API/interceptor guards must deny prohibited direct requests even when the UI does not expose the action.

   Alternative considered: only hide menu entries and React/GSP actions. This was rejected because users could still reach controllers and APIs directly.

4. Preserve higher-role behavior when mixed with custom roles.

   Rationale: Existing OpenBoxes authorization expects higher core roles to satisfy lower requirements. When a user has Assistant, Manager, Admin, or Superuser access in the effective current-location roles, custom role restrictions should not unexpectedly reduce access.

   Alternative considered: make custom restrictions always subtractive. This was rejected because it would create surprising regressions for users who carry a custom role plus a higher operational/admin role.

5. Keep the change localized to known authorization surfaces.

   Rationale: This is a customer-specific branch change. Touching upstream files is unavoidable because the current authorization architecture is not extension-only, but the implementation should stay limited to role definitions, migrations, user/session helpers, menu/action visibility, and selected controller/API guards.

   Alternative considered: introduce a new custom authorization service wrapping all OpenBoxes checks. This was rejected as too large for the immediate requirement and risky for upstream maintenance.

## Risks / Trade-offs

- Custom roles assigned globally can broaden access across locations -> Administrators should prefer location-scoped assignment for operational roles, and future hardening can restrict these roles to location assignment if required.
- Legacy `JsonController` action allow-lists are fragile -> Tests should cover stocklist JSON actions that custom roles need and deny unrelated JSON actions.
- Product/configuration write scope for RPC Superuser is broad -> Product owners should validate whether all exposed product/configuration write actions are intended.
- Authorization is distributed across several layers -> Regression coverage must include backend direct access and frontend/server-rendered visibility, not only menu checks.
- Upstream files are touched -> Keep edits minimal and avoid incidental formatting so future upstream replays remain manageable.

## Migration Plan

1. Deploy code containing the new `RoleType` values, frontend constants, authorization predicates, menu/action filters, and controller/API guards.
2. Run custom Liquibase migrations to insert the four `Role` records.
3. Assign the new roles to users, preferably as location roles for operational access.
4. Verify each role against the Dashboard, Inventory, Purchasing, Inbound, Outbound, Reporting, Products, and Stocklists matrix.

Rollback strategy:
- Remove or stop assigning the new custom roles to users before rolling back code.
- Roll back the application to the prior branch revision.
- If necessary, mark or remove the seeded custom `Role` rows after confirming no users still reference them.

## Open Questions

- Should custom operational roles be prevented from default/global assignment and allowed only as location roles?
- Should stocklist item mutation endpoints be moved away from broad legacy JSON controller actions?
- Should RPC Superuser have write access to every product/configuration resource exposed by the Products menu, or should specific product configuration areas be read-only?
