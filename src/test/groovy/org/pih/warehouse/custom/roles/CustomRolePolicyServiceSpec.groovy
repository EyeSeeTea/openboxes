package org.pih.warehouse.custom.roles

import grails.testing.services.ServiceUnitTest
import org.pih.warehouse.core.Location
import org.pih.warehouse.core.Role
import org.pih.warehouse.core.RoleType
import org.pih.warehouse.core.User
import spock.lang.Specification

class CustomRolePolicyServiceSpec extends Specification implements ServiceUnitTest<CustomRolePolicyService> {

    def setup() {
        GroovySystem.metaClassRegistry.removeMetaClass(Location)
        GroovySystem.metaClassRegistry.removeMetaClass(User)
        Location.metaClass.static.get = { String id ->
            Stub(Location) {
                getId() >> id
            }
        }
    }

    def cleanup() {
        GroovySystem.metaClassRegistry.removeMetaClass(Location)
        GroovySystem.metaClassRegistry.removeMetaClass(User)
    }

    def "should not activate custom policy when higher core role is present"() {
        given:
        User user = mockUser(
                [RoleType.ROLE_REGIONAL_WAREHOUSE, RoleType.ROLE_MANAGER],
                [RoleType.ROLE_REGIONAL_WAREHOUSE, RoleType.ROLE_MANAGER]
        )

        when:
        Map<String, Boolean> flags = service.getPolicyFlags(user, 'loc-1')

        then:
        !flags.hasFacilityStorekeeperPolicy
        !flags.hasRegionalWarehousePolicy
        !flags.hasRpcSuperuserPolicy
        !flags.hasReportingUserPolicy
    }

    def "should prioritize rpc superuser over other custom roles"() {
        given:
        User user = mockUser(
                [RoleType.ROLE_RPC_SUPERUSER, RoleType.ROLE_REGIONAL_WAREHOUSE],
                [RoleType.ROLE_RPC_SUPERUSER, RoleType.ROLE_REGIONAL_WAREHOUSE]
        )

        when:
        Map<String, Boolean> flags = service.getPolicyFlags(user, 'loc-1')

        then:
        flags.hasRpcSuperuserPolicy
        !flags.hasRegionalWarehousePolicy
        !flags.hasFacilityStorekeeperPolicy
        !flags.hasReportingUserPolicy
    }

    def "should deny combined shipment creation for regional warehouse policy"() {
        given:
        User user = mockUser([RoleType.ROLE_REGIONAL_WAREHOUSE], [RoleType.ROLE_REGIONAL_WAREHOUSE])

        when:
        Map<String, Object> access = service.evaluateRouteAccess(
                user,
                'loc-1',
                'stockMovement',
                'createCombinedShipments',
                [:],
                null
        )

        then:
        access.hasPolicy
        access.denied
        !access.allowed
    }

    def "should deny combined shipment api creation for facility storekeeper policy"() {
        given:
        User user = mockUser([RoleType.ROLE_FACILITY_STOREKEEPER], [RoleType.ROLE_FACILITY_STOREKEEPER])
        def request = [JSON: [:]]

        when:
        Map<String, Object> access = service.evaluateRouteAccess(
                user,
                'loc-1',
                'stockMovementApi',
                'createCombinedShipments',
                [:],
                request
        )

        then:
        access.hasPolicy
        access.denied
        !access.allowed
    }

    def "should build custom permission payload for reporting user"() {
        given:
        User user = mockUser([RoleType.ROLE_REPORTING_USER], [RoleType.ROLE_REPORTING_USER])

        when:
        Map<String, Object> permissions = service.getCustomRolePermissions(user, 'loc-1')

        then:
        permissions.activeCustomRolePolicy == RoleType.ROLE_REPORTING_USER.name()
        permissions.canCreateInboundMovement == false
        permissions.canCreateInboundFromPurchaseOrder == false
        permissions.canCreateOutboundMovement == false
        permissions.canManageProducts == false
        permissions.canManagePurchasing == false
        permissions.canManageStocklists == false
        permissions.canSendStocklistEmail == false
        permissions.canUseSuperuserPurchasingActions == false
    }

    def "should build custom permission payload for rpc superuser"() {
        given:
        User user = mockUser([RoleType.ROLE_RPC_SUPERUSER], [RoleType.ROLE_RPC_SUPERUSER])

        when:
        Map<String, Object> permissions = service.getCustomRolePermissions(user, 'loc-1')

        then:
        permissions.activeCustomRolePolicy == RoleType.ROLE_RPC_SUPERUSER.name()
        permissions.canCreateInboundMovement == true
        permissions.canCreateInboundFromPurchaseOrder == true
        permissions.canCreateOutboundMovement == true
        permissions.canManageProducts == true
        permissions.canManagePurchasing == true
        permissions.canManageStocklists == true
        permissions.canSendStocklistEmail == true
        permissions.canUseSuperuserPurchasingActions == true
    }

    def "should deny product create action for read-only product policies"() {
        when:
        Map<String, Object> reportingUserAccess = service.evaluateRouteAccess(
                mockUser([RoleType.ROLE_REPORTING_USER], [RoleType.ROLE_REPORTING_USER]),
                'loc-1',
                'product',
                'create',
                [:],
                null
        )
        Map<String, Object> regionalWarehouseAccess = service.evaluateRouteAccess(
                mockUser([RoleType.ROLE_REGIONAL_WAREHOUSE], [RoleType.ROLE_REGIONAL_WAREHOUSE]),
                'loc-1',
                'product',
                'create',
                [:],
                null
        )

        then:
        reportingUserAccess.hasPolicy
        reportingUserAccess.denied
        !reportingUserAccess.allowed
        regionalWarehouseAccess.hasPolicy
        regionalWarehouseAccess.denied
        !regionalWarehouseAccess.allowed
    }

    def "should preserve standard core role behavior without custom policy"() {
        given:
        User user = mockUser([RoleType.ROLE_ASSISTANT], [RoleType.ROLE_ASSISTANT])

        when:
        Map<String, Object> permissions = service.getCustomRolePermissions(user, 'loc-1')

        then:
        permissions.activeCustomRolePolicy == null
        permissions.canCreateInboundMovement == true
        permissions.canCreateInboundFromPurchaseOrder == true
        permissions.canCreateOutboundMovement == true
        permissions.canManageProducts == false
        permissions.canManagePurchasing == true
        permissions.canManageStocklists == false
        permissions.canSendStocklistEmail == true
        permissions.canUseSuperuserPurchasingActions == false
    }

    def "should allow reporting user to export visible csv data"() {
        given:
        User user = mockUser([RoleType.ROLE_REPORTING_USER], [RoleType.ROLE_REPORTING_USER])

        when:
        Map<String, Object> access = service.evaluateRouteAccess(user, 'loc-1', 'stockMovement', 'exportAsCsv')

        then:
        access.hasPolicy
        !access.denied
    }

    def "should remove inbound create actions from regional warehouse menu"() {
        given:
        User user = mockUser([RoleType.ROLE_REGIONAL_WAREHOUSE], [RoleType.ROLE_REGIONAL_WAREHOUSE])
        ArrayList menuConfig = [[
                                        id         : 'inbound',
                                        subsections: [[
                                                              menuItems: [
                                                                      [href: '/stockMovement/createInbound'],
                                                                      [href: '/stockMovement/createCombinedShipments?direction=INBOUND'],
                                                                      [href: '/stockMovement/list?direction=INBOUND'],
                                                              ]
                                                      ]]
                                ]] as ArrayList

        when:
        ArrayList filtered = service.applyMenuPolicy(menuConfig, user, 'loc-1')
        List<Map> menuItems = filtered[0].subsections[0].menuItems

        then:
        menuItems*.href == ['/stockMovement/list?direction=INBOUND']
    }

    def "should recognize location chooser role from all assigned roles"() {
        given:
        User user = mockUser(
                [RoleType.ROLE_AUTHENTICATED],
                [RoleType.ROLE_AUTHENTICATED, RoleType.ROLE_FACILITY_STOREKEEPER]
        )

        expect:
        service.hasLocationChooserRole(user)
    }

    def "should recognize default custom role as location chooser role"() {
        given:
        User user = mockUser(
                [RoleType.ROLE_REPORTING_USER],
                [RoleType.ROLE_REPORTING_USER]
        )

        expect:
        service.hasLocationChooserRole(user)
    }

    def "should resolve detached user before evaluating custom role policy"() {
        given:
        User detachedUser = Stub(User) {
            getId() >> "user-1"
            getEffectiveRoles(_ as Location) >> { throw new IllegalStateException("Detached user should not be used directly") }
        }
        User persistentUser = Stub(User) {
            getEffectiveRoles(_ as Location) >> [role(RoleType.ROLE_REGIONAL_WAREHOUSE)]
            getAllRoles() >> [role(RoleType.ROLE_REGIONAL_WAREHOUSE)]
        }
        User.metaClass.static.get = { String id ->
            assert id == "user-1"
            return persistentUser
        }

        when:
        Map<String, Boolean> flags = service.getPolicyFlags(detachedUser, "loc-1")

        then:
        flags.hasRegionalWarehousePolicy
        !flags.hasFacilityStorekeeperPolicy
        !flags.hasRpcSuperuserPolicy
        !flags.hasReportingUserPolicy
    }

    def "should expose hasAnyCustomPolicy for reporting user"() {
        given:
        User user = mockUser([RoleType.ROLE_REPORTING_USER], [RoleType.ROLE_REPORTING_USER])

        expect:
        service.hasAnyCustomPolicy(user, 'loc-1')
    }

    def "should allow rpc superuser to pass assistant minimum menu role in allowed sections"() {
        given:
        User user = mockUser([RoleType.ROLE_RPC_SUPERUSER], [RoleType.ROLE_RPC_SUPERUSER])
        Location location = Stub(Location) {
            getId() >> 'loc-1'
        }

        expect:
        service.userHasMinimumMenuRole(user, location, [RoleType.ROLE_ASSISTANT], 'purchasing')
    }

    def "should deny reporting user assistant minimum menu role"() {
        given:
        User user = mockUser([RoleType.ROLE_REPORTING_USER], [RoleType.ROLE_REPORTING_USER])
        Location location = Stub(Location) {
            getId() >> 'loc-1'
        }

        expect:
        !service.userHasMinimumMenuRole(user, location, [RoleType.ROLE_ASSISTANT], 'purchasing')
    }

    def "should allow rpc superuser supplemental menu role for purchasing section"() {
        given:
        User user = mockUser([RoleType.ROLE_RPC_SUPERUSER], [RoleType.ROLE_RPC_SUPERUSER])
        Location location = Stub(Location) {
            getId() >> 'loc-1'
        }

        expect:
        service.userHasSupplementalMenuRole(user, location, [RoleType.ROLE_SUPERUSER], 'purchasing')
    }

    private User mockUser(List<RoleType> effectiveRoleTypes, List<RoleType> allRoleTypes) {
        List<Role> effectiveRoles = effectiveRoleTypes.collect { role(it) }
        List<Role> allRoles = allRoleTypes.collect { role(it) }
        return Stub(User) {
            getEffectiveRoles(_ as Location) >> effectiveRoles
            getAllRoles() >> allRoles
        }
    }

    private Role role(RoleType roleType) {
        return new Role(roleType: roleType, name: roleType.name())
    }
}
