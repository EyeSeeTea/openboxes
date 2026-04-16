package org.pih.warehouse.custom.stockTransferDocuments

import grails.converters.JSON
import org.pih.warehouse.order.Order
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.multipart.MultipartHttpServletRequest

class CustomStockTransferDocumentController {

    def customStockTransferDocumentService

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

    def upload() {
        MultipartFile fileContents = (request instanceof MultipartHttpServletRequest)
            ? (request as MultipartHttpServletRequest).getFile("fileContents")
            : null
        if (!fileContents || fileContents.empty) {
            response.status = 400
            render([errorMessage: "fileContents is required"] as JSON)
            return
        }

        customStockTransferDocumentService.uploadDocument(params.id, fileContents)
        render([data: "Document was uploaded successfully"] as JSON)
    }
}
