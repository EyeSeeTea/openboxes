package org.pih.warehouse.custom.putawayDocuments

import grails.gorm.transactions.Transactional
import org.pih.warehouse.api.Putaway
import org.pih.warehouse.custom.stockTransferDocuments.CustomStockTransferDocumentService
import org.pih.warehouse.order.Order
import org.springframework.web.multipart.MultipartFile

@Transactional
class CustomPutawayDocumentService {

    CustomStockTransferDocumentService customStockTransferDocumentService

    Boolean isDocumentRequired(String putawayId) {
        Order order = Order.get(putawayId)
        return order ? customStockTransferDocumentService.isDocumentRequired(order) : false
    }

    List<Map> listDocuments(String putawayId) {
        Order order = Order.get(putawayId)
        return order ? customStockTransferDocumentService.listDocuments(order) : []
    }

    Order uploadDocument(String putawayId, MultipartFile fileContents) {
        return customStockTransferDocumentService.uploadDocument(putawayId, fileContents)
    }

    // Skips validation when the underlying Order doesn't exist yet — supporting docs can only be
    // attached to a saved putaway, so a missing Order can't have a missing-document state.
    void validateForCompletion(Putaway putaway) {
        if (!putaway?.id) {
            return
        }
        Order order = Order.get(putaway.id)
        if (!order) {
            return
        }
        customStockTransferDocumentService.validateForCompletion(order)
    }
}
