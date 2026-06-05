## Why

Custom role behavior is currently being centralized, but direct role-name checks can still leak into controllers, GSPs, taglibs, and React components as new restrictions are added. This change keeps policy decisions discoverable and consistent by continuing the refactor toward reusable capability checks backed by `CustomRolePolicyService`.

## What Changes

- Expand custom-role policy isolation so backend callers ask `CustomRolePolicyService` for named capabilities instead of checking role names directly.
- Prefer reusable capability checks such as `canManageStocklists`, `canCreateInboundMovement`, and `canManageProducts` for UI visibility and backend enforcement.
- Keep backend policy evaluation as the source of truth and expose any frontend-sensitive decisions through canonical permission payloads or custom frontend helpers.
- Replace scattered role-name conditionals in touched controllers, GSPs, taglibs, and React components with delegation to the centralized policy model.
- Review duplicated permission logic between backend policy responses and frontend helpers so frontend checks remain focused on UI visibility, not authorization.
- Add regression coverage that proves representative capability checks match backend enforcement and UI visibility.
- Add regression coverage that standard core roles are unaffected when no custom role policy is active.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `custom-role-policy-isolation`: Strengthen the requirement that custom role callers use centralized, reusable capability checks instead of scattered direct role-name checks, and clarify that frontend helpers mirror backend permissions for visibility only.

## Impact

- Affected backend code: `CustomRolePolicyService`, custom role helpers, controller/interceptor touch points, taglibs, and GSP action visibility where custom role checks currently appear.
- Affected frontend code: custom role permission helpers and React components that render custom-role-sensitive actions.
- Tests: backend unit/integration coverage for capability decisions and unaffected standard core roles, plus frontend Jest coverage for representative permission helpers or action gates.
- No database migrations, external APIs, or dependency changes are expected.
