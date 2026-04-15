package org.pih.warehouse.custom.stocktransferdocuments

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import grails.validation.ValidationException
import org.pih.warehouse.common.base.IntegrationSpec
import org.pih.warehouse.core.ActivityCode
import org.pih.warehouse.core.Location
import org.pih.warehouse.order.Order
import org.pih.warehouse.order.OrderType
import org.pih.warehouse.order.OrderTypeCode
import org.pih.warehouse.stockTransfer.StockTransferService

@Integration
@Rollback
class CustomStockTransferDocumentServiceIntegrationSpec extends IntegrationSpec {

    CustomStockTransferDocumentService customStockTransferDocumentService
    StockTransferService stockTransferService

    def "custom service bean is available in the application context"() {
        expect:
        customStockTransferDocumentService != null
    }

    def "StockTransferService has the customStockTransferDocumentService dependency injected"() {
        expect:
        stockTransferService.customStockTransferDocumentService != null
        stockTransferService.customStockTransferDocumentService.is(customStockTransferDocumentService)
    }

    def "validateForCompletion throws IllegalArgumentException on null order"() {
        when:
        customStockTransferDocumentService.validateForCompletion(null)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == CustomStockTransferDocumentService.NULL_ORDER_ERROR
    }

    def "validateForCompletion is a no-op when enforcement is disabled"() {
        given:
        Order order = buildTransferOrder(false)

        when:
        customStockTransferDocumentService.validateForCompletion(order)

        then:
        noExceptionThrown()
    }

    def "validateForCompletion blocks completion when enforcement is enabled and no documents attached"() {
        given:
        Order order = buildTransferOrder(true)

        when:
        customStockTransferDocumentService.validateForCompletion(order)

        then:
        ValidationException ex = thrown()
        ex.message.startsWith(CustomStockTransferDocumentService.DOCUMENT_REQUIRED_ERROR)
    }

    /**
     * Fetches the seeded "Main Warehouse" (id=1), toggles REQUIRE_TRANSFER_DOCUMENT on its
     * supportedActivities, and returns a transient Order wired to it. The change is reverted
     * by the surrounding @Rollback transaction, so neighbouring specs are unaffected.
     */
    private Order buildTransferOrder(boolean enforced) {
        Location origin = Location.get('1')
        assert origin != null, 'seeded Main Warehouse (id=1) is required for this test'

        Set<String> originalActivities = origin.supportedActivities == null
            ? ([] as Set<String>)
            : new HashSet<>(origin.supportedActivities)
        Set<String> nextActivities = new HashSet<>(originalActivities)
        if (enforced) {
            nextActivities << ActivityCode.REQUIRE_TRANSFER_DOCUMENT.id
        } else {
            nextActivities.remove(ActivityCode.REQUIRE_TRANSFER_DOCUMENT.id)
        }
        origin.supportedActivities = nextActivities
        origin.save(flush: true, failOnError: true)

        return new Order(
            origin: origin,
            orderType: OrderType.get(OrderTypeCode.TRANSFER_ORDER.name()),
        )
    }
}
