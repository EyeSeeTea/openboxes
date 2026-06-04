package org.pih.warehouse.custom.dhis2auth

import grails.testing.services.ServiceUnitTest
import spock.lang.Specification
import spock.lang.Unroll

class Dhis2OAuthServiceSpec extends Specification implements ServiceUnitTest<Dhis2OAuthService> {

    @Unroll
    void "buildAuthorizeUrl builds an exact URL when scopes=#scopes and state=#state"() {
        given:
        grailsApplication.config.openboxes.custom.dhis2.oauth.authorizeUrl = 'https://dhis2.example.com/uaa/oauth/authorize'
        grailsApplication.config.openboxes.custom.dhis2.oauth.clientId = 'my client'
        grailsApplication.config.openboxes.custom.dhis2.oauth.redirectUri = 'https://ob.example.com/oauth/dhis2/callback'
        grailsApplication.config.openboxes.custom.dhis2.oauth.scopes = scopes

        when:
        String url = service.buildAuthorizeUrl(state)

        then:
        url == expected

        where:
        scopes      | state            | expected
        'ALL'       | 'test-state-123' | 'https://dhis2.example.com/uaa/oauth/authorize?response_type=code&client_id=my+client&redirect_uri=https%3A%2F%2Fob.example.com%2Foauth%2Fdhis2%2Fcallback&scope=ALL&state=test-state-123'
        'read user' | 'state with sp'  | 'https://dhis2.example.com/uaa/oauth/authorize?response_type=code&client_id=my+client&redirect_uri=https%3A%2F%2Fob.example.com%2Foauth%2Fdhis2%2Fcallback&scope=read+user&state=state+with+sp'
        null        | 'abc'            | 'https://dhis2.example.com/uaa/oauth/authorize?response_type=code&client_id=my+client&redirect_uri=https%3A%2F%2Fob.example.com%2Foauth%2Fdhis2%2Fcallback&scope=ALL&state=abc'
    }
}
