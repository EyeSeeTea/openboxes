package org.pih.warehouse.custom.damagedAdjustments

import grails.core.GrailsApplication
import io.restassured.RestAssured
import io.restassured.response.Response
import org.springframework.beans.factory.annotation.Autowired

import org.pih.warehouse.api.spec.base.ApiSpec

class DamagedAdjustmentInterceptorSpec extends ApiSpec {

    @Autowired
    GrailsApplication grailsApplication

    private static final String CREATE_DAMAGED_PATH = "/inventory/createDamaged"
    private static final String FORBIDDEN_PATH = "/errors/handleForbidden"

    def originalEnabled

    def setup() {
        // Capture pre-test value so cleanup restores it instead of forcing a default.
        originalEnabled = grailsApplication.config.openboxes.custom.adjustments.damaged.enabled
        grailsApplication.config.openboxes.custom.adjustments.damaged.enabled = false
    }

    def cleanup() {
        grailsApplication.config.openboxes.custom.adjustments.damaged.enabled = originalEnabled
    }

    def "flag=false: GET /inventory/createDamaged redirects to errors/handleForbidden"() {
        given: "the flag is disabled (set in setup)"
        assert grailsApplication.config.openboxes.custom.adjustments.damaged.enabled == false

        when: "an authenticated user requests the createDamaged action"
        Response response = requestCreateDamaged(product.id)

        then: "the interceptor redirects with 302 to the forbidden page"
        response.statusCode() == 302
        response.header("Location").endsWith(FORBIDDEN_PATH)
    }

    def "flag=true: GET /inventory/createDamaged is not redirected to forbidden"() {
        given: "the flag is enabled"
        grailsApplication.config.openboxes.custom.adjustments.damaged.enabled = true

        when: "an authenticated user requests the createDamaged action"
        Response response = requestCreateDamaged(product.id)

        then: "the interceptor passes through and the controller renders its view"
        response.statusCode() == 200
    }

    private Response requestCreateDamaged(String productId) {
        return RestAssured
            .given()
                .cookie(cookie)
                .redirects().follow(false)
            .get(createDamagedUrl(productId))
    }

    private String createDamagedUrl(String productId) {
        // RestAssured.basePath is set to "/openboxes/api" by ApiSpec, so we
        // build the full path manually for this non-API controller.
        return "http://localhost:${serverPort}/openboxes${CREATE_DAMAGED_PATH}?product.id=${productId}"
    }
}
