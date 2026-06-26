package org.pih.warehouse.custom.consumptionDemand

import grails.testing.gorm.DataTest
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification

import org.pih.warehouse.core.Location
import org.pih.warehouse.product.Product

class ConsumptionDemandControllerSpec extends Specification
        implements ControllerUnitTest<ConsumptionDemandController>, DataTest {

    def setupSpec() {
        mockDomains(Product, Location)
    }

    def "getMonthlyConsumption renders the AMC value from the service"() {
        given:
        controller.consumptionDemandService = Mock(ConsumptionDemandService) {
            1 * getMonthlyConsumption(_, _) >> 9.9G
        }

        when:
        params.productId = 'p1'
        params.locationId = 'l1'
        controller.getMonthlyConsumption()

        then:
        response.status == 200
        response.json.amc == 9.9
    }

    def "getMonthlyConsumption renders 0 (not blank) when the service returns zero"() {
        given:
        controller.consumptionDemandService = Mock(ConsumptionDemandService) {
            1 * getMonthlyConsumption(_, _) >> 0G
        }

        when:
        params.productId = 'p1'
        params.locationId = 'l1'
        controller.getMonthlyConsumption()

        then:
        response.status == 200
        response.json.amc == 0
    }
}
