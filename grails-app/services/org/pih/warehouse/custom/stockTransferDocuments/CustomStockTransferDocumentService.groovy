package org.pih.warehouse.custom.stockTransferDocuments

import grails.gorm.transactions.Transactional
import grails.validation.ValidationException
import org.pih.warehouse.core.ActivityCode
import org.pih.warehouse.core.Constants
import org.pih.warehouse.core.Document
import org.pih.warehouse.core.DocumentType
import org.pih.warehouse.core.Location
import org.pih.warehouse.order.Order
import org.pih.warehouse.order.OrderItem
import org.springframework.web.multipart.MultipartFile

@Transactional
class CustomStockTransferDocumentService {

    static final String NULL_ORDER_ERROR = 'Cannot validate stock transfer completion: order is null'
    static final String DOCUMENT_REQUIRED_ERROR = 'A document is required to complete this stock transfer'
    static final String DOCUMENT_REQUIRED_CODE = 'customStockTransferDocument.required.error'

    def grailsApplication

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
        return originSideRequiresOutDocument(order) || destinationSideRequiresInDocument(order)
    }

    Boolean originSideRequiresOutDocument(Order order) {
        if (locationSupports(order?.origin, ActivityCode.REQUIRE_TRANSFER_OUT_DOCUMENT)) {
            return true
        }
        return order?.orderItems?.any { OrderItem item ->
            locationSupports(item.originBinLocation, ActivityCode.REQUIRE_TRANSFER_OUT_DOCUMENT)
        }
    }

    Boolean destinationSideRequiresInDocument(Order order) {
        if (locationSupports(order?.destination, ActivityCode.REQUIRE_TRANSFER_IN_DOCUMENT)) {
            return true
        }
        return order?.orderItems?.any { OrderItem item ->
            locationSupports(item.destinationBinLocation, ActivityCode.REQUIRE_TRANSFER_IN_DOCUMENT)
        }
    }

    private static Boolean locationSupports(Location location, ActivityCode activity) {
        return Boolean.TRUE.equals(location?.supports(activity))
    }

    long getMaxUploadSizeBytes() {
        Object configured = grailsApplication?.config?.openboxes?.custom?.stockTransferDocuments?.maxUploadSizeBytes
        if (configured instanceof Number && ((Number) configured).longValue() > 0L) {
            return ((Number) configured).longValue()
        }
        return UploadConstraints.DEFAULT_MAX_BYTES
    }

    Order uploadDocument(String orderId, MultipartFile fileContents) {
        Order order = getOrderOrThrow(orderId)
        if (!fileContents || fileContents.empty) {
            throw new IllegalArgumentException("File contents are required")
        }

        long maxBytes = getMaxUploadSizeBytes()
        if (fileContents.size > maxBytes) {
            throw new UploadValidationException(
                    UploadConstraints.TOO_LARGE_CODE,
                    UploadConstraints.TOO_LARGE_DEFAULT,
                    [maxBytes] as Object[])
        }

        String sanitizedName = UploadConstraints.sanitizeFilename(fileContents.originalFilename)
        if (!sanitizedName) {
            throw new UploadValidationException(
                    UploadConstraints.INVALID_FILENAME_CODE,
                    UploadConstraints.INVALID_FILENAME_DEFAULT)
        }

        boolean contentTypeOk = UploadConstraints.isContentTypeAllowed(fileContents.contentType)
        boolean extensionOk = UploadConstraints.isExtensionAllowed(sanitizedName)
        if (!contentTypeOk || !extensionOk) {
            throw new UploadValidationException(
                    UploadConstraints.INVALID_TYPE_CODE,
                    UploadConstraints.INVALID_TYPE_DEFAULT)
        }

        Document document = new Document()
        document.fileContents = fileContents.bytes
        document.contentType = fileContents.contentType
        document.name = sanitizedName
        document.filename = sanitizedName
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
            log.warn "custom_stock_transfer_completion_blocked orderId=${order.id}" +
                    " originLocationId=${order.origin?.id} destinationLocationId=${order.destination?.id}" +
                    " originBinLocationIds=${order.orderItems*.originBinLocation*.id}" +
                    " destinationBinLocationIds=${order.orderItems*.destinationBinLocation*.id}"
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
