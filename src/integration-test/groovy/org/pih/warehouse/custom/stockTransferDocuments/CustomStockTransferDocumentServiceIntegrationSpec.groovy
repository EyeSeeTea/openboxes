package org.pih.warehouse.custom.stockTransferDocuments

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import grails.validation.ValidationException
import org.pih.warehouse.common.base.IntegrationSpec
import org.pih.warehouse.core.ActivityCode
import org.pih.warehouse.core.Location
import org.pih.warehouse.order.Order
import org.pih.warehouse.order.OrderItem
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

    def "validateForCompletion is a no-op when neither side enforces"() {
        given:
        Order order = buildTransferOrder(false, false)

        when:
        customStockTransferDocumentService.validateForCompletion(order)

        then:
        noExceptionThrown()
    }

    def "validateForCompletion blocks completion when origin enforces REQUIRE_TRANSFER_OUT_DOCUMENT"() {
        given:
        Order order = buildTransferOrder(true, false)

        when:
        customStockTransferDocumentService.validateForCompletion(order)

        then:
        ValidationException ex = thrown()
        ex.message.startsWith(CustomStockTransferDocumentService.DOCUMENT_REQUIRED_ERROR)
    }

    def "validateForCompletion blocks completion when destination enforces REQUIRE_TRANSFER_IN_DOCUMENT"() {
        given:
        Order order = buildTransferOrder(false, true)

        when:
        customStockTransferDocumentService.validateForCompletion(order)

        then:
        ValidationException ex = thrown()
        ex.message.startsWith(CustomStockTransferDocumentService.DOCUMENT_REQUIRED_ERROR)
    }

    def "validateForCompletion blocks completion when an origin BIN enforces REQUIRE_TRANSFER_OUT_DOCUMENT"() {
        given:
        Order order = buildTransferOrderWithBin(true, false)

        when:
        customStockTransferDocumentService.validateForCompletion(order)

        then:
        ValidationException ex = thrown()
        ex.message.startsWith(CustomStockTransferDocumentService.DOCUMENT_REQUIRED_ERROR)
    }

    def "validateForCompletion blocks completion when a destination BIN enforces REQUIRE_TRANSFER_IN_DOCUMENT"() {
        given:
        Order order = buildTransferOrderWithBin(false, true)

        when:
        customStockTransferDocumentService.validateForCompletion(order)

        then:
        ValidationException ex = thrown()
        ex.message.startsWith(CustomStockTransferDocumentService.DOCUMENT_REQUIRED_ERROR)
    }

    /**
     * Toggles REQUIRE_TRANSFER_OUT_DOCUMENT / REQUIRE_TRANSFER_IN_DOCUMENT on the seeded
     * Main Warehouse (id=1) and returns a transient Order that uses it as both origin and
     * destination. The change is reverted by the surrounding @Rollback transaction so
     * neighbouring specs are unaffected.
     */
    private Order buildTransferOrder(boolean enforceOut, boolean enforceIn) {
        Location location = Location.get('1')
        assert location != null, 'seeded Main Warehouse (id=1) is required for this test'

        Set<String> nextActivities = location.supportedActivities == null
            ? ([] as Set<String>)
            : new HashSet<>(location.supportedActivities)
        toggle(nextActivities, ActivityCode.REQUIRE_TRANSFER_OUT_DOCUMENT.id, enforceOut)
        toggle(nextActivities, ActivityCode.REQUIRE_TRANSFER_IN_DOCUMENT.id, enforceIn)
        location.supportedActivities = nextActivities
        location.save(flush: true, failOnError: true)

        return new Order(
            origin: location,
            destination: location,
            orderType: OrderType.get(OrderTypeCode.TRANSFER_ORDER.name()),
        )
    }

    private static void toggle(Set<String> activities, String code, boolean on) {
        if (on) {
            activities << code
        } else {
            activities.remove(code)
        }
    }

    /**
     * Builds a transient transfer Order whose parent location does NOT enforce either
     * activity code, but whose order item carries an in-memory child bin Location that
     * does. Verifies the bin-aware path of validateForCompletion (the production bug:
     * the parent depot doesn't enforce, only the bin does).
     *
     * The bin Location is constructed transient (never saved); validateForCompletion
     * only reads supportedActivities, so persistence is not required.
     */
    private Order buildTransferOrderWithBin(boolean binEnforcesOut, boolean binEnforcesIn) {
        Location parent = Location.get('1')
        assert parent != null, 'seeded Main Warehouse (id=1) is required for this test'

        // Make sure the parent doesn't enforce so we isolate the bin signal.
        Set<String> parentActivities = parent.supportedActivities == null
            ? ([] as Set<String>)
            : new HashSet<>(parent.supportedActivities)
        toggle(parentActivities, ActivityCode.REQUIRE_TRANSFER_OUT_DOCUMENT.id, false)
        toggle(parentActivities, ActivityCode.REQUIRE_TRANSFER_IN_DOCUMENT.id, false)
        parent.supportedActivities = parentActivities
        parent.save(flush: true, failOnError: true)

        Set<String> binActivities = [] as Set<String>
        toggle(binActivities, ActivityCode.REQUIRE_TRANSFER_OUT_DOCUMENT.id, binEnforcesOut)
        toggle(binActivities, ActivityCode.REQUIRE_TRANSFER_IN_DOCUMENT.id, binEnforcesIn)
        Location bin = new Location(
            name: 'TestQuarantineBin',
            parentLocation: parent,
            supportedActivities: binActivities,
        )

        Order order = new Order(
            origin: parent,
            destination: parent,
            orderType: OrderType.get(OrderTypeCode.TRANSFER_ORDER.name()),
        )
        OrderItem item = new OrderItem(
            originBinLocation: binEnforcesOut ? bin : null,
            destinationBinLocation: binEnforcesIn ? bin : null,
        )
        order.orderItems = [item] as Set
        return order
    }
}
