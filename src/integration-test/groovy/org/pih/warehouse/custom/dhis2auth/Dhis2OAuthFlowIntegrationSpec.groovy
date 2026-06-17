package org.pih.warehouse.custom.dhis2auth

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import grails.core.GrailsApplication
import grails.gorm.transactions.Rollback
import org.pih.warehouse.common.base.IntegrationSpec
import org.pih.warehouse.core.User

import java.nio.charset.StandardCharsets

/**
 * End-to-end OAuth callback: real Dhis2OAuthService HTTP code exchanges against an
 * embedded stub DHIS2 (no WireMock dependency — JDK HttpServer), then real
 * Dhis2RegistrationService persists through Hibernate. Verifies the User +
 * Dhis2UserLink rows the in-memory unit tests can only approximate.
 */
@Rollback
class Dhis2OAuthFlowIntegrationSpec extends IntegrationSpec {

    Dhis2OAuthService dhis2OAuthService
    Dhis2RegistrationService dhis2RegistrationService
    GrailsApplication grailsApplication

    HttpServer server
    volatile String meBody

    def setup() {
        server = HttpServer.create(new InetSocketAddress(0), 0)
        String baseUrl = "http://localhost:${server.address.port}"
        server.createContext('/uaa/oauth/token',
            jsonHandler('{"access_token":"test-access-token","token_type":"bearer"}'))
        server.createContext('/api/me', { HttpExchange ex -> respond(ex, meBody) } as HttpHandler)
        server.start()

        grailsApplication.config.openboxes.custom.dhis2.oauth.with {
            clientId = 'openboxes'
            clientSecret = 'test-secret'
            tokenUrl = "${baseUrl}/uaa/oauth/token"
            userUrl = "${baseUrl}/api/me"
            redirectUri = "${baseUrl}/oauth/dhis2/callback"
        }
    }

    def cleanup() {
        server?.stop(0)
    }

    void "first login exchanges code, fetches user, and creates an inactive linked User"() {
        given:
        meBody = '{"id":"INTUID1234X","username":"intalice","displayName":"Int Alice","email":"intalice@example.com"}'

        when:
        Dhis2OAuthService.AccessToken token = dhis2OAuthService.exchangeCode('any-code')
        Dhis2OAuthService.Dhis2User dhis2User = dhis2OAuthService.fetchMe(token.accessToken)
        User user = dhis2RegistrationService.findOrRegister(dhis2User)

        then:
        token.accessToken == 'test-access-token'
        dhis2User.uid == 'INTUID1234X'

        and:
        user.id != null
        !user.active
        user.username == 'intalice'
        user.firstName == 'Int'
        user.lastName == 'Alice'
        user.email == 'intalice@example.com'

        and:
        Dhis2UserLink link = Dhis2UserLink.findByDhis2Uid('INTUID1234X')
        link != null
        link.user.id == user.id
        link.dhis2Username == 'intalice'
        link.lastLoginAt != null
    }

    void "returning login matches by UID, refreshes identity, and leaves active untouched"() {
        given:
        User existing = new User(
            username: 'intbob', firstName: 'Bob', lastName: 'Old', email: 'old@example.com',
            password: '*DHIS2*', passwordConfirm: '*DHIS2*', active: true,
        ).save(flush: true, failOnError: true)
        new Dhis2UserLink(user: existing, dhis2Uid: 'INTUID5678Y', dhis2Username: 'intbob')
            .save(flush: true, failOnError: true)

        meBody = '{"id":"INTUID5678Y","username":"intbob","displayName":"Bob New","email":"new@example.com"}'

        when:
        Dhis2OAuthService.AccessToken token = dhis2OAuthService.exchangeCode('any-code')
        Dhis2OAuthService.Dhis2User dhis2User = dhis2OAuthService.fetchMe(token.accessToken)
        User user = dhis2RegistrationService.findOrRegister(dhis2User)

        then:
        user.id == existing.id
        user.active
        user.email == 'new@example.com'
        user.firstName == 'Bob'
        user.lastName == 'New'

        and:
        Dhis2UserLink.countByDhis2Uid('INTUID5678Y') == 1
    }

    private static HttpHandler jsonHandler(String body) {
        { HttpExchange ex -> respond(ex, body) } as HttpHandler
    }

    private static void respond(HttpExchange ex, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8)
        ex.responseHeaders.add('Content-Type', 'application/json')
        ex.sendResponseHeaders(200, bytes.length)
        ex.responseBody.withStream { it.write(bytes) }
    }
}
