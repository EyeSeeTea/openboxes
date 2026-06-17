package org.pih.warehouse.custom.dhis2auth

import grails.testing.web.interceptor.InterceptorUnitTest
import org.pih.warehouse.SecurityInterceptor
import org.pih.warehouse.auth.AuthService
import spock.lang.Specification

/**
 * The pending-access gate lives in upstream SecurityInterceptor; the test is ours.
 * Verifies a DHIS2 user who completed OAuth but is not yet active (session marker
 * set, no session.user) is routed to the pending page for everything except the
 * pending page itself and logout.
 */
class SecurityInterceptorPendingGateSpec extends Specification
    implements InterceptorUnitTest<SecurityInterceptor> {

    def setup() {
        interceptor.authService = Mock(AuthService)
    }

    void "pending DHIS2 user is redirected to the pending page"() {
        when:
        boolean result = gateRequest('dashboard', 'index')

        then:
        !result
        response.redirectedUrl == '/dhis2OAuth/pending'
    }

    void "pending DHIS2 user can reach the pending page"() {
        when:
        boolean result = gateRequest('dhis2OAuth', 'pending')

        then:
        result
        response.redirectedUrl == null
    }

    void "pending DHIS2 user can log out"() {
        when:
        boolean result = gateRequest('auth', 'logout')

        then:
        result
        response.redirectedUrl == null
    }

    private boolean gateRequest(String controllerName, String actionName) {
        withRequest(controller: controllerName, action: actionName)
        webRequest.controllerName = controllerName
        webRequest.actionName = actionName
        params.controller = controllerName
        session.pendingDhis2UserId = 'PENDING-ID-1'
        interceptor.before()
    }
}
