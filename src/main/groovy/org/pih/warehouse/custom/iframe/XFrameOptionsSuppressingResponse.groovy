package org.pih.warehouse.custom.iframe

import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpServletResponseWrapper

/**
 * Drops any attempt to set `X-Frame-Options` on the wrapped response, so the
 * CSP `frame-ancestors` directive emitted by {@link CspFrameAncestorsFilter}
 * remains the single source of truth for framing policy.
 */
class XFrameOptionsSuppressingResponse extends HttpServletResponseWrapper {

    static final String X_FRAME_OPTIONS = 'X-Frame-Options'

    XFrameOptionsSuppressingResponse(HttpServletResponse response) {
        super(response)
    }

    @Override
    void setHeader(String name, String value) {
        if (!X_FRAME_OPTIONS.equalsIgnoreCase(name)) {
            super.setHeader(name, value)
        }
    }

    @Override
    void addHeader(String name, String value) {
        if (!X_FRAME_OPTIONS.equalsIgnoreCase(name)) {
            super.addHeader(name, value)
        }
    }
}
