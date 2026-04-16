package org.pih.warehouse.custom.stocktransferdocuments

import grails.testing.gorm.DataTest
import grails.testing.services.ServiceUnitTest
import grails.validation.ValidationException
import org.pih.warehouse.core.ActivityCode
import org.pih.warehouse.core.Document
import org.pih.warehouse.core.Location
import org.pih.warehouse.order.Order
import org.pih.warehouse.order.OrderItem
import org.springframework.validation.BeanPropertyBindingResult
import spock.lang.Specification
import spock.lang.Unroll

class CustomStockTransferDocumentServiceSpec extends Specification
        implements ServiceUnitTest<CustomStockTransferDocumentService>, DataTest {

    Class[] getDomainClassesToMock() {
        [Order, Document] as Class[]
    }

    private Order mockOrder(
            Location origin,
            Location destination,
            List<OrderItem> orderItems = [],
            Set<Document> documents = [] as Set) {
        Order order = Mock(Order)
        order.getOrigin() >> origin
        order.getDestination() >> destination
        order.getOrderItems() >> (orderItems as Set)
        order.getDocuments() >> documents
        order.getErrors() >> new BeanPropertyBindingResult(new Object(), 'order')
        return order
    }

    private OrderItem mockOrderItem(Location originBin, Location destinationBin) {
        OrderItem item = Mock(OrderItem)
        item.getOriginBinLocation() >> originBin
        item.getDestinationBinLocation() >> destinationBin
        return item
    }

    private Location locationSupporting(ActivityCode... activities) {
        Location location = Mock(Location)
        location.supports(_ as ActivityCode) >> { ActivityCode requested ->
            activities.contains(requested)
        }
        return location
    }

    private Location none() {
        return locationSupporting()
    }

    private Location withOut() {
        return locationSupporting(ActivityCode.REQUIRE_TRANSFER_OUT_DOCUMENT)
    }

    private Location withIn() {
        return locationSupporting(ActivityCode.REQUIRE_TRANSFER_IN_DOCUMENT)
    }

    @Unroll
    def "isDocumentRequired = #expected — #scenario"() {
        given:
        Order order = mockOrder(origin, destination, orderItems)

        expect:
        service.isDocumentRequired(order) == expected

        where:
        scenario                                  | origin     | destination | orderItems                                            || expected
        'neither side enforces'                   | none()     | none()      | []                                                    || false
        'origin depot enforces OUT'               | withOut()  | none()      | []                                                    || true
        'destination depot enforces IN'           | none()     | withIn()    | []                                                    || true
        'both depots enforce'                     | withOut()  | withIn()    | []                                                    || true
        'origin BIN enforces OUT (parent does not)' | none()   | none()      | [mockOrderItem(withOut(), null)]                      || true
        'destination BIN enforces IN (parent does not)' | none() | none()    | [mockOrderItem(null, withIn())]                       || true
        'multiple items, only one bin enforces'   | none()     | none()      | [mockOrderItem(none(), null), mockOrderItem(withOut(), null)] || true
        'order items but no bins enforce'         | none()     | none()      | [mockOrderItem(none(), none())]                       || false
    }

    def "isDocumentRequired returns false when origin and destination and items are null"() {
        expect:
        !service.isDocumentRequired(mockOrder(null, null))
    }

    def "listDocuments returns an empty list when the order has no documents"() {
        expect:
        service.listDocuments(mockOrder(null, null, [], [] as Set)) == []
    }

    def "validateForCompletion throws IllegalArgumentException when order is null"() {
        when:
        service.validateForCompletion(null)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == CustomStockTransferDocumentService.NULL_ORDER_ERROR
    }

    def "validateForCompletion throws ValidationException when an origin BIN requires OUT and no documents attached"() {
        given:
        Order order = mockOrder(none(), none(), [mockOrderItem(withOut(), null)])

        when:
        service.validateForCompletion(order)

        then:
        ValidationException ex = thrown()
        ex.message.startsWith(CustomStockTransferDocumentService.DOCUMENT_REQUIRED_ERROR)
        ex.errors.getAllErrors().any { it.code == CustomStockTransferDocumentService.DOCUMENT_REQUIRED_CODE }
    }

    def "validateForCompletion throws ValidationException when a destination BIN requires IN and no documents attached"() {
        given:
        Order order = mockOrder(none(), none(), [mockOrderItem(null, withIn())])

        when:
        service.validateForCompletion(order)

        then:
        ValidationException ex = thrown()
        ex.message.startsWith(CustomStockTransferDocumentService.DOCUMENT_REQUIRED_ERROR)
    }

    def "validateForCompletion does not throw when bin requires OUT and a document is attached"() {
        given:
        Order order = mockOrder(
            none(),
            none(),
            [mockOrderItem(withOut(), null)],
            [new Document(id: 'd1', name: 'cert.pdf')] as Set,
        )

        when:
        service.validateForCompletion(order)

        then:
        noExceptionThrown()
    }

    def "validateForCompletion does not throw when neither parent nor bins enforce"() {
        given:
        Order order = mockOrder(none(), none(), [mockOrderItem(none(), none())])

        when:
        service.validateForCompletion(order)

        then:
        noExceptionThrown()
    }
}
