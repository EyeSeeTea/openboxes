package org.pih.warehouse.custom.iframe

import org.grails.testing.GrailsUnitTest
import org.springframework.boot.context.embedded.ConfigurableEmbeddedServletContainer
import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainerFactory

import spock.lang.Specification

class IframeCookieCustomizerSpec extends Specification implements GrailsUnitTest {

    private IframeCookieCustomizer customizerWithAncestors(List<String> ancestors) {
        config.openboxes.custom.iframe.frameAncestors = ancestors
        new IframeCookieCustomizer(grailsApplication: grailsApplication)
    }

    void "registers a Tomcat context customizer when embedding is enabled"() {
        given:
        TomcatEmbeddedServletContainerFactory factory = new TomcatEmbeddedServletContainerFactory()

        when:
        customizerWithAncestors(['https://dhis2.example.org']).customize(factory)

        then:
        factory.tomcatContextCustomizers.size() == 1
    }

    void "adds no customizer when embedding is disabled"() {
        given:
        TomcatEmbeddedServletContainerFactory factory = new TomcatEmbeddedServletContainerFactory()

        when:
        customizerWithAncestors([]).customize(factory)

        then:
        factory.tomcatContextCustomizers.isEmpty()
    }

    void "is a no-op for a non-Tomcat container"() {
        given:
        ConfigurableEmbeddedServletContainer container = Mock()

        when:
        customizerWithAncestors(['https://dhis2.example.org']).customize(container)

        then:
        0 * container._
    }
}
