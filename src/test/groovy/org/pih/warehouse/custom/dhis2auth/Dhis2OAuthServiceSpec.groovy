package org.pih.warehouse.custom.dhis2auth

import grails.testing.services.ServiceUnitTest
import spock.lang.Specification
import spock.lang.Unroll

class Dhis2OAuthServiceSpec extends Specification implements ServiceUnitTest<Dhis2OAuthService> {

    @Unroll
    void "buildAuthorizeUrl builds an exact URL when scopes=#scopes and state=#state"() {
        given:
        service.grailsApplication = Stub(grails.core.GrailsApplication) {
            getConfig() >> configWith(
                authorizeUrl: 'https://dhis2.example.com/uaa/oauth/authorize',
                clientId: 'my client',
                redirectUri: 'https://ob.example.com/oauth/dhis2/callback',
                scopes: scopes,
            )
        }

        when:
        String url = service.buildAuthorizeUrl(state)

        then:
        url == expected

        where:
        scopes      | state             | expected
        'ALL'       | 'test-state-123'  | 'https://dhis2.example.com/uaa/oauth/authorize?response_type=code&client_id=my+client&redirect_uri=https%3A%2F%2Fob.example.com%2Foauth%2Fdhis2%2Fcallback&scope=ALL&state=test-state-123'
        'read user' | 'state with sp'   | 'https://dhis2.example.com/uaa/oauth/authorize?response_type=code&client_id=my+client&redirect_uri=https%3A%2F%2Fob.example.com%2Foauth%2Fdhis2%2Fcallback&scope=read+user&state=state+with+sp'
        null        | 'abc'             | 'https://dhis2.example.com/uaa/oauth/authorize?response_type=code&client_id=my+client&redirect_uri=https%3A%2F%2Fob.example.com%2Foauth%2Fdhis2%2Fcallback&scope=ALL&state=abc'
    }

    private static ConfigObject configWith(Map values) {
        def cfg = new ConfigObject()
        cfg.openboxes = new ConfigObject()
        cfg.openboxes.dhis2 = new ConfigObject()
        cfg.openboxes.dhis2.oauth = new ConfigObject()
        values.each { k, v -> cfg.openboxes.dhis2.oauth[k] = v }
        cfg
    }
}
