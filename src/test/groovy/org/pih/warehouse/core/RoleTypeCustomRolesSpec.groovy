package org.pih.warehouse.core

import spock.lang.Specification

class RoleTypeCustomRolesSpec extends Specification {

    def "assistant expansion should not include custom policy roles"() {
        when:
        Set<RoleType> expanded = RoleType.expand(RoleType.ROLE_ASSISTANT) as Set<RoleType>

        then:
        !expanded.contains(RoleType.ROLE_FACILITY_STOREKEEPER)
        !expanded.contains(RoleType.ROLE_REGIONAL_WAREHOUSE)
        !expanded.contains(RoleType.ROLE_RPC_SUPERUSER)
        !expanded.contains(RoleType.ROLE_REPORTING_USER)
        expanded.contains(RoleType.ROLE_ASSISTANT)
        expanded.contains(RoleType.ROLE_MANAGER)
        expanded.contains(RoleType.ROLE_ADMIN)
        expanded.contains(RoleType.ROLE_SUPERUSER)
    }

    def "custom policy roles should remain below assistant and above authenticated"() {
        expect:
        RoleType.ROLE_FACILITY_STOREKEEPER.sortOrder == RoleType.ROLE_BROWSER.sortOrder
        RoleType.ROLE_FACILITY_STOREKEEPER.sortOrder > RoleType.ROLE_ASSISTANT.sortOrder
        RoleType.ROLE_FACILITY_STOREKEEPER.sortOrder < RoleType.ROLE_AUTHENTICATED.sortOrder
    }
}
