databaseChangeLog = {
    changeSet(author: "codex", id: "202605151200-0") {
        preConditions(onFail: "MARK_RAN") {
            sqlCheck(expectedResult: "0", "select count(*) from role where role_type = 'ROLE_FACILITY_STOREKEEPER'")
        }

        insert(tableName: "role") {
            column(name: "id", value: "ROLE_FACILITY_STOREKEEPER")
            column(name: "version", valueNumeric: "0")
            column(name: "description", value: "Role for facility storekeepers with inventory-change access and restricted purchasing/shipment creation access")
            column(name: "role_type", value: "ROLE_FACILITY_STOREKEEPER")
            column(name: "name", value: "Facility Storekeeper")
        }
    }
}
