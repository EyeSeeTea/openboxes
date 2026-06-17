package org.pih.warehouse

import util.ConfigHelper

class LinkTagLib {

    static namespace = "g"
    def userService
    def customRolePolicyService

    Closure link = { attrs, body ->

        boolean disabled = attrs.disabled ?: false
        String disabledMessage = attrs.disabledMessage ?: 'Access denied'
        def actionName = attrs.action
        def controllerName = attrs.controller ?: ""
        if (!SecurityInterceptor.actionsWithAuthUserNotRequired.contains(actionName)) {
            Map params = attrs.params instanceof Map ? new LinkedHashMap(attrs.params) : [:]
            if (attrs.id && !params.containsKey('id')) {
                params.id = attrs.id
            }
            Map customRoleAccess = session?.warehouse?.id ? customRolePolicyService.evaluateRouteAccess(
                    session?.user,
                    session?.warehouse?.id,
                    controllerName as String,
                    actionName as String,
                    params,
                    null
            ) : [hasPolicy: false, denied: false, allowed: false]
            def rule = ConfigHelper.findAccessRule(controllerName as String, actionName as String)
            def missRule = rule ? !userService.isUserInRole(session.user, rule?.accessRules?.minimumRequiredRole) : false
            def missManager = RoleInterceptor.needManager(controllerName, actionName) && (RoleInterceptor.needRequestorOrManager(controllerName, actionName) ? !userService.isUserManager(session.user) && !userService.isUserRequestor(session.user) : !userService.isUserManager(session.user))
            def missAdmin = RoleInterceptor.needAdmin(controllerName, actionName) && !userService.isUserAdmin(session.user)
            def missSuperuser = RoleInterceptor.needSuperuser(controllerName, actionName) && !userService.isSuperuser(session.user)

            // If user is not authorized to access link we just display the link body (text)
            if (customRoleAccess.denied || (!customRoleAccess.allowed && (missManager || missAdmin || missSuperuser || disabled || missRule))) {
                attrs.onclick = "alert('${disabledMessage}'); return false;"
            }
        }

        def applicationTagLib = grailsApplication.mainContext.getBean('org.grails.plugins.web.taglib.ApplicationTagLib')
        applicationTagLib.link.call(attrs, body)
    }
}
