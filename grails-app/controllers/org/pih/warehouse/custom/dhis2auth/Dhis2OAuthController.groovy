package org.pih.warehouse.custom.dhis2auth

import grails.core.GrailsApplication
import org.pih.warehouse.core.User
import org.pih.warehouse.custom.dhis2auth.Dhis2OAuthService.AccessToken
import org.pih.warehouse.custom.dhis2auth.Dhis2OAuthService.Dhis2OAuthException
import org.pih.warehouse.custom.dhis2auth.Dhis2OAuthService.Dhis2User

class Dhis2OAuthController {

    static allowedMethods = [initiate: 'GET', callback: 'GET', pending: 'GET']

    // OIDC errors a prompt=none attempt returns when interactive login/consent is needed.
    private static final List<String> SILENT_INTERACTION_ERRORS =
        ['login_required', 'consent_required', 'interaction_required']

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

        // Embedded entry point (iframe src = .../initiate?embedded=true) attempts silent SSO on v42.
        boolean silent = params.boolean('embedded') && iframeEmbeddingEnabled && dhis2OAuthService.silentAuthSupported
        session.dhis2OAuthSilent = silent

        Dhis2OAuthService.AuthorizeRequest authRequest = dhis2OAuthService.prepareAuthorize(state, silent)
        session.dhis2OAuthCodeVerifier = authRequest.codeVerifier
        redirect(url: authRequest.url)
    }

    def callback() {
        String code = params.code
        String state = params.state
        String error = params.error

        if (!state || state != session.dhis2OAuthState) {
            response.status = 400
            render "Bad request: state mismatch"
            return
        }
        boolean wasSilent = session.dhis2OAuthSilent as boolean
        session.dhis2OAuthState = null
        session.dhis2OAuthSilent = null
        String codeVerifier = session.dhis2OAuthCodeVerifier
        session.dhis2OAuthCodeVerifier = null

        if (error) {
            // A silent prompt=none attempt that needs interaction: break out of the iframe to a
            // top-level interactive login (the break-out page omits prompt=none, so no loop).
            if (wasSilent && SILENT_INTERACTION_ERRORS.contains(error)) {
                render(view: '/custom/dhis2auth/breakout')
                return
            }
            // Reason: error is attacker-influenceable (OAuth error param) — strip CR/LF and cap length to prevent log forging.
            log.warn "dhis2_oauth_authorize_error error=${error?.replaceAll(/[\r\n]/, ' ')?.take(100)}"
            flash.message = "DHIS2 login failed. Please try again or contact an administrator."
            redirect(controller: 'auth', action: 'login')
            return
        }

        if (!code) {
            response.status = 400
            render "Bad request: missing code"
            return
        }

        try {
            AccessToken token = dhis2OAuthService.exchangeCode(code, codeVerifier)
            Dhis2User dhis2User = dhis2OAuthService.resolveIdentity(token)
            User user = dhis2RegistrationService.findOrRegister(dhis2User)

            if (user.active) {
                dhis2SessionService.establishSession(user, session)
                String targetUri = session.targetUri
                session.targetUri = null
                // Reason: only follow local paths so a pre-seeded targetUri can't open-redirect off-domain
                // post-login. Reject '//' and '\' since browsers normalise backslashes to '/' (protocol-relative bypass).
                if (targetUri && targetUri.startsWith('/') && !targetUri.startsWith('//') && !targetUri.contains('\\')) {
                    redirect(uri: targetUri)
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
        grailsApplication.config.openboxes.custom.dhis2.oauth.enabled as boolean
    }

    private boolean isIframeEmbeddingEnabled() {
        List ancestors = (grailsApplication.config.openboxes.custom.iframe.frameAncestors ?: []) as List
        ancestors as boolean
    }
}
