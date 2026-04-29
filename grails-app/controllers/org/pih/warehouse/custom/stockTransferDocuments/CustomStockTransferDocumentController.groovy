package org.pih.warehouse.custom.stockTransferDocuments

import grails.converters.JSON
import org.pih.warehouse.core.ActivityCode
import org.pih.warehouse.core.Location
import org.pih.warehouse.order.Order
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.multipart.MultipartHttpServletRequest

class CustomStockTransferDocumentController {

    def customStockTransferDocumentService
    def locationService
    def messageSource

    def list() {
        Order order = Order.get(params.id)
        if (!order) {
            response.status = 404
            render([errorMessage: "No stock transfer found for order ID ${params.id}"] as JSON)
            return
        }

        render([
            data: [
                documentRequired: customStockTransferDocumentService.isDocumentRequired(order),
                documents       : customStockTransferDocumentService.listDocuments(order),
            ],
        ] as JSON)
    }

    def refreshFilteredBinLocations() {
        Location location = Location.get(params.id)
        List<Location> bins = []
        if (location?.hasBinLocationSupport()) {
            bins = locationService.getBinLocations(location)
                .findAll { !it.supports(ActivityCode.REQUIRE_TRANSFER_IN_DOCUMENT) }
                .sort { it?.name?.toLowerCase() }
        }

        render g.select(
            name: params.name ?: 'otherBinLocation.id',
            'class': 'chzn-select-deselect',
            noSelection: ['': g.message(code: 'default.label')],
            from: bins,
            optionKey: 'id',
            optionValue: 'name',
        )
    }

    def upload() {
        MultipartFile fileContents = (request instanceof MultipartHttpServletRequest)
            ? (request as MultipartHttpServletRequest).getFile("fileContents")
            : null
        if (!fileContents || fileContents.empty) {
            response.status = 400
            render([errorMessage: "fileContents is required"] as JSON)
            return
        }

        try {
            customStockTransferDocumentService.uploadDocument(params.id, fileContents)
            render([data: "Document was uploaded successfully"] as JSON)
        } catch (UploadValidationException ex) {
            log.warn "custom_stock_transfer_document_upload_rejected orderId=${params.id}" +
                    " code=${ex.messageCode} originalFilename=${fileContents.originalFilename}" +
                    " contentType=${fileContents.contentType} size=${fileContents.size}"
            response.status = 400
            render([errorMessage: resolveMessage(ex)] as JSON)
        } catch (IllegalArgumentException ex) {
            log.warn "custom_stock_transfer_document_upload_bad_request orderId=${params.id} message=${ex.message}"
            response.status = 404
            render([errorMessage: ex.message] as JSON)
        } catch (Exception ex) {
            log.error "custom_stock_transfer_document_upload_failed orderId=${params.id}", ex
            response.status = 500
            render([errorMessage: 'Document upload failed. Please try again.'] as JSON)
        }
    }

    private String resolveMessage(UploadValidationException ex) {
        try {
            return messageSource.getMessage(
                    ex.messageCode,
                    ex.messageArgs,
                    ex.message,
                    LocaleContextHolder.locale)
        } catch (Exception ignore) {
            return ex.message
        }
    }
}
