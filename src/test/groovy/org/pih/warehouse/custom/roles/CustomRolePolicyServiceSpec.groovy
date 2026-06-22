package org.pih.warehouse.custom.roles

import grails.testing.services.ServiceUnitTest
import org.pih.warehouse.core.Location
import org.pih.warehouse.core.Role
import org.pih.warehouse.core.RoleType
import org.pih.warehouse.core.User
import org.pih.warehouse.requisition.Requisition
import org.pih.warehouse.requisition.RequisitionSourceType
import spock.lang.Specification

class CustomRolePolicyServiceSpec extends Specification implements ServiceUnitTest<CustomRolePolicyService> {

    def setup() {
        GroovySystem.metaClassRegistry.removeMetaClass(Location)
        GroovySystem.metaClassRegistry.removeMetaClass(Requisition)
        GroovySystem.metaClassRegistry.removeMetaClass(User)
        Location.metaClass.static.get = { String id ->
            Stub(Location) {
                getId() >> id
            }
        }
    }

    def cleanup() {
        GroovySystem.metaClassRegistry.removeMetaClass(Location)
        GroovySystem.metaClassRegistry.removeMetaClass(Requisition)
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

    def "should fall through to standard rbac when route has no custom policy"() {
        given:
        User user = mockUser([RoleType.ROLE_ASSISTANT], [RoleType.ROLE_ASSISTANT])

        when:
        Map<String, Object> access = service.evaluateRouteAccess(user, 'loc-1', 'stockMovement', 'createInbound')

        then:
        !access.hasPolicy
        !access.denied
        !access.allowed
    }

    def "should return no custom policy when location is null"() {
        given:
        User user = mockUser([RoleType.ROLE_REPORTING_USER], [RoleType.ROLE_REPORTING_USER])

        when:
        Map<String, Object> access = service.evaluateRouteAccess(user, null, 'stockMovement', 'list')

        then:
        !access.hasPolicy
        !access.denied
        !access.allowed
        !service.hasAnyCustomPolicy(user, null)
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

    def "should allow facility storekeeper to create stock requests"() {
        given:
        User user = mockUser([RoleType.ROLE_FACILITY_STOREKEEPER], [RoleType.ROLE_FACILITY_STOREKEEPER])

        when:
        Map<String, Object> access = service.evaluateRouteAccess(user, 'loc-1', 'stockMovement', 'createRequest')

        then:
        access.hasPolicy
        !access.denied
        access.allowed
    }

    def "should allow facility storekeeper to create stock requests through the api"() {
        given:
        User user = mockUser([RoleType.ROLE_FACILITY_STOREKEEPER], [RoleType.ROLE_FACILITY_STOREKEEPER])
        def request = [JSON: [sourceType: RequisitionSourceType.ELECTRONIC.name()]]

        when:
        Map<String, Object> access = service.evaluateRouteAccess(
                user,
                'loc-1',
                'stockMovementApi',
                'create',
                [:],
                request
        )

        then:
        access.hasPolicy
        !access.denied
        access.allowed
    }

    def "should allow facility storekeeper to list stocklists for request creation"() {
        given:
        User user = mockUser([RoleType.ROLE_FACILITY_STOREKEEPER], [RoleType.ROLE_FACILITY_STOREKEEPER])

        when:
        Map<String, Object> access = service.evaluateRouteAccess(user, 'loc-1', 'stocklistApi', 'list')

        then:
        access.hasPolicy
        !access.denied
        access.allowed
    }

    def "should allow facility storekeeper stock request comments documents and delete routes"() {
        given:
        User user = mockUser([RoleType.ROLE_FACILITY_STOREKEEPER], [RoleType.ROLE_FACILITY_STOREKEEPER])
        mockElectronicRequisition('req-1')

        when:
        Map<String, Object> addCommentAccess = service.evaluateRouteAccess(user, 'loc-1', 'stockMovement', 'addComment', [id: 'req-1'], null)
        Map<String, Object> saveCommentAccess = service.evaluateRouteAccess(user, 'loc-1', 'stockMovement', 'saveComment', [stockMovementId: 'req-1'], null)
        Map<String, Object> addDocumentAccess = service.evaluateRouteAccess(user, 'loc-1', 'stockMovement', 'addDocument', [id: 'req-1'], null)
        Map<String, Object> uploadDocumentAccess = service.evaluateRouteAccess(user, 'loc-1', 'document', 'uploadDocument', [stockMovementId: 'req-1'], null)
        Map<String, Object> removeAccess = service.evaluateRouteAccess(user, 'loc-1', 'stockRequest', 'remove', [id: 'req-1'], null)

        then:
        addCommentAccess.hasPolicy
        !addCommentAccess.denied
        addCommentAccess.allowed
        saveCommentAccess.hasPolicy
        !saveCommentAccess.denied
        saveCommentAccess.allowed
        addDocumentAccess.hasPolicy
        !addDocumentAccess.denied
        addDocumentAccess.allowed
        uploadDocumentAccess.hasPolicy
        !uploadDocumentAccess.denied
        uploadDocumentAccess.allowed
        removeAccess.hasPolicy
        !removeAccess.denied
        removeAccess.allowed
    }

    def "should deny regional warehouse stock request approval routes without approver role"() {
        given:
        User user = mockUser([RoleType.ROLE_REGIONAL_WAREHOUSE], [RoleType.ROLE_REGIONAL_WAREHOUSE])
        mockElectronicRequisition('req-1')

        when:
        Map<String, Object> updateStatusAccess = service.evaluateRouteAccess(user, 'loc-1', 'stockMovement', 'updateStatus', [id: 'req-1'], null)
        Map<String, Object> rejectAccess = service.evaluateRouteAccess(user, 'loc-1', 'stockRequest', 'reject', [id: 'req-1'], null)
        Map<String, Object> rollbackApprovalAccess = service.evaluateRouteAccess(user, 'loc-1', 'stockRequest', 'rollbackApproval', [id: 'req-1'], null)

        then:
        updateStatusAccess.hasPolicy
        !updateStatusAccess.denied
        !updateStatusAccess.allowed
        rejectAccess.hasPolicy
        !rejectAccess.denied
        !rejectAccess.allowed
        rollbackApprovalAccess.hasPolicy
        !rollbackApprovalAccess.denied
        rollbackApprovalAccess.allowed
    }

    def "should allow regional warehouse stock request approval routes with approver role"() {
        given:
        User user = mockUser(
                [RoleType.ROLE_REGIONAL_WAREHOUSE, RoleType.ROLE_REQUISITION_APPROVER],
                [RoleType.ROLE_REGIONAL_WAREHOUSE, RoleType.ROLE_REQUISITION_APPROVER]
        )
        mockElectronicRequisition('req-1')

        when:
        Map<String, Object> updateStatusAccess = service.evaluateRouteAccess(user, 'loc-1', 'stockMovement', 'updateStatus', [id: 'req-1'], null)
        Map<String, Object> rejectAccess = service.evaluateRouteAccess(user, 'loc-1', 'stockRequest', 'reject', [id: 'req-1'], null)
        Map<String, Object> rollbackApprovalAccess = service.evaluateRouteAccess(user, 'loc-1', 'stockRequest', 'rollbackApproval', [id: 'req-1'], null)

        then:
        updateStatusAccess.hasPolicy
        !updateStatusAccess.denied
        updateStatusAccess.allowed
        rejectAccess.hasPolicy
        !rejectAccess.denied
        rejectAccess.allowed
        rollbackApprovalAccess.hasPolicy
        !rollbackApprovalAccess.denied
        rollbackApprovalAccess.allowed
    }

    def "should allow facility storekeeper to complete record stock workflow"() {
        given:
        User user = mockUser([RoleType.ROLE_FACILITY_STOREKEEPER], [RoleType.ROLE_FACILITY_STOREKEEPER])

        when:
        Map<String, Object> showAccess = service.evaluateRouteAccess(user, 'loc-1', 'inventoryItem', 'showRecordInventory')
        Map<String, Object> saveAccess = service.evaluateRouteAccess(user, 'loc-1', 'inventoryItem', 'saveRecordInventory')
        Map<String, Object> apiAccess = service.evaluateRouteAccess(user, 'loc-1', 'recordStockApi', 'saveRecordStock')

        then:
        showAccess.hasPolicy
        !showAccess.denied
        showAccess.allowed
        saveAccess.hasPolicy
        !saveAccess.denied
        saveAccess.allowed
        apiAccess.hasPolicy
        !apiAccess.denied
        apiAccess.allowed
    }

    def "should allow regional warehouse to create and save inventory adjustments"() {
        given:
        User user = mockUser([RoleType.ROLE_REGIONAL_WAREHOUSE], [RoleType.ROLE_REGIONAL_WAREHOUSE])

        when:
        Map<String, Object> createAccess = service.evaluateRouteAccess(user, 'loc-1', 'inventory', 'createAdjustment')
        Map<String, Object> saveAccess = service.evaluateRouteAccess(user, 'loc-1', 'inventory', 'saveAdjustmentTransaction')

        then:
        createAccess.hasPolicy
        !createAccess.denied
        createAccess.allowed
        saveAccess.hasPolicy
        !saveAccess.denied
        saveAccess.allowed
    }

    def "should allow regional warehouse to create stock requests"() {
        given:
        User user = mockUser([RoleType.ROLE_REGIONAL_WAREHOUSE], [RoleType.ROLE_REGIONAL_WAREHOUSE])

        when:
        Map<String, Object> access = service.evaluateRouteAccess(user, 'loc-1', 'stockMovement', 'createRequest')

        then:
        access.hasPolicy
        !access.denied
        access.allowed
    }

    def "should allow regional warehouse to update stock requests through the api"() {
        given:
        User user = mockUser([RoleType.ROLE_REGIONAL_WAREHOUSE], [RoleType.ROLE_REGIONAL_WAREHOUSE])
        mockElectronicRequisition('req-1')

        when:
        Map<String, Object> readAccess = service.evaluateRouteAccess(user, 'loc-1', 'stockMovementApi', 'read', [id: 'req-1'], null)
        Map<String, Object> updateAccess = service.evaluateRouteAccess(user, 'loc-1', 'stockMovementApi', 'updateRequisition', [id: 'req-1'], null)
        Map<String, Object> itemAccess = service.evaluateRouteAccess(user, 'loc-1', 'stockMovementItemApi', 'getStockMovementItems', [id: 'req-1'], null)

        then:
        readAccess.hasPolicy
        !readAccess.denied
        readAccess.allowed
        updateAccess.hasPolicy
        !updateAccess.denied
        updateAccess.allowed
        itemAccess.hasPolicy
        !itemAccess.denied
        itemAccess.allowed
    }

    def "should allow regional warehouse to complete record stock workflow"() {
        given:
        User user = mockUser([RoleType.ROLE_REGIONAL_WAREHOUSE], [RoleType.ROLE_REGIONAL_WAREHOUSE])

        when:
        Map<String, Object> showAccess = service.evaluateRouteAccess(user, 'loc-1', 'inventoryItem', 'showRecordInventory')
        Map<String, Object> saveAccess = service.evaluateRouteAccess(user, 'loc-1', 'inventoryItem', 'saveRecordInventory')
        Map<String, Object> apiAccess = service.evaluateRouteAccess(user, 'loc-1', 'recordStockApi', 'saveRecordStock')

        then:
        showAccess.hasPolicy
        !showAccess.denied
        showAccess.allowed
        saveAccess.hasPolicy
        !saveAccess.denied
        saveAccess.allowed
        apiAccess.hasPolicy
        !apiAccess.denied
        apiAccess.allowed
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

    private void mockElectronicRequisition(String id) {
        Requisition.metaClass.static.get = { String requisitionId ->
            if (requisitionId != id) {
                return null
            }

            Stub(Requisition) {
                getSourceType() >> RequisitionSourceType.ELECTRONIC
            }
        }
    }
}
