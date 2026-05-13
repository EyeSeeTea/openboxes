package org.pih.warehouse.custom.dhis2auth

import grails.testing.services.ServiceUnitTest
import spock.lang.Specification

class Dhis2OAuthClientSpec extends Specification implements ServiceUnitTest<Dhis2OAuthClient> {

    void "buildAuthorizeUrl encodes all query parameters"() {
        given:
        service.grailsApplication = Stub(grails.core.GrailsApplication) {
            getConfig() >> configWith(
                authorizeUrl: 'https://dhis2.example.com/uaa/oauth/authorize',
                clientId: 'my client',
                redirectUri: 'https://ob.example.com/oauth/dhis2/callback',
                scopes: 'ALL',
            )
        }

        when:
        String url = service.buildAuthorizeUrl('test-state-123')

        then:
        url.contains('response_type=code')
        url.contains('client_id=my+client')
        url.contains('state=test-state-123')
        url.contains('redirect_uri=https%3A%2F%2Fob.example.com')
        url.startsWith('https://dhis2.example.com/uaa/oauth/authorize')
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
