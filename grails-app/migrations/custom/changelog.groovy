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
    include file: 'custom/0001-dhis2-user-link.groovy'
}
