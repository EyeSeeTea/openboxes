databaseChangeLog = {
    changeSet(author: "eyeseetea", id: "2026-05-13-01-dhis2-user-link") {
        preConditions(onFail: "MARK_RAN") {
            not { tableExists(tableName: "custom_dhis2_user_link") }
        }
        createTable(tableName: "custom_dhis2_user_link") {
            column(name: "id", type: "CHAR(38)") {
                constraints(primaryKey: true, nullable: false)
            }
            column(name: "version", type: "BIGINT", defaultValueNumeric: "0") {
                constraints(nullable: false)
            }
            column(name: "user_id", type: "CHAR(38)") {
                constraints(nullable: false, unique: true,
                    foreignKeyName: "fk_custom_dhis2_user_link_user", references: "user(id)")
            }
            // v42 SSO keys the link on the DHIS2 username (the UID is not reachable from a SAS
            // token), so dhis2_uid is nullable; v40/v41 links still carry the 11-char UID.
            column(name: "dhis2_uid", type: "VARCHAR(11)") {
                constraints(nullable: true, unique: true)
            }
            // The username is the v42 identity key: required, unique, and exact-byte collated below.
            column(name: "dhis2_username", type: "VARCHAR(255)") {
                constraints(nullable: false)
            }
            column(name: "last_login_at", type: "TIMESTAMP")
            // Tombstone: a recycled username forces re-approval instead of silently reusing the account.
            column(name: "deactivated_at", type: "TIMESTAMP")
            column(name: "created_at", type: "TIMESTAMP") {
                constraints(nullable: false)
            }
            column(name: "updated_at", type: "TIMESTAMP") {
                constraints(nullable: false)
            }
        }

        // Reason: DHIS2 usernames are case-sensitive; a case-insensitive collation could merge or
        // split distinct accounts. utf8mb4_bin is exact-byte on MySQL/MariaDB (no-op on H2 tests).
        sql(dbms: "mysql,mariadb",
            "ALTER TABLE custom_dhis2_user_link " +
            "MODIFY dhis2_username VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL")

        addUniqueConstraint(
            tableName: "custom_dhis2_user_link",
            columnNames: "dhis2_username",
            constraintName: "uc_custom_dhis2_user_link_username")

        rollback {
            dropTable(tableName: "custom_dhis2_user_link")
        }
    }
}
