package spring

import org.pih.warehouse.product.ProductValidator
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.core.Ordered

import org.pih.warehouse.monitoring.SentryGrailsTracingFilter
import org.pih.warehouse.custom.iframe.CspFrameAncestorsFilter
import org.pih.warehouse.custom.iframe.IframeCookieCustomizer

// This is where we can register spring-specific beans using the Spring Bean DSL.
// Regular beans that conform to Grails conventions don't need to be registered here.
// https://docs.grails.org/latest/guide/spring.html
beans = {

    // Override Sentry's default tracing filters since Grails behaves slightly differently than SpringBoot.
    sentryTracingFilter(SentryGrailsTracingFilter)
    sentryTracingFilterRegistration(FilterRegistrationBean) {
        filter = sentryTracingFilter
        urlPatterns = ['/*']
        order = Ordered.HIGHEST_PRECEDENCE + 1
    }
    productValidator(ProductValidator)

    // Custom: DHIS2 iframe embedding — emit CSP frame-ancestors, suppress X-Frame-Options.
    cspFrameAncestorsFilter(CspFrameAncestorsFilter) {
        grailsApplication = ref('grailsApplication')
    }
    cspFrameAncestorsFilterRegistration(FilterRegistrationBean) {
        filter = cspFrameAncestorsFilter
        urlPatterns = ['/*']
        order = Ordered.HIGHEST_PRECEDENCE + 2
    }

    // Custom: DHIS2 iframe embedding — SameSite=None session cookie when embedding is enabled.
    // grailsApplication must be wired explicitly: resources.groovy DSL beans are not
    // autowired by name, and this customizer runs at container-creation time (very early).
    iframeCookieCustomizer(IframeCookieCustomizer) {
        grailsApplication = ref('grailsApplication')
    }
}
