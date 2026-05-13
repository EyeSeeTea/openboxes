package spring

import org.pih.warehouse.custom.dhis2auth.Dhis2OAuthClient
import org.pih.warehouse.product.ProductValidator
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.core.Ordered

import org.pih.warehouse.monitoring.SentryGrailsTracingFilter

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

    // Dhis2OAuthClient doesn't end in "Service" so Grails won't auto-register it as a bean
    dhis2OAuthClient(Dhis2OAuthClient) { bean ->
        bean.autowire = 'byName'
    }
}
