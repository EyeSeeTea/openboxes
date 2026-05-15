---
paths:
  - "grails-app/domain/**"
  - "grails-app/migrations/**"
  - "src/main/groovy/**"
---

# Upstream Entity Extension — Side Tables, Never Column Injection

> **The rule:** when a custom feature needs to add data to an upstream domain entity (`Shipment`, `Container`, `Product`, `Organization`, `Person`, `Location`, `RequisitionItem`, `ShipmentItem`, etc.), add the data in a **new custom domain class with a UNIQUE FK to the upstream entity** — a 1-to-1 (or 1-to-many) side table. **Never** add columns to upstream tables and never extend upstream domain classes via GORM/metaclass/trait injection.

## Why

The fork isolation contract (see `rules/custom-package-isolation.md`) is about keeping upstream **files** untouched so merges stay clean. This rule extends the same contract to upstream **schemas**:

- **Upstream table schema collisions.** If we add a `seal_number` column to the upstream `container` table and upstream later adds the same column (with different semantics), merge resolution is painful and potentially data-destructive.
- **Runtime class extension is fragile.** GORM metaclass tricks, AST transforms, or trait-based property additions surprise anyone reading the upstream domain class — the class in the source file does not match the runtime shape. Debugging gets harder.
- **Side-tables scale cleanly.** Collections (e.g. multiple seals per container) can't be injected onto upstream classes without adding new FK tables anyway — at which point you've built a side-table with worse ergonomics.
- **Nulls on unused rows.** An injected scalar column forces every upstream row to carry it, even rows the feature never touches. A side table only has rows for entities the feature actually extends.

## The pattern

```groovy
// grails-app/domain/org/pih/warehouse/custom/<feature>/CustomContainer.groovy
package org.pih.warehouse.custom.shipmentPackaging

import org.pih.warehouse.shipping.Container  // upstream — read-only reference

class CustomContainer {
    Container container              // UNIQUE FK to upstream — the side-table "anchor"
    Boolean isShipperOwned = false   // feature-specific scalar
    static hasMany = [
        seals: CustomContainerSeal,
        customsReferences: CustomContainerCustomsReference,
    ]
    static constraints = {
        container unique: true       // 1-to-1 enforcement
    }
    static mapping = {
        id generator: 'uuid'
        table 'custom_container'
    }
}
```

Traversal from a line:

```groovy
// Find the CustomContainer for a ShipmentItem's container (one extra query)
CustomContainer cc = shipmentItem.container ? CustomContainer.findByContainer(shipmentItem.container) : null
```

Or, if you want native GORM navigation, add a **one-direction** `hasOne` back-reference *on the custom side only* — do **not** add a property to upstream `Container`:

```groovy
class CustomContainer {
    static belongsTo = [container: Container]  // still cascades lifecycle correctly
    // ...
}
```

Still zero edits to upstream `Container.groovy`.

## Explicitly forbidden

| Anti-pattern | Why it fails the rule |
|---|---|
| `Container.metaClass.sealNumber = null` and friends | Runtime metaclass mutation — fragile, invisible in source. |
| Adding a Liquibase migration that does `addColumn(tableName: 'container', ...)` | Modifies the upstream schema directly. Merge risk if upstream adds a same-named column later. |
| Groovy trait applied to upstream domain class via AST | Changes the upstream class's bytecode shape at runtime. |
| Subclassing upstream domain class (`class CustomContainer extends Container`) | Creates an STI/table-per-hierarchy coupling that changes the upstream table's semantics. |
| Adding new values to upstream enums (`RequisitionStatus`, `ShipmentStatusCode`, `LotStatusCode`, etc.) | Every consumer of the enum (frontend filters, status badges, switch statements, reports) must be aware of the new value. Upstream merges where they add their own values to the same enum cause guaranteed conflicts. **Express new state in a custom side-table** (e.g. `custom_approval_decision.status` enum), and have the chain *terminate* in an existing upstream status — never widen the upstream domain. |
| Adding new values to `RoleType` enum | Same reason. Acceptable *only* when the value is genuinely needed for `findUsersByRoleTypes` recipient lookups (e.g. `ROLE_*_NOTIFICATION`); document the touch point in the OpenSpec `design.md` and accept the merge cost. Default answer: reuse an existing `Role`. |
| Adding new values to `ActivityCode` for feature toggles | Same reason. Prefer external config (`openboxes-config.properties` keys) for boolean feature flags. Add to `ActivityCode` *only* when the toggle is genuinely per-location and the existing per-location infrastructure (admin UI, `Location.supports()`, location-type seeding) buys you something material. |

## When the rule does NOT apply

- **Seed data** for upstream lookup tables (`ContainerType`, `LocationType`, `DocumentType`, `ReferenceNumberType`, etc.). Adding rows is not schema change. Use a Liquibase changeset under `grails-app/migrations/custom/` and make it idempotent (preconditions).
- **Config / feature flags** — belong in external config, not in domain extensions.
- **Transient computed properties** you need in a service layer — write a helper service or Groovy category; don't persist anything.

## Related rules

- `rules/custom-package-isolation.md` — upstream **files** stay pristine; this rule is the schema-level analogue.
- `rules/groovy/patterns.md` — GORM constraints/mappings/query conventions that apply to the new side-table classes themselves.
- `rules/ims-custom-domain.md` — IMS-specific side tables (`CustomContainer`, `CustomRequisitionItem`, `CustomShipmentItem`, `CustomShipmentMilestone`, `CustomOrganizationContact`, `Sli`, `DistributionPlan`) all follow this pattern.

## Quick self-check before writing a migration

If your custom Liquibase changeset contains `addColumn(tableName: '<upstream-table>', ...)`, stop. Replace it with `createTable(tableName: 'custom_<feature>', ...)` that has a `FOREIGN KEY ... REFERENCES <upstream-table>(id)` and a `UNIQUE` constraint on that FK column (for 1-to-1) or an index (for 1-to-many).

## Quick self-check before naming a new domain concept

Before introducing a new field, table column, or `Location.<something>` semantic, **grep upstream for the term you're about to use** and confirm it isn't already taken with different semantics. Real cases that have bitten this fork:

- `Location.parentLocation` already exists upstream — but it expresses **physical containment** (bin → zone → warehouse), not **organizational hierarchy** (district → region → national). A custom feature that wanted "parent" for org hierarchy needed a separate side-table (`custom_location_admin_hierarchy.admin_parent_id`) to avoid colliding with upstream's `LocationService.findInternalLocation`, `getZones`, `getBinLocations`, and the picklist resolver, all of which read `parentLocation` as bin/zone.
- `LotStatusCode` includes `ON_HOLD`, `QUARANTINED`, `EXPIRED`, etc. — **only `RECALLED` is referenced anywhere in the codebase**; the others are dead enum values. The actual "hold" mechanism is `ActivityCode.HOLD_STOCK` on a bin `Location`, not the lot status. A custom feature reaching for `LotStatusCode.ON_HOLD` would build on dead code.
- `requisition.approvers` is a `Person` collection, not `User`. Custom approval logic that resolves users by role and compares against the approvers list needs a `User → Person` hop.

**The check is simple: `grep -rn '<term>' grails-app/ src/main/`** before committing to a name. If the term shows up with surprising semantics, pick a different name (or build a side-table that owns the new semantic cleanly).
