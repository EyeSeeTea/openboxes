package org.pih.warehouse.custom.iframe

import javax.servlet.http.HttpServletResponse

import spock.lang.Specification

class XFrameOptionsSuppressingResponseSpec extends Specification {

    HttpServletResponse delegate = Mock()
    XFrameOptionsSuppressingResponse wrapper = new XFrameOptionsSuppressingResponse(delegate)

    void "setHeader drops X-Frame-Options (case-insensitive) and passes everything else through"() {
        when:
        wrapper.setHeader('X-Frame-Options', 'DENY')
        wrapper.setHeader('x-frame-options', 'SAMEORIGIN')
        wrapper.setHeader('Content-Type', 'text/html')

        then:
        0 * delegate.setHeader('X-Frame-Options', _)
        0 * delegate.setHeader('x-frame-options', _)
        1 * delegate.setHeader('Content-Type', 'text/html')
    }

    void "addHeader drops X-Frame-Options and passes everything else through"() {
        when:
        wrapper.addHeader('X-Frame-Options', 'DENY')
        wrapper.addHeader('Cache-Control', 'no-store')

        then:
        0 * delegate.addHeader('X-Frame-Options', _)
        1 * delegate.addHeader('Cache-Control', 'no-store')
    }
}
