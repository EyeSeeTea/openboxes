package org.pih.warehouse.custom.consumptionDemand

import grails.gorm.transactions.Rollback
import groovy.time.TimeCategory

import org.pih.warehouse.DateUtil
import org.pih.warehouse.common.base.IntegrationSpec
import org.pih.warehouse.core.Constants
import org.pih.warehouse.core.Location
import org.pih.warehouse.core.LocationType
import org.pih.warehouse.core.Organization
import org.pih.warehouse.core.PartyType
import org.pih.warehouse.core.User
import org.pih.warehouse.inventory.Inventory
import org.pih.warehouse.inventory.InventoryItem
import org.pih.warehouse.inventory.Transaction
import org.pih.warehouse.inventory.TransactionEntry
import org.pih.warehouse.inventory.TransactionType
import org.pih.warehouse.product.Category
import org.pih.warehouse.product.Product
import org.pih.warehouse.product.ProductType
import org.pih.warehouse.product.ProductTypeCode

/**
 * Verifies AMC = SUM(consumption qty in window) / windowDays * 30, scoped to a
 * single location + product, using Consumption transactions (type id 2) only.
 *
 * The service reads via groovy.sql.Sql on the injected (transaction-aware)
 * dataSource, so rows seeded inside this @Rollback transaction are visible to
 * the query and rolled back afterwards.
 */
@Rollback
class ConsumptionDemandServiceIntegrationSpec extends IntegrationSpec {

    ConsumptionDemandService consumptionDemandService
    def grailsApplication

    def setup() {
        // The service short-circuits to 0 when the feature flag is off, so enable
        // it for the computation tests (restored in cleanup to avoid leaking into
        // other integration specs sharing the application context).
        grailsApplication.config.openboxes.custom.consumption.showAmcInRequisition = true
    }

    def cleanup() {
        grailsApplication.config.openboxes.custom.consumption.showAmcInRequisition = false
    }

    // --- window bounds mirror ConsumptionDemandService / ForecastingService ---

    private int amcPeriod() {
        return (grailsApplication.config.openboxes.custom.consumption.amcPeriod
                ?: grailsApplication.config.openboxes.forecasting.demandPeriod
                ?: 365) as Integer
    }

    private Date windowEnd() {
        return DateUtil.getDateRange(new Date(), -1).endDate
    }

    private int windowDays() {
        Date start
        use(TimeCategory) {
            start = DateUtil.getDateRange(new Date(), 0).startDate - amcPeriod().days
        }
        return windowEnd() - start
    }

    // --- minimal persistent graph (mirrors DbHelper's proven save sequence) ---

    private Location createLocationWithInventory(String name) {
        Organization organization = new Organization(
                code: name.take(4),
                name: "${name} Org",
                partyType: PartyType.findByCode(Constants.DEFAULT_ORGANIZATION_CODE),
        ).save(failOnError: true, flush: true)
        Location location = new Location(
                name: name,
                locationType: LocationType.get(Constants.WAREHOUSE_LOCATION_TYPE_ID),
                organization: organization,
        ).save(failOnError: true, flush: true)
        Inventory inventory = new Inventory(warehouse: location).save(failOnError: true, flush: true)
        location.inventory = inventory
        location.save(failOnError: true, flush: true)
        return location
    }

    private Product createProduct(String name) {
        Category category = Category.findByName('AmcMedicines')
                ?: new Category(name: 'AmcMedicines').save(failOnError: true, flush: true)
        ProductType productType = ProductType.findByName('AmcDefault')
                ?: new ProductType(name: 'AmcDefault', productTypeCode: ProductTypeCode.GOOD)
                        .save(failOnError: true, flush: true)
        return new Product(name: name, productCode: name, category: category, productType: productType)
                .save(failOnError: true, flush: true)
    }

    private void recordConsumption(Location location, Product product, int quantity, Date transactionDate) {
        String lotNumber = "${product.productCode}-LOT"
        InventoryItem inventoryItem = InventoryItem.findByProductAndLotNumber(product, lotNumber)
                ?: new InventoryItem(product: product, lotNumber: lotNumber, expirationDate: new Date() + 365)
                        .save(failOnError: true, flush: true)
        Transaction transaction = new Transaction(
                inventory: location.inventory,
                transactionType: TransactionType.get(Constants.CONSUMPTION_TRANSACTION_TYPE_ID),
                transactionDate: transactionDate,
                createdBy: User.get(2),
        )
        transaction.addToTransactionEntries(new TransactionEntry(quantity: quantity, inventoryItem: inventoryItem))
        transaction.save(failOnError: true, flush: true)
    }

    def "getMonthlyConsumption sums only in-window, same-product, same-location consumption"() {
        given: "two consumption transactions inside the window for the target product/location"
        String suffix = System.currentTimeMillis()
        Location location = createLocationWithInventory("AMC In-Window ${suffix}")
        Product product = createProduct("AMC-PROD-${suffix}")
        Date insideWindow = windowEnd() - 5
        recordConsumption(location, product, 30, insideWindow)
        recordConsumption(location, product, 60, insideWindow)

        and: "consumption AFTER the window end (must be excluded)"
        recordConsumption(location, product, 999, new Date() + 5)

        and: "consumption for a different product and a different location (must be excluded)"
        Location otherLocation = createLocationWithInventory("AMC Other ${suffix}")
        Product otherProduct = createProduct("AMC-OTHER-${suffix}")
        recordConsumption(otherLocation, product, 500, insideWindow)
        recordConsumption(location, otherProduct, 700, insideWindow)

        when:
        BigDecimal amc = consumptionDemandService.getMonthlyConsumption(location, product)

        then: "only 30 + 60 = 90 contributes: 90 / windowDays * 30"
        BigDecimal expected = (90 as BigDecimal) / windowDays() * 30
        (amc - expected).abs() < 0.0001
    }

    def "getMonthlyConsumption returns 0 when there is no consumption in the window"() {
        given:
        String suffix = System.currentTimeMillis()
        Location location = createLocationWithInventory("AMC Empty ${suffix}")
        Product product = createProduct("AMC-EMPTY-${suffix}")

        and: "only an out-of-window consumption transaction exists"
        recordConsumption(location, product, 123, new Date() + 10)

        expect:
        consumptionDemandService.getMonthlyConsumption(location, product) == 0
    }

    def "getMonthlyConsumption returns 0 without querying when showAmcInRequisition is off"() {
        given: "the feature flag is disabled"
        grailsApplication.config.openboxes.custom.consumption.showAmcInRequisition = false

        and: "in-window consumption exists that would otherwise contribute"
        String suffix = System.currentTimeMillis()
        Location location = createLocationWithInventory("AMC Flag Off ${suffix}")
        Product product = createProduct("AMC-FLAGOFF-${suffix}")
        recordConsumption(location, product, 90, windowEnd() - 5)

        expect: "the gate short-circuits to 0 regardless of the seeded data"
        consumptionDemandService.getMonthlyConsumption(location, product) == 0
    }
}
