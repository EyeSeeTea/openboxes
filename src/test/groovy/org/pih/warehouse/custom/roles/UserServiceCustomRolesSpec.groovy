package org.pih.warehouse.custom.roles

import grails.testing.gorm.DataTest
import grails.testing.services.ServiceUnitTest
import org.pih.warehouse.core.Location
import org.pih.warehouse.core.LocationRole
import org.pih.warehouse.core.Role
import org.pih.warehouse.core.RoleType
import org.pih.warehouse.core.User
import org.pih.warehouse.core.UserService
import spock.lang.Specification

class UserServiceCustomRolesSpec extends Specification implements ServiceUnitTest<UserService>, DataTest {

    def setup() {
        mockDomains(User, Role, Location, LocationRole)
        service.authService = [currentLocation: null]
    }

    def "hasAnyRoles should check supplemental roles exactly without hierarchy expansion"() {
        given:
        User user = Stub(User) {
            getId() >> null
            getEffectiveRoles(null) >> [new Role(roleType: RoleType.ROLE_SUPERUSER)]
            getEffectiveRoles(_ as Location) >> [new Role(roleType: RoleType.ROLE_SUPERUSER)]
        }

        expect:
        !service.hasAnyRoles(user, [RoleType.ROLE_INVOICE])
        service.hasAnyRoles(user, [RoleType.ROLE_SUPERUSER])
    }
}
