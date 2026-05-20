/**
 * Aggregator for custom Liquibase changesets in the EST fork.
 *
 * Wired into the master changelog.groovy via a single include line (after the
 * upstream release loop, before the views rebuild). New custom migrations:
 *
 *   1. Drop a file under grails-app/migrations/custom/<yyyy-mm-dd>-<feature>.groovy
 *      with a `databaseChangeLog = { changeSet(...) { ... rollback { ... } } }` block.
 *   2. Append a one-line `include file: '<yyyy-mm-dd>-<feature>.groovy'` below.
 *
 * Order include lines by FK dependency (target tables above holder tables).
 * See .claude/rules/custom-package-isolation.md for the full rules.
 */
databaseChangeLog = {
    include file: '2026-05-15-add-facility-storekeeper-role.groovy'
    include file: '2026-05-19-add-regional-warehouse-role.groovy'
}
