package org.pih.warehouse.custom.dhis2auth

import grails.core.GrailsApplication
import org.pih.warehouse.core.User
import org.pih.warehouse.custom.dhis2auth.Dhis2OAuthService.AccessToken
import org.pih.warehouse.custom.dhis2auth.Dhis2OAuthService.Dhis2OAuthException
import org.pih.warehouse.custom.dhis2auth.Dhis2OAuthService.Dhis2User

class Dhis2OAuthController {

    static allowedMethods = [initiate: 'GET', callback: 'GET', pending: 'GET']

    GrailsApplication grailsApplication
    Dhis2OAuthService dhis2OAuthService
    Dhis2RegistrationService dhis2RegistrationService
    Dhis2SessionService dhis2SessionService

    def initiate() {
        if (!oauthEnabled) {
            flash.message = "DHIS2 SSO is not configured on this system."
            redirect(controller: 'auth', action: 'login')
            return
        }

        String state = UUID.randomUUID().toString()
        session.dhis2OAuthState = state

        redirect(url: dhis2OAuthService.buildAuthorizeUrl(state))
    }

    def callback() {
        String code = params.code
        String state = params.state

        if (!code || !state) {
            response.status = 400
            render "Bad request: missing code or state"
            return
        }
        if (state != session.dhis2OAuthState) {
            response.status = 400
            render "Bad request: state mismatch"
            return
        }
        session.dhis2OAuthState = null

        try {
            AccessToken token = dhis2OAuthService.exchangeCode(code)
            Dhis2User dhis2User = dhis2OAuthService.fetchMe(token.accessToken)
            User user = dhis2RegistrationService.findOrRegister(dhis2User)

            if (user.active) {
                dhis2SessionService.establishSession(user, session)
                if (session.targetUri) {
                    String uri = session.targetUri
                    session.targetUri = null
                    redirect(uri: uri)
                } else {
                    redirect(controller: 'dashboard', action: 'index')
                }
            } else {
                dhis2SessionService.setPendingSession(user, session)
                redirect(controller: 'dhis2OAuth', action: 'pending')
            }
        } catch (Dhis2OAuthException ex) {
            log.error "dhis2_oauth_callback_failed", ex
            flash.message = "DHIS2 login failed. Please try again or contact an administrator."
            redirect(controller: 'auth', action: 'login')
        }
    }

    def pending() {
        render(view: '/custom/dhis2auth/pending')
    }

    private boolean isOauthEnabled() {
        grailsApplication.config.openboxes.dhis2.oauth.enabled as boolean
    }
}
