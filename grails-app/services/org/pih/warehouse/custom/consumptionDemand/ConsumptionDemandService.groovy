package org.pih.warehouse.custom.consumptionDemand

import grails.core.GrailsApplication
import groovy.sql.Sql
import groovy.time.TimeCategory

import org.pih.warehouse.DateUtil
import org.pih.warehouse.core.Constants
import org.pih.warehouse.core.Location
import org.pih.warehouse.product.Product

import javax.sql.DataSource

/**
 * Computes Average Monthly Consumption (AMC) for a product at a location from
 * Consumption transactions only (TransactionType id "2",
 * Constants.CONSUMPTION_TRANSACTION_TYPE_ID).
 *
 * The formula and window bounds intentionally mirror the Consumption Report
 * (ConsumptionController.getMonthlyQuantity, line 589: totalConsumption /
 * numberOfDays * 30) so the AMC column on the requisition screens reconciles
 * with the report for the same product/location/period.
 *
 * Isolated custom service for the amc-in-requisition change. Injected into the
 * upstream StockMovementService to surface an AMC column beside Demand on the
 * requisition create (Add Items) and edit (fulfiller) screens.
 */
class ConsumptionDemandService {

    // Pure read via raw SQL — no GORM writes, so no Hibernate transaction needed.
    static transactional = false

    DataSource dataSource
    GrailsApplication grailsApplication

    /**
     * AMC = SUM(consumption quantity in window) / windowDays * 30, scoped to the
     * given location + product, using Consumption transactions only.
     *
     * @return the AMC value, or 0 when there is no consumption in the window
     *         (never null, never throws on missing data). Returns 0 without
     *         querying when the showAmcInRequisition flag is off, so deployments
     *         that don't surface the column pay no query cost (mirrors how
     *         ForecastingService.getDemand short-circuits on forecasting.enabled).
     */
    BigDecimal getMonthlyConsumption(Location location, Product product) {
        boolean showAmcInRequisition = grailsApplication.config.openboxes.custom.consumption.showAmcInRequisition ?: false
        if (!showAmcInRequisition || !location || !product) {
            return BigDecimal.ZERO
        }

        // amcPeriod aligns with demand by default; tunable independently per deployment.
        Integer amcPeriod = (grailsApplication.config.openboxes.custom.consumption.amcPeriod
                ?: grailsApplication.config.openboxes.forecasting.demandPeriod
                ?: 365) as Integer

        // Window mirrors ForecastingService.getDemand: first day of current month
        // − amcPeriod → end of previous month.
        Date startDate
        use(TimeCategory) {
            startDate = DateUtil.getDateRange(new Date(), 0).startDate - amcPeriod.days
        }
        Date endDate = DateUtil.getDateRange(new Date(), -1).endDate

        // windowDays computed exactly as the Consumption Report's numberOfDays
        // (toDate − fromDate, ConsumptionController:530) so the /windowDays*30
        // divisor reconciles with getMonthlyQuantity.
        Integer windowDays = (endDate - startDate)
        if (windowDays <= 0) {
            return BigDecimal.ZERO
        }

        // Raw SQL: mirrors the Consumption Report's debit fetch
        // (InventoryService.getDebitsBetweenDates) but scoped to Consumption type
        // only (transaction_type_id = '2'). Consumption transactions are scoped by
        // transaction.inventory (location.inventory_id = transaction.inventory_id),
        // matching the report's join — no `confirmed` filter (Resolved OQ#2).
        String query = """
            SELECT SUM(te.quantity) AS total_consumption
            FROM   transaction_entry te
            JOIN   transaction t     ON t.id = te.transaction_id
            JOIN   location l        ON l.inventory_id = t.inventory_id
            JOIN   inventory_item ii ON ii.id = te.inventory_item_id
            WHERE  t.transaction_type_id = :transactionTypeId
              AND  ii.product_id          = :productId
              AND  l.id                   = :locationId
              AND  t.transaction_date BETWEEN :startDate AND :endDate
        """

        Map params = [
                transactionTypeId: Constants.CONSUMPTION_TRANSACTION_TYPE_ID,
                productId        : product.id,
                locationId       : location.id,
                startDate        : startDate,
                endDate          : endDate,
        ]

        Sql sql = new Sql(dataSource)
        try {
            def row = sql.firstRow(query, params)
            BigDecimal totalConsumption = (row?.total_consumption ?: 0) as BigDecimal
            return totalConsumption / windowDays * 30
        } finally {
            sql.close()
        }
    }
}
