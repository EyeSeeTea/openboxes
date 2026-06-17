package org.pih.warehouse.custom.iframe

import grails.core.GrailsApplication

import javax.servlet.Filter
import javax.servlet.FilterChain
import javax.servlet.FilterConfig
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletResponse

/**
 * When iframe embedding is enabled (a non-empty DHIS2 origin allow-list), emits a
 * Content-Security-Policy `frame-ancestors` directive scoped to that list and
 * suppresses any X-Frame-Options so CSP is the single source of truth for framing.
 * When embedding is disabled (the default), the filter is a pass-through and leaves
 * OB responses exactly as upstream — no header is added.
 */
class CspFrameAncestorsFilter implements Filter {

    static final String CSP_HEADER = 'Content-Security-Policy'
    static final String FRAME_ANCESTORS_SELF = "frame-ancestors 'self'"

    GrailsApplication grailsApplication

    void init(FilterConfig filterConfig) { }

    void destroy() { }

    void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
        List<String> origins = frameAncestors()
        if (!origins) {
            // Embedding disabled: do not touch the response — preserve upstream default behaviour.
            chain.doFilter(request, response)
            return
        }
        HttpServletResponse httpResponse = response as HttpServletResponse
        httpResponse.setHeader(CSP_HEADER, frameAncestorsDirective(origins))
        chain.doFilter(request, new XFrameOptionsSuppressingResponse(httpResponse))
    }

    List<String> frameAncestors() {
        (grailsApplication.config.openboxes.custom.iframe.frameAncestors ?: []) as List
    }

    String frameAncestorsDirective(List<String> origins) {
        origins ? "${FRAME_ANCESTORS_SELF} ${origins.join(' ')}" : FRAME_ANCESTORS_SELF
    }
}
