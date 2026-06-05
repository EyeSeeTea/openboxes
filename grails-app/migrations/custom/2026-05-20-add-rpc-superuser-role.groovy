databaseChangeLog = {
    changeSet(author: "codex", id: "202605201200-0") {
        preConditions(onFail: "MARK_RAN") {
            sqlCheck(expectedResult: "0", "select count(*) from role where role_type = 'ROLE_RPC_SUPERUSER'")
        }

        insert(tableName: "role") {
            column(name: "id", value: "ROLE_RPC_SUPERUSER")
            column(name: "version", valueNumeric: "0")
            column(name: "description", value: "Role for RPC superusers with dashboard read and read/write access to inventory, purchasing, inbound, outbound, products, and stocklists")
            column(name: "role_type", value: "ROLE_RPC_SUPERUSER")
            column(name: "name", value: "RPC Superuser")
        }
    }
}
