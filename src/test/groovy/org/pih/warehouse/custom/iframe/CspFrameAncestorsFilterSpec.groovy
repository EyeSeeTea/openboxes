package org.pih.warehouse.custom.iframe

import org.grails.testing.GrailsUnitTest

import javax.servlet.FilterChain
import javax.servlet.ServletRequest
import javax.servlet.http.HttpServletResponse

import spock.lang.Specification

class CspFrameAncestorsFilterSpec extends Specification implements GrailsUnitTest {

    private static final String CSP = 'Content-Security-Policy'

    ServletRequest request = Mock()
    HttpServletResponse response = Mock()
    FilterChain chain = Mock()

    private CspFrameAncestorsFilter filterWithAncestors(List<String> ancestors) {
        config.openboxes.custom.iframe.frameAncestors = ancestors
        new CspFrameAncestorsFilter(grailsApplication: grailsApplication)
    }

    void "embedding disabled (empty allow-list) leaves the response untouched"() {
        when:
        filterWithAncestors([]).doFilter(request, response, chain)

        then:
        0 * response.setHeader(CSP, _)
        1 * chain.doFilter(request, response)
    }

    void "configured origins emit the directive and wrap the response"() {
        when:
        filterWithAncestors(['https://dhis2.example.org', 'https://dhis2.other.org'])
            .doFilter(request, response, chain)

        then:
        1 * response.setHeader(CSP, "frame-ancestors 'self' https://dhis2.example.org https://dhis2.other.org")
        1 * chain.doFilter(request, { it instanceof XFrameOptionsSuppressingResponse })
    }

    void "directive builder reflects the configured allow-list"() {
        expect:
        filterWithAncestors(ancestors).frameAncestorsDirective(ancestors) == expected

        where:
        ancestors                        || expected
        ['https://dhis2.example.org']    || "frame-ancestors 'self' https://dhis2.example.org"
    }
}
