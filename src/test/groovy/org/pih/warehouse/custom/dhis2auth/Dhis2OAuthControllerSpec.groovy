package org.pih.warehouse.custom.dhis2auth

import grails.testing.gorm.DataTest
import grails.testing.web.controllers.ControllerUnitTest
import org.pih.warehouse.core.Person
import org.pih.warehouse.core.User
import org.pih.warehouse.custom.dhis2auth.Dhis2OAuthService.AccessToken
import org.pih.warehouse.custom.dhis2auth.Dhis2OAuthService.AuthorizeRequest
import org.pih.warehouse.custom.dhis2auth.Dhis2OAuthService.Dhis2User
import spock.lang.Specification
import spock.lang.Unroll

class Dhis2OAuthControllerSpec extends Specification
    implements ControllerUnitTest<Dhis2OAuthController>, DataTest {

    private static final String CODE = 'auth-code-123'
    private static final String STATE = 'state-token-abc'

    Dhis2OAuthService dhis2OAuthService = Mock()
    Dhis2RegistrationService dhis2RegistrationService = Mock()

    Class[] getDomainClassesToMock() {
        [Person, User, Dhis2UserLink]
    }

    def setup() {
        controller.dhis2OAuthService = dhis2OAuthService
        controller.dhis2RegistrationService = dhis2RegistrationService
        controller.dhis2SessionService = new Dhis2SessionService()
    }

    void "callback rejects a missing code with HTTP 400"() {
        when:
        params.state = STATE
        controller.callback()

        then:
        response.status == 400
        0 * dhis2OAuthService.exchangeCode(*_)
    }

    void "callback rejects a state mismatch with HTTP 400"() {
        when:
        session.dhis2OAuthState = STATE
        params.code = CODE
        params.state = 'tampered'
        controller.callback()

        then:
        response.status == 400
        0 * dhis2OAuthService.exchangeCode(*_)
    }

    void "initiate stores the PKCE verifier from prepareAuthorize in the session"() {
        given:
        enableOAuth('v42')
        dhis2OAuthService.prepareAuthorize(_, _) >> new AuthorizeRequest(
            url: 'https://dhis2.example.com/oauth2/authorize', codeVerifier: 'verifier-xyz')

        when:
        controller.initiate()

        then:
        session.dhis2OAuthCodeVerifier == 'verifier-xyz'
        response.redirectedUrl == 'https://dhis2.example.com/oauth2/authorize'
    }

    void "callback passes the session verifier to exchangeCode and clears it for the v42 profile"() {
        given:
        enableOAuth('v42')
        User user = new User(username: 'carol', active: true)
        stubUserResolution(user)

        when:
        session.dhis2OAuthState = STATE
        session.dhis2OAuthCodeVerifier = 'verifier-xyz'
        params.code = CODE
        params.state = STATE
        controller.callback()

        then:
        1 * dhis2OAuthService.exchangeCode(CODE, 'verifier-xyz') >> new AccessToken(accessToken: 'tok')
        session.dhis2OAuthCodeVerifier == null
        session.user == user
        response.redirectedUrl == '/dashboard/index'
    }

    void "initiate leaves the PKCE verifier null when prepareAuthorize returns none"() {
        given:
        enableOAuth('v40')
        dhis2OAuthService.prepareAuthorize(_, _) >> new AuthorizeRequest(
            url: 'https://dhis2.example.com/uaa/oauth/authorize', codeVerifier: null)

        when:
        controller.initiate()

        then:
        session.dhis2OAuthCodeVerifier == null
    }

    void "embedded initiate on v42 attempts a silent prompt=none authorize"() {
        given:
        enableOAuth('v42')
        enableIframe()
        dhis2OAuthService.isSilentAuthSupported() >> true
        dhis2OAuthService.prepareAuthorize(_, true) >> new AuthorizeRequest(
            url: 'https://dhis2.example.com/oauth2/authorize?prompt=none', codeVerifier: 'verifier-xyz')

        when:
        params.embedded = 'true'
        controller.initiate()

        then:
        session.dhis2OAuthSilent == true
        session.dhis2OAuthCodeVerifier == 'verifier-xyz'
        response.redirectedUrl == 'https://dhis2.example.com/oauth2/authorize?prompt=none'
    }

    void "embedded initiate does not go silent when embedding is disabled"() {
        given:
        enableOAuth('v42')
        dhis2OAuthService.prepareAuthorize(_, _) >> new AuthorizeRequest(
            url: 'https://dhis2.example.com/oauth2/authorize', codeVerifier: 'v')

        when:
        params.embedded = 'true'
        controller.initiate()

        then:
        session.dhis2OAuthSilent == false
        response.redirectedUrl == 'https://dhis2.example.com/oauth2/authorize'
    }

    void "callback breaks out of the iframe when a silent attempt returns login_required"() {
        given:
        enableOAuth('v42')

        when:
        session.dhis2OAuthState = STATE
        session.dhis2OAuthSilent = true
        params.state = STATE
        params.error = 'login_required'
        controller.callback()

        then:
        view == '/custom/dhis2auth/breakout'
        session.dhis2OAuthSilent == null
        0 * dhis2OAuthService.exchangeCode(*_)
    }

    void "callback redirects to login when an error arrives outside a silent attempt"() {
        when:
        session.dhis2OAuthState = STATE
        params.state = STATE
        params.error = 'access_denied'
        controller.callback()

        then:
        response.redirectedUrl == '/auth/login'
        0 * dhis2OAuthService.exchangeCode(*_)
    }

    void "callback does not break out for login_required when the attempt was not silent"() {
        when:
        session.dhis2OAuthState = STATE
        session.dhis2OAuthSilent = false
        params.state = STATE
        params.error = 'login_required'
        controller.callback()

        then: "it redirects to login (a break-out would render the breakout view, not redirect)"
        response.redirectedUrl == '/auth/login'
        0 * dhis2OAuthService.exchangeCode(*_)
    }

    private void enableOAuth(String profile) {
        controller.grailsApplication.config.openboxes.custom.dhis2.oauth.enabled = true
        controller.grailsApplication.config.openboxes.custom.dhis2.oauth.profile = profile
    }

    private void enableIframe() {
        controller.grailsApplication.config.openboxes.custom.iframe.frameAncestors = ['https://dhis2.example.com']
    }

    void "callback for an active user establishes a session and redirects to the dashboard"() {
        given:
        User user = new User(username: 'alice', active: true)
        stubExchange(user)

        when:
        session.dhis2OAuthState = STATE
        params.code = CODE
        params.state = STATE
        controller.callback()

        then:
        session.user == user
        session.userName == 'alice'
        session.pendingDhis2UserId == null
        response.redirectedUrl == '/dashboard/index'
    }

    void "callback follows a local targetUri after an active-user login"() {
        given:
        User user = new User(username: 'ivan', active: true)
        stubExchange(user)

        when:
        session.dhis2OAuthState = STATE
        session.targetUri = '/stockMovement/list'
        params.code = CODE
        params.state = STATE
        controller.callback()

        then:
        response.redirectedUrl == '/stockMovement/list'
        session.targetUri == null
    }

    @Unroll
    void "callback ignores an off-domain targetUri (#targetUri) and redirects to the dashboard"() {
        given:
        User user = new User(username: 'judy', active: true)
        stubExchange(user)

        when:
        session.dhis2OAuthState = STATE
        session.targetUri = targetUri
        params.code = CODE
        params.state = STATE
        controller.callback()

        then:
        response.redirectedUrl == '/dashboard/index'
        session.targetUri == null

        where:
        targetUri << ['//evil.example.com/phish', 'https://evil.example.com', 'javascript:alert(1)', '/\\evil.example.com']
    }

    void "callback for an inactive user sets the pending marker and redirects to the pending page"() {
        given:
        User user = new User(username: 'bob', active: false)
        user.id = 'PENDING-ID-1'
        stubExchange(user)

        when:
        session.dhis2OAuthState = STATE
        params.code = CODE
        params.state = STATE
        controller.callback()

        then:
        session.user == null
        session.pendingDhis2UserId == 'PENDING-ID-1'
        response.redirectedUrl == '/dhis2OAuth/pending'
    }

    private void stubExchange(User user) {
        dhis2OAuthService.exchangeCode(CODE, null) >> new AccessToken(accessToken: 'tok')
        stubUserResolution(user)
    }

    // Stubs identity + registration but leaves exchangeCode to the caller so a
    // test can verify the verifier argument it was passed.
    private void stubUserResolution(User user) {
        dhis2OAuthService.resolveIdentity(_) >> new Dhis2User(uid: 'UID11111111', username: user.username)
        dhis2RegistrationService.findOrRegister(_) >> user
    }
}
