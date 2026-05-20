package org.pih.warehouse

import org.pih.warehouse.core.RoleType

/**
 * Copyright (c) 2012 Partners In Health.  All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file epl-v10.html at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 * */
class RoleInterceptor {
    def userService

    // this interceptor depends on SecurityInterceptor
    int order = LOWEST_PRECEDENCE
    def static changeActions = [
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
            'importOutboundStockMovement'
    ]

    def static changeControllers = ['createProductFromTemplate']

    def static managerActions = [
        'inventory'           : ['createOutboundTransfer'],
        'stockMovementItemApi': ['eraseItem']
    ]

    def static adminControllers = ['createProduct', 'createProductFromTemplate', 'admin']
    def static adminActions = [
        'product'      : ['create'],
        'person'       : ['list'],
        'user'         : ['list'],
        'location'     : ['edit'],
        'shipper'      : ['create'],
        'locationGroup': ['create'],
        'locationType' : ['list'],
        'productSupplier': ['create', 'delete', 'edit']
    ]

    def static superuserControllers = []
    def static superuserActions = [
        'console'                   : ['index', 'execute'],
        'inventory'                 : ['createInboundTransfer', 'createConsumed', 'editTransaction', 'deleteTransaction', 'saveTransaction'],
        'inventoryItem'             : ['adjustStock', 'transferStock'],
        'productCatalog'            : ['create', 'importProductCatalog'],
        'productType'               : ['edit', 'delete', 'save', 'update'],
        'transactionEntry'          : ['edit', 'delete', 'save', 'update'],
        'user'                      : ['impersonate'],
        'productsConfigurationApi'  : ['downloadCategories', 'importCategories'],
        'locationType'              : ['create', 'edit', 'delete', 'update', 'save'],
        'quartz'                    : ['*'],
        'jobs'                      : ['*']
    ]

    def static facilityStorekeeperActions = [
        'inventory'    : ['createInboundTransfer', 'createConsumed', 'editTransaction', 'deleteTransaction', 'saveTransaction'],
        'inventoryItem': ['showRecordInventory', 'adjustStock', 'transferStock'],
        'stockTransfer': ['create', 'edit', 'createInboundReturn'],
        'stockTransferApi': ['list', 'read', 'create', 'update', 'stockTransferCandidates', 'returnCandidates']
    ]
    def static regionalWarehouseActions = [
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
        'json'               : ['addToRequisitionItems', 'updateRequisitionItems', 'removeRequisitionItem', 'sortRequisitionItems']
    ]

    def static invoiceActions = [
        'invoice': ['*']
    ]

    def static productManagerActions = [
            'productSupplier': ['create', 'delete', 'edit']
    ]

    def static requestorOrManagerActions = [
        'api'                 : ['getAppContext', 'getRequestTypes', 'getMenuConfig'],
        'dashboard'           : ['megamenu'],
        'grails'              : ['errors'],
        'localizationApi'     : ['list'],
        'locationApi'         : ['list'],
        'productApi'          : ['list', 'productDemand', 'productAvailabilityAndDemand'],
        'stocklistApi'        : ['list'],
        'stockMovement'       : ['list', 'createRequest'],
        'stockMovementApi'    : ['updateItems', 'create', 'updateStatus', 'read'],
        'stockMovementItemApi': ['getStockMovementItems']
    ]

    def static authenticatedActions = [
        'api'                 : ['getAppContext', 'getRequestTypes', 'getMenuConfig'],
        'dashboard'           : ['megamenu'],
        'grails'              : ['errors'],
        'localizationApi'     : ['list'],
        'locationApi'         : ['list'],
        'productApi'          : ['list', 'productDemand', 'productAvailabilityAndDemand'],
        'stocklistApi'        : ['list'],
        'stockMovement'       : ['list'],
    ]

    RoleInterceptor() {
        matchAll().except(uri: '/static/**').except(controller: "errors").except(uri: "/info").except(uri: "/health")
    }

    boolean before() {
        // Apply custom location-scoped role policy only after a warehouse context exists.
        Boolean hasWarehouseContext = session?.warehouse?.id
        Boolean hasRegionalWarehousePolicy = hasWarehouseContext &&
                userService.hasRegionalWarehousePolicy(session.user, session?.warehouse?.id)
        Boolean hasFacilityStorekeeperPolicy = hasWarehouseContext &&
                !hasRegionalWarehousePolicy &&
                userService.hasFacilityStorekeeperPolicy(session.user, session?.warehouse?.id)
        Boolean isStorekeeperAllowedAction = hasFacilityStorekeeperPolicy && needFacilityStorekeeper(controllerName, actionName, params, request)
        Boolean isStorekeeperRestrictedAction = hasFacilityStorekeeperPolicy && needStorekeeperDeniedAction(controllerName, actionName, params, request)
        Boolean isRegionalWarehouseAllowedAction = hasRegionalWarehousePolicy && needRegionalWarehouse(controllerName, actionName, params, request)
        Boolean isRegionalWarehouseRestrictedAction = hasRegionalWarehousePolicy && needRegionalWarehouseDeniedAction(controllerName, actionName, params, request)

        if (isStorekeeperRestrictedAction || isRegionalWarehouseRestrictedAction) {
            log.info("User ${session?.user?.username} does not have access to ${controllerName}/${actionName} in location ${session?.warehouse?.name}")
            redirect(controller: "errors", action: "handleForbidden")
            return false
        }
        if (isStorekeeperAllowedAction || isRegionalWarehouseAllowedAction) {
            return true
        }

        def rules = grailsApplication.config.openboxes.security.rbac.rules
        def rule = rules.find { it.controller == controllerName && it.actions.contains(actionName) ||
            it.controller == controllerName && it.actions.contains("*") ||
            it.controller == "*" && it.actions.contains("*")
        }

        if (!rule) {
            log.debug "No rule for ${controllerName}:${actionName} -> allow anonymous"
        } else {
            log.debug "Found rule matching controller ${controllerName}, action ${actionName}: " + rule
            def minimumRequiredRole = rule.accessRules?.minimumRequiredRole
            def supplementalRoles = rule.accessRules?.supplementalRoles ?: []

            Boolean isAnonymous = minimumRequiredRole == RoleType.ROLE_ANONYMOUS

            Boolean isMinimumRequiredRole = true
            if (session.user && minimumRequiredRole) {
                isMinimumRequiredRole = userService.isUserInRole(session.user, minimumRequiredRole)
            }

            Boolean isUserInRole = true
            if (session.user && supplementalRoles.size() > 0) {
                isUserInRole = userService.hasAnyRoles(session.user, supplementalRoles)
            }

            if (isAnonymous || (session.user && isMinimumRequiredRole && isUserInRole)) {
                log.debug "User has access to ${controllerName}.${actionName}"
                return true
            }
            redirect(controller: "errors", action: "handleForbidden")
            return false
        }

        // Anonymous
        if (SecurityInterceptor.actionsWithAuthUserNotRequired.contains(actionName) || actionName == "chooseLocation" ||
                SecurityInterceptor.controllersWithAuthUserNotRequired.contains(controllerName)) {
            return true
        }

        // Authorized users
        def isNotAuthenticated = !userService.isUserInRole(session.user, RoleType.ROLE_AUTHENTICATED)
        def isNotBrowser = !userService.canUserBrowse(session.user) && !needRequestorOrManager(controllerName, actionName)
        def isNotManager = needManager(controllerName, actionName) &&
            (needRequestorOrManager(controllerName, actionName) ? !userService.isUserManager(session.user) && !userService.isUserRequestor(session.user) : !userService.isUserManager(session.user))
        def isNotAdmin = needAdmin(controllerName, actionName) && !userService.isUserAdmin(session.user)
        def isNotSuperuser = needSuperuser(controllerName, actionName) && !userService.isSuperuser(session.user)
        def hasNoRoleInvoice = needInvoice(controllerName, actionName) && !userService.hasRoleInvoice(session.user)
        def isNotRequestor = needRequestorOrManager(controllerName, actionName) && !userService.isUserRequestor(session.user)
        def isNotRequestorOrManager = needRequestorOrManager(controllerName, actionName) ? !userService.isUserManager(session.user) && !userService.isUserRequestor(session.user) : false
        def hasNoRoleProductManager = needProductManager(controllerName, actionName) && !userService.hasRoleProductManager(session.user)

        if (isNotAuthenticated || isNotBrowser || isNotManager || isNotAdmin || isNotSuperuser || hasNoRoleInvoice || hasNoRoleProductManager || (isNotRequestorOrManager && !userService.hasHighestRole(session.user, session?.warehouse?.id, RoleType.ROLE_AUTHENTICATED) && !needAuthenticatedActions(controllerName, actionName)) || (isNotRequestor && userService.hasHighestRole(session.user, session?.warehouse?.id, RoleType.ROLE_AUTHENTICATED) && !needAuthenticatedActions(controllerName, actionName))) {
            log.info("User ${session?.user?.username} does not have access to ${controllerName}/${actionName} in location ${session?.warehouse?.name}")
            redirect(controller: "errors", action: "handleForbidden")
            return false
        }
        return true
    }

    static Boolean needSuperuser(controllerName, actionName) {
        (superuserActions[controllerName]?.contains("*")
            || superuserControllers?.contains(controllerName)
            || superuserActions[controllerName]?.contains(actionName)
            || superuserActions['*'].any {actionName?.startsWith(it)})
    }

    static Boolean needAdmin(controllerName, actionName) {
        adminControllers?.contains(controllerName) || adminActions[controllerName]?.contains(actionName) || adminActions['*'].any {
            actionName?.startsWith(it)
        }
    }

    static Boolean needManager(controllerName, actionName) {
        def isChangeAction = changeActions.any {
            actionName?.startsWith(it)
        }
        def isWorkflow = controllerName?.contains("Workflow")
        def isChangeController = changeControllers?.contains(controllerName)
        def isManagerAction = managerActions[controllerName]?.contains(actionName)
        return isChangeAction || isWorkflow || isChangeController || isManagerAction
    }

    static Boolean needInvoice(controllerName, actionName) {
        invoiceActions[controllerName]?.contains("*") || invoiceActions[controllerName]?.contains(actionName)
    }

    static Boolean needProductManager(controllerName, actionName) {
        productManagerActions[controllerName]?.contains("*") || productManagerActions[controllerName]?.contains(actionName)
    }

    static Boolean needRequestorOrManager(controllerName, actionName) {
        requestorOrManagerActions[controllerName]?.contains(actionName)
    }

    static Boolean needAuthenticatedActions(controllerName, actionName) {
        authenticatedActions[controllerName]?.contains(actionName)
    }

    static Boolean needFacilityStorekeeper(controllerName, actionName, params = null, request = null) {
        if (needStorekeeperDeniedAction(controllerName, actionName, params, request)) {
            return false
        }
        return facilityStorekeeperActions[controllerName]?.contains("*") ||
            facilityStorekeeperActions[controllerName]?.contains(actionName)
    }

    static Boolean needStorekeeperDeniedAction(controllerName, actionName, params, request = null) {
        // Purchasing: no access
        if (controllerName in ['purchaseOrder', 'purchaseOrderApi']) {
            return true
        }
        if (controllerName == 'supplier') {
            return true
        }

        // Outbound: no access
        if (controllerName == 'stockMovement') {
            if (actionName in ['createOutbound', 'importOutboundStockMovement', 'verifyRequest']) {
                return true
            }
            if (actionName == 'list' && params.direction?.toUpperCase() == "OUTBOUND") {
                return true
            }
        }
        if (controllerName == 'stockMovementApi') {
            String direction = params.direction ?: request?.JSON?.direction
            if (actionName == 'list' && direction?.toUpperCase() == "OUTBOUND") {
                return true
            }
            if (actionName == 'create' && direction?.toUpperCase() == "OUTBOUND") {
                return true
            }
        }
        if (controllerName == 'stockTransfer' && actionName == 'createOutboundReturn') {
            return true
        }

        // Stocklists: no access
        if (controllerName in ['requisitionTemplate', 'stocklist', 'stocklistApi', 'stocklistItemApi', 'stocklistManagement']) {
            return true
        }

        // Products: read-only
        if (controllerName == 'product' && actionName in [
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
        ]) {
            return true
        }
        if (controllerName == 'productApi' && actionName in ['create', 'update', 'delete']) {
            return true
        }

        // Inbound: no access to "Create Inbound Movement" only
        if (controllerName == 'stockMovement' && actionName == 'createInbound') {
            return !params.id
        }
        if (controllerName == 'stockMovementApi' && actionName == 'create') {
            String direction = params.direction ?: request?.JSON?.direction
            return direction?.toUpperCase() == "INBOUND"
        }

        // Legacy inbound shipment creation should remain blocked for storekeeper
        if (controllerName == 'createShipmentWorkflow' && actionName == 'createShipment') {
            return !params.id && params.type?.toUpperCase() == "INCOMING"
        }

        return false
    }

    static Boolean needRegionalWarehouse(controllerName, actionName, params = null, request = null) {
        if (needRegionalWarehouseDeniedAction(controllerName, actionName, params, request)) {
            return false
        }
        return regionalWarehouseActions[controllerName]?.contains("*") ||
                regionalWarehouseActions[controllerName]?.contains(actionName)
    }

    static Boolean needRegionalWarehouseDeniedAction(controllerName, actionName, params, request = null) {
        // Purchasing: no access (including suppliers)
        if (controllerName in ['purchaseOrder', 'purchaseOrderApi', 'supplier']) {
            return true
        }
        if (controllerName == 'dashboard' && actionName == 'supplier') {
            return true
        }

        // Inbound: no access to "Create Inbound Movement" only
        if (controllerName == 'stockMovement' && actionName == 'createInbound') {
            return !params.id
        }
        if (controllerName == 'stockMovementApi' && actionName == 'create') {
            String direction = params.direction ?: request?.JSON?.direction
            return direction?.toUpperCase() == "INBOUND"
        }
        if (controllerName == 'createShipmentWorkflow' && actionName == 'createShipment') {
            return !params.id && params.type?.toUpperCase() == "INCOMING"
        }

        // Products: read-only
        if (controllerName == 'product' && actionName in [
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
        ]) {
            return true
        }
        if (controllerName == 'productApi' && actionName in ['create', 'update', 'delete']) {
            return true
        }

        return false
    }
}
