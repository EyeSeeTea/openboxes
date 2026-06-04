package org.pih.warehouse.custom.dhis2auth

import grails.testing.gorm.DataTest
import grails.testing.web.controllers.ControllerUnitTest
import org.pih.warehouse.core.Person
import org.pih.warehouse.core.User
import org.pih.warehouse.custom.dhis2auth.Dhis2OAuthService.AccessToken
import org.pih.warehouse.custom.dhis2auth.Dhis2OAuthService.Dhis2User
import spock.lang.Specification

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
        0 * dhis2OAuthService.exchangeCode(_)
    }

    void "callback rejects a state mismatch with HTTP 400"() {
        when:
        session.dhis2OAuthState = STATE
        params.code = CODE
        params.state = 'tampered'
        controller.callback()

        then:
        response.status == 400
        0 * dhis2OAuthService.exchangeCode(_)
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
        dhis2OAuthService.exchangeCode(CODE) >> new AccessToken(accessToken: 'tok')
        dhis2OAuthService.fetchMe('tok') >> new Dhis2User(uid: 'UID11111111', username: user.username)
        dhis2RegistrationService.findOrRegister(_) >> user
    }
}
