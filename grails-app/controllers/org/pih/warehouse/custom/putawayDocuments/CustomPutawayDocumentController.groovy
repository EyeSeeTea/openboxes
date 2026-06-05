package org.pih.warehouse.custom.putawayDocuments

import grails.converters.JSON
import org.pih.warehouse.custom.stockTransferDocuments.UploadValidationException
import org.pih.warehouse.order.Order
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.multipart.MultipartHttpServletRequest

class CustomPutawayDocumentController {

    def customPutawayDocumentService
    def messageSource

    def list() {
        Order order = Order.get(params.id)
        if (!order) {
            response.status = 404
            render([errorMessage: "No putaway found for order ID ${params.id}"] as JSON)
            return
        }

        render([
            data: [
                documentRequired: customPutawayDocumentService.isDocumentRequired(params.id),
                documents       : customPutawayDocumentService.listDocuments(params.id),
            ],
        ] as JSON)
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
            customPutawayDocumentService.uploadDocument(params.id, fileContents)
            render([data: "Document was uploaded successfully"] as JSON)
        } catch (UploadValidationException ex) {
            log.warn "custom_putaway_document_upload_rejected putawayId=${params.id}" +
                    " code=${ex.messageCode} originalFilename=${fileContents.originalFilename}" +
                    " contentType=${fileContents.contentType} size=${fileContents.size}"
            response.status = 400
            render([errorMessage: resolveMessage(ex)] as JSON)
        } catch (IllegalArgumentException ex) {
            log.warn "custom_putaway_document_upload_bad_request putawayId=${params.id} message=${ex.message}"
            response.status = 404
            render([errorMessage: ex.message] as JSON)
        } catch (Exception ex) {
            log.error "custom_putaway_document_upload_failed putawayId=${params.id}", ex
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
