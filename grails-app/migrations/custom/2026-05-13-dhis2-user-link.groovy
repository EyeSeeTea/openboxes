databaseChangeLog = {
    changeSet(author: "est-fork", id: "custom-0001-dhis2-user-link") {
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
            column(name: "dhis2_uid", type: "VARCHAR(11)") {
                constraints(nullable: false, unique: true)
            }
            column(name: "dhis2_username", type: "VARCHAR(255)")
            column(name: "last_login_at", type: "TIMESTAMP")
            column(name: "created_at", type: "TIMESTAMP") {
                constraints(nullable: false)
            }
            column(name: "updated_at", type: "TIMESTAMP") {
                constraints(nullable: false)
            }
        }
        rollback {
            dropTable(tableName: "custom_dhis2_user_link")
        }
    }
}
