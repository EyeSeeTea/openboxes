package org.pih.warehouse.custom.stockTransferDocuments

import grails.converters.JSON
import org.pih.warehouse.core.ActivityCode
import org.pih.warehouse.core.Location
import org.pih.warehouse.order.Order
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.multipart.MultipartHttpServletRequest

class CustomStockTransferDocumentController {

    def customStockTransferDocumentService
    def locationService

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

        customStockTransferDocumentService.uploadDocument(params.id, fileContents)
        render([data: "Document was uploaded successfully"] as JSON)
    }
}
