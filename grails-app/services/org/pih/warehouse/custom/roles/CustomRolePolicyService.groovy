package org.pih.warehouse.custom.roles

import org.pih.warehouse.core.Location
import org.pih.warehouse.core.Role
import org.pih.warehouse.core.RoleType
import org.pih.warehouse.core.User

class CustomRolePolicyService {

    private static final Set<String> HIGHER_CORE_ROLE_NAMES = [
            RoleType.ROLE_SUPERUSER.name(),
            RoleType.ROLE_ADMIN.name(),
            RoleType.ROLE_MANAGER.name(),
            RoleType.ROLE_ASSISTANT.name(),
    ] as Set<String>

    private static final List<RoleType> POLICY_PRECEDENCE = [
            RoleType.ROLE_RPC_SUPERUSER,
            RoleType.ROLE_REGIONAL_WAREHOUSE,
            RoleType.ROLE_REPORTING_USER,
            RoleType.ROLE_FACILITY_STOREKEEPER,
    ]

    private static final List<String> CHANGE_ACTIONS = [
            'delete',
            'create',
            'add',
            'process',
            'save',
            'update',
            'importData',
            'receive',
            'showRecordInventory',
            'withdraw',
            'cancel',
            'change',
            'toggle',
            'exportAsCsv',
            'importOutboundStockMovement',
    ]
    private static final List<String> CHANGE_CONTROLLERS = ['createProductFromTemplate']
    private static final Map<String, List<String>> MANAGER_ACTIONS = [
            'inventory'           : ['createOutboundTransfer'],
            'stockMovementItemApi': ['eraseItem'],
    ]

    private static final Map<String, List<String>> FACILITY_STOREKEEPER_ALLOWED_ACTIONS = [
            'inventory'    : ['createInboundTransfer', 'createConsumed', 'editTransaction', 'deleteTransaction', 'saveTransaction'],
            'inventoryItem': ['showRecordInventory', 'adjustStock', 'transferStock'],
            'stockTransfer': ['create', 'edit', 'createInboundReturn'],
            'stockTransferApi': ['list', 'read', 'create', 'update', 'stockTransferCandidates', 'returnCandidates'],
    ]

    private static final Map<String, List<String>> REGIONAL_WAREHOUSE_ALLOWED_ACTIONS = [
            'inventory'        : ['createInboundTransfer', 'createConsumed', 'editTransaction', 'deleteTransaction', 'saveTransaction'],
            'inventoryItem'    : ['showRecordInventory', 'adjustStock', 'transferStock'],
            'stockTransfer'    : ['create', 'edit', 'createInboundReturn', 'createOutboundReturn'],
            'stockTransferApi' : ['list', 'read', 'create', 'update', 'stockTransferCandidates', 'returnCandidates'],
            'stockMovement'    : ['createOutbound', 'importOutboundStockMovement', 'verifyRequest'],
            'stockMovementApi' : ['list', 'create'],
            'stocklistApi'     : ['list', 'read', 'create', 'update', 'delete', 'sendMail', 'clear', 'clone', 'publish', 'unpublish', 'export'],
            'stocklistItemApi' : ['list', 'read', 'create', 'update', 'remove', 'availableStocklists'],
            'requisitionTemplate': [
                    'create',
                    'save',
                    'edit',
                    'editHeader',
                    'update',
                    'delete',
                    'clear',
                    'clone',
                    'publish',
                    'unpublish',
                    'export',
                    'batch',
                    'importData',
                    'doImport',
                    'sendMail',
                    'addToRequisitionItems',
                    'removeFromRequisitionItems',
                    'changeSortOrderAlpha',
                    'changeSortOrderChrono',
            ],
            'json'               : ['addToRequisitionItems', 'updateRequisitionItems', 'removeRequisitionItem', 'sortRequisitionItems'],
    ]

    private static final Map<String, List<String>> RPC_SUPERUSER_ALLOWED_ACTIONS = [
            'inventory'          : ['*'],
            'inventoryItem'      : ['*'],
            'stockTransfer'      : ['*'],
            'stockTransferApi'   : ['*'],
            'stockMovement'      : ['*'],
            'stockMovementApi'   : ['*'],
            'stocklist'          : ['*'],
            'stocklistApi'       : ['*'],
            'stocklistItemApi'   : ['*'],
            'stocklistManagement': ['*'],
            'requisitionTemplate': ['*'],
            'json'               : ['addToRequisitionItems', 'updateRequisitionItems', 'removeRequisitionItem', 'sortRequisitionItems'],
            'purchaseOrder'      : ['*'],
            'purchaseOrderApi'   : ['*'],
            'supplier'           : ['*'],
            'product'            : ['*'],
            'productApi'         : ['*'],
            'productType'        : ['*'],
            'category'           : ['*'],
            'categoryApi'        : ['*'],
            'productCatalog'     : ['*'],
            'tag'                : ['*'],
            'attribute'          : ['*'],
            'attributeApi'       : ['*'],
            'productAssociation' : ['*'],
            'productSupplier'    : ['*'],
            'productSupplierApi' : ['*'],
            'productSupplierPreferenceApi': ['*'],
            'productSupplierAttributeApi': ['*'],
            'productPackage'     : ['*'],
            'productPackageApi'  : ['*'],
            'productComponent'   : ['*'],
            'productGroup'       : ['*'],
            'unitOfMeasure'      : ['*'],
            'unitOfMeasureApi'   : ['*'],
            'unitOfMeasureClass' : ['*'],
            'unitOfMeasureConversion': ['*'],
            'productsConfiguration': ['*'],
            'productsConfigurationApi': ['*'],
    ]

    private static final Map<String, List<String>> REPORTING_USER_ALLOWED_ACTIONS = [
            'api'                 : ['getAppContext', 'getRequestTypes', 'getMenuConfig'],
            'dashboard'           : ['megamenu'],
            'grails'              : ['errors'],
            'localizationApi'     : ['list'],
            'locationApi'         : ['list'],
            'productApi'          : ['list', 'productDemand', 'productAvailabilityAndDemand'],
            'requisitionTemplate' : ['list', 'show'],
            'stocklistApi'        : ['list', 'read'],
            'stocklistItemApi'    : ['list', 'read', 'availableStocklists'],
            'stockMovement'       : ['list'],
            'stockMovementApi'    : ['read', 'list'],
            'stockMovementItemApi': ['getStockMovementItems'],
            'stockTransferApi'    : ['list', 'read', 'stockTransferCandidates', 'returnCandidates'],
            'stockTransfer'       : ['list'],
    ]

    private static final Set<String> STOCKLIST_MANAGEMENT_CONTROLLERS = [
            'requisitionTemplate', 'stocklist', 'stocklistApi', 'stocklistItemApi', 'stocklistManagement',
    ] as Set<String>

    private static final Set<String> PRODUCT_WRITE_ACTIONS = [
            'batchEdit',
            'batchEditProperties',
            'create',
            'save',
            'edit',
            'update',
            'delete',
            'deleteProducts',
            'importAsCsv',
            'savePackage',
            'deleteDocument',
            'deleteProductComponent',
            'deleteProductGroup',
            'editProductSynonym',
            'editProductSynonymDialog',
            'deleteSynonym',
    ] as Set<String>

    private static final Set<String> REPORTING_USER_HIDDEN_SECTIONS = [
            'purchasing',
    ] as Set<String>
    private static final Set<String> STOREKEEPER_HIDDEN_SECTIONS = [
            'purchasing',
            'outbound',
            'requisitionTemplate',
    ] as Set<String>
    private static final Set<String> REGIONAL_WAREHOUSE_HIDDEN_SECTIONS = [
            'purchasing',
    ] as Set<String>

    private static final String INBOUND_CREATE_HREF = '/stockMovement/createInbound'
    private static final String INBOUND_COMBINED_CREATE_HREF = '/stockMovement/createCombinedShipments'

    private static final List<String> REPORTING_USER_HIDDEN_HREF_FRAGMENTS = [
            '/create',
            '/edit',
            '/delete',
            '/import',
            '/upload',
            '/batch',
            '/replenishment/create',
            '/stockMovement/importOutboundStockMovement',
            '/product/mergeProducts',
            '/product/add',
            '/inventoryItem/adjustStock',
            '/inventoryItem/transferStock',
    ]

    private static final Set<String> CUSTOM_ROLE_NAMES = [
            RoleType.ROLE_FACILITY_STOREKEEPER.name(),
            RoleType.ROLE_REGIONAL_WAREHOUSE.name(),
            RoleType.ROLE_RPC_SUPERUSER.name(),
            RoleType.ROLE_REPORTING_USER.name(),
    ] as Set<String>

    private static final Set<String> RPC_SUPERUSER_MENU_SECTIONS = ['purchasing', 'products', 'requisitionTemplate'] as Set<String>
    private static final Set<RoleType> RPC_SUPERUSER_MENU_MIN_ROLES = [
            RoleType.ROLE_ASSISTANT,
            RoleType.ROLE_MANAGER,
            RoleType.ROLE_ADMIN,
            RoleType.ROLE_SUPERUSER,
    ] as Set<RoleType>

    Map<String, Boolean> getPolicyFlags(User user, String locationId) {
        RoleType activePolicy = getActivePolicy(user, locationId)
        return [
                hasFacilityStorekeeperPolicy: activePolicy == RoleType.ROLE_FACILITY_STOREKEEPER,
                hasRegionalWarehousePolicy  : activePolicy == RoleType.ROLE_REGIONAL_WAREHOUSE,
                hasRpcSuperuserPolicy       : activePolicy == RoleType.ROLE_RPC_SUPERUSER,
                hasReportingUserPolicy      : activePolicy == RoleType.ROLE_REPORTING_USER,
        ]
    }

    Map<String, Object> evaluateRouteAccess(User user, String locationId, String controllerName, String actionName, Map params = null, def request = null) {
        RoleType activePolicy = getActivePolicy(user, locationId)
        if (!activePolicy) {
            return [hasPolicy: false, denied: false, allowed: false]
        }

        boolean denied = isDeniedByPolicy(activePolicy, controllerName, actionName, params ?: [:], request)
        boolean allowed = !denied && isAllowedByPolicy(activePolicy, controllerName, actionName, params ?: [:], request)
        return [hasPolicy: true, denied: denied, allowed: allowed, policyRole: activePolicy]
    }

    boolean hasCustomPolicy(User user, String locationId, RoleType roleType) {
        return getActivePolicy(user, locationId) == roleType
    }

    boolean hasAnyCustomPolicy(User user, String locationId) {
        return getActivePolicy(user, locationId) != null
    }

    boolean hasLocationChooserRole(User user) {
        Set<String> roleNames = getAllRoleNames(user)
        Set<String> chooserRoleNames = (RoleType.listRoleTypesForLocationChooser()*.name()) as Set<String>
        return roleNames.any { chooserRoleNames.contains(it) }
    }

    ArrayList applyMenuPolicy(ArrayList menuConfig, User user, String locationId) {
        RoleType activePolicy = getActivePolicy(user, locationId)
        if (!activePolicy) {
            return menuConfig
        }

        switch (activePolicy) {
            case RoleType.ROLE_FACILITY_STOREKEEPER:
                return applyFacilityStorekeeperMenuPolicy(menuConfig)
            case RoleType.ROLE_REGIONAL_WAREHOUSE:
                return applyRegionalWarehouseMenuPolicy(menuConfig)
            case RoleType.ROLE_REPORTING_USER:
                return applyReportingUserMenuPolicy(menuConfig)
            default:
                return menuConfig
        }
    }

    Map<String, Object> getCustomRolePermissions(User user, String locationId) {
        RoleType activePolicy = getActivePolicy(user, locationId)
        return [
                activeCustomRolePolicy          : activePolicy?.name(),
                canCreateInboundMovement        : ![RoleType.ROLE_FACILITY_STOREKEEPER, RoleType.ROLE_REGIONAL_WAREHOUSE, RoleType.ROLE_REPORTING_USER].contains(activePolicy),
                canCreateInboundFromPurchaseOrder: ![RoleType.ROLE_FACILITY_STOREKEEPER, RoleType.ROLE_REGIONAL_WAREHOUSE, RoleType.ROLE_REPORTING_USER].contains(activePolicy),
                canCreateOutboundMovement       : ![RoleType.ROLE_FACILITY_STOREKEEPER, RoleType.ROLE_REPORTING_USER].contains(activePolicy),
                canManageStocklists             : [RoleType.ROLE_REGIONAL_WAREHOUSE, RoleType.ROLE_RPC_SUPERUSER].contains(activePolicy),
                canSendStocklistEmail           : [RoleType.ROLE_REGIONAL_WAREHOUSE, RoleType.ROLE_RPC_SUPERUSER].contains(activePolicy),
                canManageProducts               : activePolicy == RoleType.ROLE_RPC_SUPERUSER,
                canManagePurchasing             : ![RoleType.ROLE_FACILITY_STOREKEEPER, RoleType.ROLE_REGIONAL_WAREHOUSE, RoleType.ROLE_REPORTING_USER].contains(activePolicy),
        ]
    }

    boolean canManageStocklists(User user, String locationId) {
        RoleType activePolicy = getActivePolicy(user, locationId)
        return [RoleType.ROLE_REGIONAL_WAREHOUSE, RoleType.ROLE_RPC_SUPERUSER].contains(activePolicy)
    }

    boolean shouldHideStocklistEmail(User user, String locationId) {
        RoleType activePolicy = getActivePolicy(user, locationId)
        return [RoleType.ROLE_REPORTING_USER, RoleType.ROLE_FACILITY_STOREKEEPER].contains(activePolicy)
    }

    boolean shouldAllowRpcSuperuserMenuMinRole(User user, String locationId, String sectionId, Collection roleTypes) {
        return sectionId in RPC_SUPERUSER_MENU_SECTIONS &&
                hasCustomPolicy(user, locationId, RoleType.ROLE_RPC_SUPERUSER) &&
                roleTypes?.any { RPC_SUPERUSER_MENU_MIN_ROLES.contains(it as RoleType) }
    }

    boolean shouldAllowRpcSuperuserMenuSupplementalRole(User user, String locationId, String sectionId, Collection roleTypes) {
        return sectionId in RPC_SUPERUSER_MENU_SECTIONS &&
                hasCustomPolicy(user, locationId, RoleType.ROLE_RPC_SUPERUSER) &&
                roleTypes?.any { it in [RoleType.ROLE_SUPERUSER, RoleType.ROLE_ADMIN, RoleType.ROLE_REGIONAL_WAREHOUSE] }
    }

    private RoleType getActivePolicy(User user, String locationId) {
        if (!user || !locationId) {
            return null
        }

        Set<String> effectiveRoleNames = getEffectiveRoleNames(user, locationId)
        if (!effectiveRoleNames || effectiveRoleNames.any { HIGHER_CORE_ROLE_NAMES.contains(it) }) {
            return null
        }

        return POLICY_PRECEDENCE.find { RoleType roleType ->
            effectiveRoleNames.contains(roleType.name())
        }
    }

    private Set<String> getEffectiveRoleNames(User user, String locationId) {
        User persistentUser = resolveUser(user)
        Location location = Location.get(locationId)
        return (persistentUser?.getEffectiveRoles(location)*.roleType*.name()).findAll { it } as Set<String>
    }

    private Set<String> getAllRoleNames(User user) {
        User persistentUser = resolveUser(user)
        return (persistentUser?.getAllRoles()*.roleType*.name()).findAll { it } as Set<String>
    }

    private User resolveUser(User user) {
        if (!user?.id) {
            return user
        }
        return User.get(user.id) ?: user
    }

    private boolean isAllowedByPolicy(RoleType policy, String controllerName, String actionName, Map params, def request) {
        switch (policy) {
            case RoleType.ROLE_FACILITY_STOREKEEPER:
                return isRouteAllowed(FACILITY_STOREKEEPER_ALLOWED_ACTIONS, controllerName, actionName)
            case RoleType.ROLE_REGIONAL_WAREHOUSE:
                return isRouteAllowed(REGIONAL_WAREHOUSE_ALLOWED_ACTIONS, controllerName, actionName)
            case RoleType.ROLE_RPC_SUPERUSER:
                return isRouteAllowed(RPC_SUPERUSER_ALLOWED_ACTIONS, controllerName, actionName)
            case RoleType.ROLE_REPORTING_USER:
                return isRouteAllowed(REPORTING_USER_ALLOWED_ACTIONS, controllerName, actionName)
            default:
                return false
        }
    }

    private boolean isDeniedByPolicy(RoleType policy, String controllerName, String actionName, Map params, def request) {
        switch (policy) {
            case RoleType.ROLE_FACILITY_STOREKEEPER:
                return isDeniedForFacilityStorekeeper(controllerName, actionName, params, request)
            case RoleType.ROLE_REGIONAL_WAREHOUSE:
                return isDeniedForRegionalWarehouse(controllerName, actionName, params, request)
            case RoleType.ROLE_RPC_SUPERUSER:
                return isDeniedForRpcSuperuser(controllerName, actionName)
            case RoleType.ROLE_REPORTING_USER:
                return isDeniedForReportingUser(controllerName, actionName, params, request)
            default:
                return false
        }
    }

    private static boolean isRouteAllowed(Map<String, List<String>> allowedActions, String controllerName, String actionName) {
        List<String> controllerActions = allowedActions[controllerName] ?: []
        return controllerActions.contains('*') || controllerActions.contains(actionName)
    }

    private static boolean isDeniedForFacilityStorekeeper(String controllerName, String actionName, Map params, def request) {
        if (controllerName in ['purchaseOrder', 'purchaseOrderApi', 'supplier']) {
            return true
        }

        if (controllerName == 'stockMovement') {
            if (actionName in ['createOutbound', 'importOutboundStockMovement', 'verifyRequest']) {
                return true
            }
            if (actionName == 'list' && params.direction?.toUpperCase() == 'OUTBOUND') {
                return true
            }
        }
        if (controllerName == 'stockMovementApi') {
            String direction = params.direction ?: request?.JSON?.direction
            if (actionName == 'list' && direction?.toUpperCase() == 'OUTBOUND') {
                return true
            }
            if (actionName == 'create' && direction?.toUpperCase() == 'OUTBOUND') {
                return true
            }
        }
        if (controllerName == 'stockTransfer' && actionName == 'createOutboundReturn') {
            return true
        }

        if (controllerName in STOCKLIST_MANAGEMENT_CONTROLLERS) {
            return true
        }

        if (controllerName == 'product' && PRODUCT_WRITE_ACTIONS.contains(actionName)) {
            return true
        }
        if (controllerName == 'productApi' && actionName in ['create', 'update', 'delete', 'importCsv']) {
            return true
        }

        if (controllerName == 'stockMovement' && actionName == 'createInbound') {
            return !params.id
        }
        if (controllerName == 'stockMovementApi' && actionName == 'create') {
            String direction = params.direction ?: request?.JSON?.direction
            return direction?.toUpperCase() == 'INBOUND'
        }
        if (controllerName == 'stockMovement' && actionName == 'createCombinedShipments') {
            return !params.id
        }
        if (controllerName == 'stockMovementApi' && actionName == 'createCombinedShipments') {
            return isCreateOperation(params, request)
        }
        if (controllerName == 'createShipmentWorkflow' && actionName == 'createShipment') {
            return !params.id && params.type?.toUpperCase() == 'INCOMING'
        }

        return false
    }

    private static boolean isDeniedForRegionalWarehouse(String controllerName, String actionName, Map params, def request) {
        if (controllerName in ['purchaseOrder', 'purchaseOrderApi', 'supplier']) {
            return true
        }
        if (controllerName == 'dashboard' && actionName == 'supplier') {
            return true
        }

        if (controllerName == 'stockMovement' && actionName == 'createInbound') {
            return !params.id
        }
        if (controllerName == 'stockMovementApi' && actionName == 'create') {
            String direction = params.direction ?: request?.JSON?.direction
            return direction?.toUpperCase() == 'INBOUND'
        }
        if (controllerName == 'stockMovement' && actionName == 'createCombinedShipments') {
            return !params.id
        }
        if (controllerName == 'stockMovementApi' && actionName == 'createCombinedShipments') {
            return isCreateOperation(params, request)
        }
        if (controllerName == 'createShipmentWorkflow' && actionName == 'createShipment') {
            return !params.id && params.type?.toUpperCase() == 'INCOMING'
        }

        if (controllerName == 'product' && PRODUCT_WRITE_ACTIONS.contains(actionName)) {
            return true
        }
        if (controllerName == 'productApi' && actionName in ['create', 'update', 'delete']) {
            return true
        }

        return false
    }

    private static boolean isDeniedForRpcSuperuser(String controllerName, String actionName) {
        return controllerName == 'dashboard' && actionName in ['hideTag', 'hideCatalog', 'flushCache']
    }

    private static boolean isDeniedForReportingUser(String controllerName, String actionName, Map params, def request) {
        if (controllerName in ['purchaseOrder', 'purchaseOrderApi', 'supplier']) {
            return true
        }
        if (controllerName == 'dashboard' && actionName == 'supplier') {
            return true
        }

        if (controllerName in ['inventory', 'inventoryItem', 'stockTransfer', 'stockTransferApi', 'stockMovement', 'stockMovementApi', 'stockMovementItemApi', 'stocklist', 'stocklistApi', 'stocklistItemApi', 'stocklistManagement', 'requisitionTemplate', 'json']) {
            if (isManagerAction(controllerName, actionName)) {
                return true
            }
        }

        if (controllerName == 'stockMovement' && actionName in ['createInbound', 'createOutbound', 'importOutboundStockMovement', 'verifyRequest', 'createRequest', 'createCombinedShipments']) {
            return true
        }
        if (controllerName == 'stockMovementApi' && actionName in ['create', 'createCombinedShipments', 'update', 'updateStatus', 'delete']) {
            return true
        }

        if (controllerName == 'stocklistApi' && actionName in ['create', 'update', 'delete', 'sendMail', 'clear', 'clone', 'publish', 'unpublish', 'export']) {
            return true
        }
        if (controllerName == 'stocklistItemApi' && actionName in ['create', 'update', 'remove']) {
            return true
        }
        if (controllerName == 'requisitionTemplate' && actionName in [
                'create',
                'save',
                'edit',
                'editHeader',
                'update',
                'delete',
                'clear',
                'clone',
                'publish',
                'unpublish',
                'export',
                'batch',
                'importData',
                'doImport',
                'sendMail',
                'addToRequisitionItems',
                'removeFromRequisitionItems',
                'changeSortOrderAlpha',
                'changeSortOrderChrono',
        ]) {
            return true
        }
        if (controllerName == 'stocklistManagement') {
            return true
        }
        if (controllerName == 'json' && actionName in ['addToRequisitionItems', 'updateRequisitionItems', 'removeRequisitionItem', 'sortRequisitionItems']) {
            return true
        }

        if (controllerName == 'product' && PRODUCT_WRITE_ACTIONS.contains(actionName)) {
            return true
        }
        if (controllerName == 'productApi' && actionName in ['create', 'update', 'delete', 'importCsv']) {
            return true
        }

        return false
    }

    private static boolean isManagerAction(String controllerName, String actionName) {
        boolean isChangeAction = CHANGE_ACTIONS.any { actionName?.startsWith(it) }
        boolean isWorkflow = controllerName?.contains('Workflow')
        boolean isChangeController = CHANGE_CONTROLLERS.contains(controllerName)
        boolean isManagerAction = (MANAGER_ACTIONS[controllerName] ?: []).contains(actionName)
        return isChangeAction || isWorkflow || isChangeController || isManagerAction
    }

    private static boolean isCreateOperation(Map params, def request) {
        return !params?.id && !request?.JSON?.id
    }

    private ArrayList applyFacilityStorekeeperMenuPolicy(ArrayList menuConfig) {
        ArrayList filteredMenu = (menuConfig ?: []).findAll { section ->
            !STOREKEEPER_HIDDEN_SECTIONS.contains(section?.id)
        } as ArrayList

        filteredMenu.each { section ->
            if (section?.id == 'inbound' && section?.subsections) {
                section.subsections = section.subsections.findAll { it != null }.collect { subsection ->
                    subsection.menuItems = (subsection?.menuItems ?: []).findAll { menuItem ->
                        String href = menuItem?.href ?: ''
                        !href.contains(INBOUND_CREATE_HREF) && !href.contains(INBOUND_COMBINED_CREATE_HREF)
                    } ?: []
                    return subsection
                }
                section.subsections = section.subsections.findAll { it?.menuItems }
            }
        }

        return filteredMenu
    }

    private ArrayList applyRegionalWarehouseMenuPolicy(ArrayList menuConfig) {
        ArrayList filteredMenu = (menuConfig ?: []).findAll { section ->
            !REGIONAL_WAREHOUSE_HIDDEN_SECTIONS.contains(section?.id)
        } as ArrayList

        filteredMenu.each { section ->
            if (section?.id == 'inbound' && section?.subsections) {
                section.subsections = section.subsections.findAll { it != null }.collect { subsection ->
                    subsection.menuItems = (subsection?.menuItems ?: []).findAll { menuItem ->
                        String href = menuItem?.href ?: ''
                        !href.contains(INBOUND_CREATE_HREF) && !href.contains(INBOUND_COMBINED_CREATE_HREF)
                    } ?: []
                    return subsection
                }
                section.subsections = section.subsections.findAll { it?.menuItems }
            }
        }

        return filteredMenu
    }

    private ArrayList applyReportingUserMenuPolicy(ArrayList menuConfig) {
        ArrayList filteredMenu = (menuConfig ?: []).findAll { section ->
            !REPORTING_USER_HIDDEN_SECTIONS.contains(section?.id)
        } as ArrayList

        filteredMenu.each { section ->
            if (section?.menuItems) {
                section.menuItems = (section.menuItems ?: []).findAll { menuItem ->
                    String href = menuItem?.href ?: ''
                    !REPORTING_USER_HIDDEN_HREF_FRAGMENTS.any { href.contains(it) }
                } ?: []
            }
            if (section?.subsections) {
                section.subsections = (section.subsections ?: []).findAll { it != null }.collect { subsection ->
                    subsection.menuItems = (subsection?.menuItems ?: []).findAll { menuItem ->
                        String href = menuItem?.href ?: ''
                        !REPORTING_USER_HIDDEN_HREF_FRAGMENTS.any { href.contains(it) }
                    } ?: []
                    return subsection
                }
                section.subsections = section.subsections.findAll { it?.menuItems }
            }
        }

        return filteredMenu
    }
}
