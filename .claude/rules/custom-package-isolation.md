---
paths:
  - "grails-app/**"
  - "src/main/**"
  - "src/main/groovy/**"
  - "src/main/java/**"
  - "src/main/webapp/**"
  - "src/test/**"
  - "src/integration-test/**"
  - "src/js/**"
  - "test/**"
---

# Custom Package Isolation (CRITICAL)

> This is the **single most important rule** in this fork. Every new custom line of code MUST live under a `custom/` subfolder or `org.pih.warehouse.custom.*` package. Failure to do this creates merge conflicts forever when we pull upstream updates, because any file we edited becomes a conflict point when upstream also edits it.

## The Rule

**All new code goes under a `custom` path.** Period.

| Layer | Where new files go |
|---|---|
| Grails domain classes | `grails-app/domain/org/pih/warehouse/custom/<feature>/` |
| Grails services | `grails-app/services/org/pih/warehouse/custom/<feature>/` |
| Grails controllers | `grails-app/controllers/org/pih/warehouse/custom/<feature>/` |
| Grails views (GSP) | `grails-app/views/custom/<feature>/` |
| Grails taglibs | `grails-app/taglib/org/pih/warehouse/custom/<feature>/` |
| Grails interceptors | `grails-app/controllers/org/pih/warehouse/custom/<feature>/` |
| Grails commands / jobs | `grails-app/jobs/org/pih/warehouse/custom/<feature>/` |
| Liquibase migrations | `grails-app/migrations/custom/` (filename prefixed with date + feature) |
| i18n messages | **Append to the upstream root `grails-app/i18n/messages.properties`** under a `# <feature> (custom)` comment block. This is the one exception to the custom-folder rule — see "i18n exception" below. |
| Java helpers | `src/main/java/org/pih/warehouse/custom/<feature>/` |
| Groovy helpers | `src/main/groovy/org/pih/warehouse/custom/<feature>/` |
| React components | `src/js/custom/<feature>/components/` |
| React hooks | `src/js/custom/<feature>/hooks/` |
| React utils | `src/js/custom/<feature>/utils/` |
| Redux actions / reducers / selectors | `src/js/custom/<feature>/redux/` |
| Frontend tests | `src/js/custom/<feature>/__tests__/` |
| Backend unit tests | `src/test/groovy/org/pih/warehouse/custom/<feature>/` |
| Backend integration tests | `src/integration-test/groovy/org/pih/warehouse/custom/<feature>/` |

**`<feature>` must be a camelCase name** describing the feature: `stockTransferDocuments`, `seasonCartonCascade`, `imsImport`, etc. Do **not** use kebab-case (`gs1-barcode`) or flat lowercase (`stocktransferdocuments`) — camelCase is the enforced convention for all custom package and folder names across both backend and frontend.

## Why

OpenBoxes is a **fork** of upstream PIH OpenBoxes. We pull upstream updates periodically — every merge replays upstream commits onto our EST layer, and onto every customer branch on top of EST. The number of merge conflicts is proportional to the number of upstream files we've touched. If every custom line lives in a `custom/` folder that upstream never touches, we get **zero conflicts** on those files.

**The corollary:** the Boy Scout Rule from the global CLAUDE.md is **suspended** for any file outside the custom folders. Do not reformat, reorder imports, rename symbols, or "clean up" upstream files. Every incidental edit is a future merge conflict for zero functional benefit.

**Schema-level analogue:** this rule is about upstream *files*. The schema version — don't inject columns onto upstream tables or extend upstream domain classes at runtime — lives in `upstream-entity-extension.md`. When you need to add data to an existing upstream entity, create a custom side-table with a UNIQUE FK rather than modifying the upstream schema.

## When You MUST Touch an Upstream File

Sometimes a feature genuinely requires modifying an existing upstream file (adding a menu link, registering a new controller, hooking into an existing wizard step). The rules are:

1. **Keep the edit surgical and localized.** Change only the lines the feature requires. No incidental cleanup, no import reordering, no rename-while-you're-there.
2. **Prefer extension points** — Grails service injection, event listeners, taglib additions, React component composition via props/children — over rewriting the upstream logic.
3. **Document the touch point.** Every OpenSpec change (`openspec/changes/<change>/design.md`) MUST include an "Upstream touch points" section listing the upstream files modified and the one-line reason for each. This is our merge-conflict hitlist for future upstream pulls.
4. **Prefer a new file + a one-line include** over editing a big file. Example: instead of inlining a new section into `grails-app/views/dashboard/index.gsp`, create `grails-app/views/custom/<feature>/_section.gsp` and add a single `<g:render template="/custom/<feature>/section"/>` line to `index.gsp`.

## When You're Creating a New File

**Before you write the first line, check the destination path.** If the path does not contain `custom/` or `org.pih.warehouse.custom.*`, STOP and re-route.

### Backend example

```
❌ grails-app/services/org/pih/warehouse/inventory/IMSImportService.groovy
✅ grails-app/services/org/pih/warehouse/custom/imsImport/IMSImportService.groovy
```

### Frontend example

```
❌ src/js/components/imsImport/ImsImportPage.jsx
✅ src/js/custom/imsImport/components/ImsImportPage.jsx
```

### Migration example

```
❌ grails-app/migrations/0.9.x/add-ims-fields.groovy
✅ grails-app/migrations/custom/2026-04-13-add-ims-fields.groovy
```

## Wiring It Up

### Liquibase

**The wiring is already in place on EST** — `grails-app/migrations/changelog.groovy` already contains the include line (after the release loop, before the views rebuild), and `grails-app/migrations/custom/changelog.groovy` exists as the aggregator. **You should not edit the upstream master changelog at all.** New custom migrations only need to:

1. Drop a file under `grails-app/migrations/custom/<yyyy-mm-dd>-<feature>.groovy`.
2. Append a one-line `include file: '<yyyy-mm-dd>-<feature>.groovy'` to `grails-app/migrations/custom/changelog.groovy`.

For reference, the master changelog looks like this — **don't change it**:

```groovy
// grails-app/migrations/changelog.groovy  (upstream — pre-wired, do not edit)
databaseChangeLog = {
    // ... upstream includes unchanged ...
    for (TaggedMigrationVersion release : currentAndNewerReleases) {
        include(file: release.toString() + "/changelog.xml")
    }

    include file: 'custom/changelog.groovy'   // <-- already here

    include(file: 'views/changelog.xml')
}
```

**Why placement matters (already correct on EST):** the include sits **after the upstream release loop** and **before the views rebuild** so upstream migrations apply first, ours second, and view rebuilds last (so any custom views aren't dropped by the rebuild step).

The aggregator at `grails-app/migrations/custom/changelog.groovy` is **ours** — appending include lines to it is **never** an upstream touch:

```groovy
databaseChangeLog = {
    include file: '2026-04-13-add-ims-fields.groovy'
    include file: '2026-04-15-ims-indexes.groovy'
}
```

**Order changesets by FK dependency.** When one custom feature's tables reference another custom feature's tables (e.g. an approval-tier FK into a location-level table), the include order in `custom/changelog.groovy` must put the FK-target file **above** the FK-holder file. Date-prefixed filenames usually sort correctly; if same-day shipments collide, reorder the include lines explicitly. Rollback runs in reverse-application order automatically — get the apply order right and rollback follows.

**Every changeset MUST have an explicit `rollback {}` block.** Don't rely on Liquibase's auto-derive (it doesn't always work for tables with FKs). For `createTable` use `rollback { dropTable(tableName: '...') }`; for `insert` use `rollback { delete(tableName: '...', where: "...") }`. Drop FK-holding tables before FK-target tables — the per-changeset rollback runs in reverse-application order, so getting the apply order right gives you the correct rollback order for free.

**Liquibase id collisions are protected by namespace, not by id uniqueness.** Liquibase tracks changesets by `(id, author, filename)`. Use `author = 'eyeseetea'` and put files under `custom/` so even if upstream coincidentally ships an id matching one of ours, the tuple differs and the rows are independent in `databasechangelog`. Date-prefix changeset ids (`2026-04-13-01-...`) for human readability — collisions inside our own folder are the only ones we have to avoid manually.

**Changeset id + author convention (REQUIRED).** Every custom changeset uses:

```groovy
changeSet(author: "eyeseetea", id: "<yyyy-MM-dd>-<NN>-<short-desc>") {  // e.g. 2026-05-13-01-dhis2-user-link
```

- **`id`: `<yyyy-MM-dd>-<NN>-<short-desc>`** — the date the changeset is written, a two-digit sequence (`01`, `02`, …) for multiple changesets the same day, then a kebab-case description. Matches the filename's date prefix.
- **`author`: always `eyeseetea`** — uniform across the fork so the `(id, author, filename)` tuple stays predictable.
- **Do NOT use a global sequential counter** (`custom-0001`, `custom-0002`, …). It needs cross-branch coordination — two parallel feature branches both grab the next number and clash. The date prefix is self-coordinating and collision-proof; a global counter is not.

This is a maintainability/coordination rule, not a correctness one — Liquibase runs any unique tuple regardless. Standardize **going forward**; don't retrofit ids on already-merged changesets (changing a merged tuple orphans its `databasechangelog` row).

### Spring beans

If a custom class needs to be registered as a Spring bean (rare; Grails auto-scans `grails-app/` subfolders), add it in `grails-app/conf/spring/resources.groovy`. That file is edited rarely by upstream, so diffs there have low conflict risk.

### React routes

If you're adding a new React route, the route table is in `src/js/routes/` (or wherever the project has it). That's an upstream file — the edit is a single `<Route>` line, which is acceptable and should be noted in the design.md touch points.

### i18n exception — keys go in the upstream root bundle, not under `i18n/custom/`

**Custom i18n keys MUST be appended to the upstream `grails-app/i18n/messages.properties`**, not split into a sibling file under `grails-app/i18n/custom/`. This is the only place where the custom-folder pattern doesn't apply — and it's a runtime constraint, not a stylistic choice.

**Why.** Grails 3.3 wires a single `messageSource` bean (`PluginAwareResourceBundleMessageSource`) whose default basename glob is `WEB-INF/grails-app/i18n/messages*.properties` at the root only. Files under `grails-app/i18n/custom/` are **never loaded at runtime**, so every `messageSource.getMessage(key, args, defaultMessage, locale)` call silently falls back to the hardcoded `defaultMessage` parameter. That defeats translation entirely — Crowdin would happily produce localised files no one ever reads. The `<Translate id=... defaultMessage=... />` frontend wrapper has the same issue: in production the redux-localize store is hydrated from the same root bundle, so a key that lives only under `i18n/custom/` will only ever render the English default.

**The pattern.**

```properties
# grails-app/i18n/messages.properties (upstream — append at the bottom)
# ... upstream keys ...

# <feature> (custom)
<feature>.expired.cannotShip=Cannot pick lot {1} of product {0} — it expired on {2}.
<feature>.expired.tooltip=Cannot ship — expired on {0}.
```

A `# <feature> (custom)` comment block at the bottom of `messages.properties` keeps the custom keys visually grouped, makes them trivial to grep (`grep '^outboundExpiryRestrictions\.' grails-app/i18n/messages.properties`), and keeps the upstream merge surface to a single contiguous block at end-of-file rather than scattered insertions.

**Document the touch.** Because `messages.properties` is upstream, every change that adds keys MUST list it under "Upstream touch points" in the OpenSpec change's `design.md`, with a one-line reason ("Custom i18n keys for `<feature>` — must live in the root bundle because Grails 3.3's messageSource only globs `messages*.properties` at the root"). This is exactly the merge-conflict hitlist the rule is designed to produce — accept the conflict cost in exchange for keys that actually load.

**Do not.**

- ❌ Create `grails-app/i18n/custom/<feature>-messages.properties` and call `messageSource.getMessage(...)` against it — it won't load.
- ❌ Override the `messageSource` bean in `resources.groovy` to add a custom basename. Possible, but invasive (changes the bean Grails wires automatically), and one bean override carries more upstream-merge risk than three appended lines in `messages.properties`. Not worth it.
- ❌ Hardcode English defaults in JSX as the "real" string and rely on `<Translate defaultMessage=...>`. The default is a fallback for missing keys — not a substitute for translation.
- ❌ Add the keys to `grails-app/i18n/messages_<locale>.properties` directly. Crowdin owns those files; only the root `messages.properties` is the source of truth.

**Crowdin.** The existing top entry of `crowdin.yml` (`source: /**/grails-app/i18n/messages.properties` → `translation: /**/grails-app/i18n/messages_%two_letters_code%.properties`) already covers any keys appended to the root bundle. **Do not add a second glob** for `i18n/custom/*-messages.properties` — there is nothing for it to match, and orphan globs are dead config that misleads the next person.

## Boy Scout Rule

The team-wide Boy Scout Rule from `~/.claude/est/CLAUDE.md` is **suspended inside any upstream file** (anything not under `custom/` or `org.pih.warehouse.custom.*`). Apply cleanups ONLY inside the custom folders.

## Enforcement in Code Review

The `code-review`, `java-reviewer`, and `react-reviewer` agents all check this rule. Any new file outside the custom folders is flagged as **CRITICAL** — a blocker, not a warning.

## Starting a New Feature

Start every non-trivial custom feature with `/opsx:propose` — it generates the OpenSpec change folder (`openspec/changes/<change>/`) with `proposal.md`, `design.md`, `tasks.md`, and the spec files. The `design.md` template must include an "Upstream touch points" section if the work modifies any upstream file.

When you create the first real backend or frontend file for the feature, place it under the matching `custom/` path from the table above. There is no directory scaffolder — this rule file plus the reviewer agents are the enforcement.

## FAQ

**Q: What if the feature is tiny — one controller and one GSP? Do I still need the whole structure?**
A: Yes. The path `grails-app/controllers/org/pih/warehouse/custom/<feature>/FooController.groovy` is not overhead — it's the difference between "upstream merge just works" and "upstream merge has conflicts".

**Q: The existing OpenBoxes code doesn't follow this structure. Can I put my new file next to the existing code for consistency?**
A: No. The existing code is upstream-pristine; our custom additions live in a parallel `custom/` structure. Consistency with upstream means **leaving upstream alone**, not adopting its layout for new files.

**Q: What about tests for upstream code I had to edit?**
A: Write the tests under `src/test/groovy/org/pih/warehouse/custom/<feature>/` even if the code under test is upstream. The test file is yours, so it lives in your tree.

**Q: What if two customers share a custom feature?**
A: Then it belongs on the EST shared layer (`release/est/<version>`), not the per-customer branch. The file layout is the same — `org.pih.warehouse.custom.<feature>/` — it's just pushed down the stack one level.
