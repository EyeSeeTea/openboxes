## Why

Manual review of the custom roles PR found duplicated policy checks, inconsistent treatment of `ROLE_REPORTING_USER`, and unclear role hierarchy/location chooser behavior. Addressing these now reduces authorization drift and makes the custom-role implementation safer to maintain across upstream merges.

## What Changes

- Centralize custom-policy presence checks instead of enumerating individual custom roles in controllers and taglibs.
- Remove redundant per-role policy flags from the app-context payload when the canonical `customRolePermissions` payload already conveys active policy and capabilities.
- Clarify and test location chooser behavior for default custom roles versus location-scoped custom roles.
- Move menu role helper logic out of `MegamenuService` into the canonical custom-role policy layer or a reusable service method.
- Document and test the intended relationship between custom roles, `ROLE_ASSISTANT`, and role priority `4`.
- Preserve backend enforcement as the source of truth and keep frontend permissions as UI visibility helpers only.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `custom-role-policy-isolation`: Tighten the requirement that custom-role-sensitive decisions use canonical policy/capability methods rather than repeated direct role-specific flags.
- `custom-roles-access`: Clarify custom-role hierarchy, higher core role precedence, and location chooser behavior for default and location-scoped custom roles.

## Impact

- Backend controllers: `ApiController`, `DashboardController`, and `LocationApiController` custom-role checks and app-context payload.
- Backend services: `CustomRolePolicyService`, `MegamenuService`, `LocationService`, and possibly `UserService` helper methods.
- Backend taglibs: `AuthTagLib` role policy checks.
- Role model: comments/tests around `RoleType` priority and expansion behavior.
- Tests: backend unit tests for policy flags, menu helpers, location chooser behavior, role priority/expansion, and higher core role precedence.
