package org.pih.warehouse.custom.iframe

import grails.core.GrailsApplication
import groovy.util.logging.Slf4j
import org.apache.tomcat.util.http.Rfc6265CookieProcessor
import org.springframework.boot.context.embedded.ConfigurableEmbeddedServletContainer
import org.springframework.boot.context.embedded.EmbeddedServletContainerCustomizer
import org.springframework.boot.context.embedded.tomcat.TomcatContextCustomizer
import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainerFactory

/**
 * When iframe embedding is enabled (a non-empty DHIS2 origin allow-list), installs
 * Tomcat's Rfc6265CookieProcessor with SameSite=None on OB's own embedded Tomcat,
 * so the session cookie is sent on requests made from inside a cross-site DHIS2
 * iframe. SameSite=None requires Secure, which OB sets once it knows it is behind
 * TLS (server.use-forward-headers — see the forwarded-proto config).
 *
 * Scoped: when embedding is disabled (the default) this is a no-op and OB keeps its
 * default cookie processor, so non-embedding deployments are unchanged.
 */
@Slf4j
class IframeCookieCustomizer implements EmbeddedServletContainerCustomizer {

    static final String SAME_SITE_NONE = 'None'

    GrailsApplication grailsApplication

    void customize(ConfigurableEmbeddedServletContainer container) {
        if (!iframeEmbeddingEnabled || !(container instanceof TomcatEmbeddedServletContainerFactory)) {
            return
        }
        log.warn("iframe embedding enabled: session cookie will be SameSite=None. " +
            "OB MUST be served over TLS (set server.use-forward-headers=true behind a TLS-terminating " +
            "proxy) or browsers will reject the cookie and login will fail.")
        ((TomcatEmbeddedServletContainerFactory) container).addContextCustomizers({ context ->
            Rfc6265CookieProcessor processor = new Rfc6265CookieProcessor()
            processor.sameSiteCookies = SAME_SITE_NONE
            context.cookieProcessor = processor
        } as TomcatContextCustomizer)
    }

    private boolean isIframeEmbeddingEnabled() {
        ((grailsApplication.config.openboxes.custom.iframe.frameAncestors ?: []) as List) as boolean
    }
}
