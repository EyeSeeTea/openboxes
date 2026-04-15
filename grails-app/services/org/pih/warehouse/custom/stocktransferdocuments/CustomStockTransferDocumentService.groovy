package org.pih.warehouse.custom.stocktransferdocuments

import grails.gorm.transactions.Transactional
import grails.validation.ValidationException
import org.pih.warehouse.core.ActivityCode
import org.pih.warehouse.core.Constants
import org.pih.warehouse.core.Document
import org.pih.warehouse.core.DocumentType
import org.pih.warehouse.order.Order
import org.springframework.web.multipart.MultipartFile

@Transactional
class CustomStockTransferDocumentService {

    static final String NULL_ORDER_ERROR = 'Cannot validate stock transfer completion: order is null'
    static final String DOCUMENT_REQUIRED_ERROR = 'A document is required to complete stock transfers from this location'
    static final String DOCUMENT_REQUIRED_CODE = 'customStockTransferDocument.required.error'

    List<Map> listDocuments(Order order) {
        return order.documents?.collect { Document doc ->
            [
                id          : doc.id,
                name        : doc.name ?: doc.filename,
                filename    : doc.filename,
                documentType: doc.documentType?.name,
                contentType : doc.contentType,
                size        : doc.size,
                uri         : doc.link,
            ]
        } ?: []
    }

    Boolean isDocumentRequired(Order order) {
        return Boolean.TRUE.equals(order?.origin?.supports(ActivityCode.REQUIRE_TRANSFER_DOCUMENT))
    }

    Order uploadDocument(String orderId, MultipartFile fileContents) {
        Order order = getOrderOrThrow(orderId)
        if (!fileContents || fileContents.empty) {
            throw new IllegalArgumentException("File contents are required")
        }

        Document document = new Document()
        document.fileContents = fileContents.bytes
        document.contentType = fileContents.contentType
        document.name = fileContents.originalFilename
        document.filename = fileContents.originalFilename
        document.documentType = DocumentType.get(Constants.DEFAULT_DOCUMENT_TYPE_ID)

        order.addToDocuments(document)
        order.save(failOnError: true)

        log.info "custom_stock_transfer_document_uploaded orderId=${order.id} documentId=${document.id} size=${document.size}"
        return order
    }

    void validateForCompletion(Order order) {
        if (!order) {
            throw new IllegalArgumentException(NULL_ORDER_ERROR)
        }

        if (isDocumentRequired(order) && !order.documents) {
            log.warn "custom_stock_transfer_completion_blocked orderId=${order.id} originLocationId=${order.origin?.id}"
            order.errors.reject(DOCUMENT_REQUIRED_CODE, DOCUMENT_REQUIRED_ERROR)
            throw new ValidationException(DOCUMENT_REQUIRED_ERROR, order.errors)
        }
    }

    private Order getOrderOrThrow(String orderId) {
        Order order = Order.get(orderId)
        if (!order) {
            throw new IllegalArgumentException("No stock transfer order found for id ${orderId}")
        }
        return order
    }
}
