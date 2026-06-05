databaseChangeLog = {
    changeSet(author: "codex", id: "202605191200-0") {
        preConditions(onFail: "MARK_RAN") {
            sqlCheck(expectedResult: "0", "select count(*) from role where role_type = 'ROLE_REGIONAL_WAREHOUSE'")
        }

        insert(tableName: "role") {
            column(name: "id", value: "ROLE_REGIONAL_WAREHOUSE")
            column(name: "version", valueNumeric: "0")
            column(name: "description", value: "Role for regional warehouse users with inventory/inbound/outbound operations, no purchasing access, and no inbound movement creation")
            column(name: "role_type", value: "ROLE_REGIONAL_WAREHOUSE")
            column(name: "name", value: "Regional Warehouse User")
        }
    }
}
