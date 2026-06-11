package org.pih.warehouse.custom.iframe

import grails.core.GrailsApplication

import javax.servlet.Filter
import javax.servlet.FilterChain
import javax.servlet.FilterConfig
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletResponse

/**
 * Emits a Content-Security-Policy `frame-ancestors` directive scoped to the
 * configured DHIS2 origin allow-list, making OB the single source of truth for
 * framing. Defaults to `frame-ancestors 'self'` when the allow-list is empty,
 * which is browser-equivalent to `X-Frame-Options: SAMEORIGIN`.
 */
class CspFrameAncestorsFilter implements Filter {

    static final String CSP_HEADER = 'Content-Security-Policy'
    static final String FRAME_ANCESTORS_SELF = "frame-ancestors 'self'"

    GrailsApplication grailsApplication

    void init(FilterConfig filterConfig) { }

    void destroy() { }

    void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
        HttpServletResponse httpResponse = response as HttpServletResponse
        httpResponse.setHeader(CSP_HEADER, frameAncestorsDirective())
        // Suppress any X-Frame-Options set downstream so it cannot contradict the CSP directive.
        chain.doFilter(request, new XFrameOptionsSuppressingResponse(httpResponse))
    }

    String frameAncestorsDirective() {
        List<String> origins = (grailsApplication.config.openboxes.custom.iframe.frameAncestors ?: []) as List
        origins ? "${FRAME_ANCESTORS_SELF} ${origins.join(' ')}" : FRAME_ANCESTORS_SELF
    }
}
