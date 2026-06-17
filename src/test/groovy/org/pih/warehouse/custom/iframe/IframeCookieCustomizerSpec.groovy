package org.pih.warehouse.custom.iframe

import org.apache.catalina.core.StandardContext
import org.apache.tomcat.util.http.Rfc6265CookieProcessor
import org.grails.testing.GrailsUnitTest
import org.springframework.boot.context.embedded.ConfigurableEmbeddedServletContainer
import org.springframework.boot.context.embedded.tomcat.TomcatContextCustomizer
import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainerFactory

import spock.lang.Specification

class IframeCookieCustomizerSpec extends Specification implements GrailsUnitTest {

    private IframeCookieCustomizer customizerWithAncestors(List<String> ancestors) {
        config.openboxes.custom.iframe.frameAncestors = ancestors
        new IframeCookieCustomizer(grailsApplication: grailsApplication)
    }

    void "installs an Rfc6265CookieProcessor with SameSite=None when embedding is enabled"() {
        given:
        TomcatEmbeddedServletContainerFactory factory = new TomcatEmbeddedServletContainerFactory()
        StandardContext context = new StandardContext()

        when:
        customizerWithAncestors(['https://dhis2.example.org']).customize(factory)
        factory.tomcatContextCustomizers.each { TomcatContextCustomizer it -> it.customize(context) }

        then:
        factory.tomcatContextCustomizers.size() == 1
        context.cookieProcessor instanceof Rfc6265CookieProcessor
        ((Rfc6265CookieProcessor) context.cookieProcessor).sameSiteCookies.value == 'None'
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
