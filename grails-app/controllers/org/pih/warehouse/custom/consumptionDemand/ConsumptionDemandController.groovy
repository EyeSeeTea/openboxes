package org.pih.warehouse.custom.consumptionDemand

import grails.converters.JSON

import org.pih.warehouse.core.Location
import org.pih.warehouse.product.Product

/**
 * Thin HTTP wrapper over ConsumptionDemandService so the AMC value can be fetched
 * on product-select in the requisition Add Items page, at parity with how Demand
 * is fetched (see openspec amc-in-requisition, Decision 7). Reachable via the
 * default URL mapping at /consumptionDemand/getMonthlyConsumption — no UrlMappings
 * edit required. The service short-circuits to 0 when showAmcInRequisition is off,
 * so this endpoint costs nothing for deployments with the column disabled.
 */
class ConsumptionDemandController {

    def consumptionDemandService

    def getMonthlyConsumption() {
        Product product = Product.get(params.productId)
        Location location = Location.get(params.locationId)
        render([amc: consumptionDemandService.getMonthlyConsumption(location, product)] as JSON)
    }
}
