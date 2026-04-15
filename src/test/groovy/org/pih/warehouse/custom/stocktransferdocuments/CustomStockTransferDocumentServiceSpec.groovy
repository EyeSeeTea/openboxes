package org.pih.warehouse.custom.stocktransferdocuments

import grails.testing.gorm.DataTest
import grails.testing.services.ServiceUnitTest
import grails.validation.ValidationException
import org.pih.warehouse.core.ActivityCode
import org.pih.warehouse.core.Document
import org.pih.warehouse.core.Location
import org.pih.warehouse.order.Order
import org.springframework.validation.BeanPropertyBindingResult
import spock.lang.Specification

class CustomStockTransferDocumentServiceSpec extends Specification
        implements ServiceUnitTest<CustomStockTransferDocumentService>, DataTest {

    Class[] getDomainClassesToMock() {
        [Order, Document] as Class[]
    }

    private Order mockOrder(Location origin, Set<Document> documents = [] as Set) {
        Order order = Mock(Order)
        order.getOrigin() >> origin
        order.getDocuments() >> documents
        order.getErrors() >> new BeanPropertyBindingResult(new Object(), 'order')
        return order
    }

    private Location originSupporting(boolean supports) {
        Location origin = Mock(Location)
        origin.supports(ActivityCode.REQUIRE_TRANSFER_DOCUMENT) >> supports
        return origin
    }

    def "isDocumentRequired returns false when origin does not support REQUIRE_TRANSFER_DOCUMENT"() {
        expect:
        !service.isDocumentRequired(mockOrder(originSupporting(false)))
    }

    def "isDocumentRequired returns true when origin supports REQUIRE_TRANSFER_DOCUMENT"() {
        expect:
        service.isDocumentRequired(mockOrder(originSupporting(true)))
    }

    def "isDocumentRequired returns false when origin is null"() {
        expect:
        !service.isDocumentRequired(mockOrder(null))
    }

    def "listDocuments returns an empty list when the order has no documents"() {
        expect:
        service.listDocuments(mockOrder(null, [] as Set)) == []
    }

    def "validateForCompletion throws IllegalArgumentException when order is null"() {
        when:
        service.validateForCompletion(null)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == CustomStockTransferDocumentService.NULL_ORDER_ERROR
    }

    def "validateForCompletion throws ValidationException when required and no documents attached"() {
        given:
        Order order = mockOrder(originSupporting(true), [] as Set)

        when:
        service.validateForCompletion(order)

        then:
        ValidationException ex = thrown()
        // ValidationException.message prepends the supplied message with a summary of order.errors,
        // so match on startsWith rather than equality.
        ex.message.startsWith(CustomStockTransferDocumentService.DOCUMENT_REQUIRED_ERROR)
        ex.errors.getAllErrors().any { it.code == CustomStockTransferDocumentService.DOCUMENT_REQUIRED_CODE }
    }

    def "validateForCompletion does not throw when required and documents are attached"() {
        given:
        Order order = mockOrder(originSupporting(true), [new Document(id: 'd1', name: 'cert.pdf')] as Set)

        when:
        service.validateForCompletion(order)

        then:
        noExceptionThrown()
    }

    def "validateForCompletion does not throw when enforcement is disabled"() {
        given:
        Order order = mockOrder(originSupporting(false), [] as Set)

        when:
        service.validateForCompletion(order)

        then:
        noExceptionThrown()
    }
}
