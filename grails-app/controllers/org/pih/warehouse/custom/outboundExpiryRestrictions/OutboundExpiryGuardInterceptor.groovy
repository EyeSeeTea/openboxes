package org.pih.warehouse.custom.outboundExpiryRestrictions

import grails.converters.JSON
import org.pih.warehouse.api.StockMovementType
import org.pih.warehouse.custom.outboundExpiryRestrictions.support.ExpiryRule
import org.pih.warehouse.inventory.InventoryItem
import org.pih.warehouse.inventory.OutboundStockMovement
import org.pih.warehouse.requisition.RequisitionItem

import java.text.DateFormat

class OutboundExpiryGuardInterceptor {

    static final String ERROR_CODE = 'outboundExpiryRestrictions.expired.cannotShip'

    def messageSource

    OutboundExpiryGuardInterceptor() {
        match(controller: 'stockMovementItemApi', action: 'updatePicklist')
    }

    boolean before() {
        def jsonObject = request.JSON
        List picklistItems = jsonObject?.picklistItems
        if (!picklistItems) {
            return true
        }

        RequisitionItem requisitionItem = RequisitionItem.get(params.id)
        if (!requisitionItem) {
            return true
        }

        // Reason: defence-in-depth. The SQL view at grails-app/migrations/views/stock-movement.sql
        // hard-codes stock_movement_type='STOCK_MOVEMENT' for the requisition branch, but if upstream
        // changes the view to introduce other types backed by requisitions, this guard auto-disables
        // for them. Failing open on null per design.md Risks table.
        OutboundStockMovement parentMovement = OutboundStockMovement.findByRequisition(requisitionItem.requisition)
        if (parentMovement && parentMovement.stockMovementType != StockMovementType.STOCK_MOVEMENT) {
            return true
        }

        List<String> ids = picklistItems
                .findAll { it?.inventoryItem?.id }
                .collect { it.inventoryItem.id as String }
        if (!ids) {
            return true
        }

        // One projection query instead of N InventoryItem.read() round-trips plus N lazy-product fetches.
        List<Object[]> rows = InventoryItem.executeQuery(
                'select ii.lotNumber, ii.expirationDate, p.productCode ' +
                        'from InventoryItem ii left join ii.product p where ii.id in :ids',
                [ids: ids]
        )

        Date today = new Date().clearTime()
        List expired = rows.findAll { ExpiryRule.isExpired((Date) it[1], today) }

        if (!expired) {
            return true
        }

        DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.SHORT, request.locale)
        List<String> errorMessages = expired.collect { formatMessage(it, dateFormat) }
        log.info("outbound_expiry_guard_blocked requisition_item_id=${params.id} expired_count=${expired.size()}")

        response.status = 400
        render([errorCode: ERROR_CODE, errorMessages: errorMessages] as JSON)
        return false
    }

    private String formatMessage(row, DateFormat dateFormat) {
        String lotNumber = (row[0] ?: '') as String
        Date expirationDate = (Date) row[1]
        String productCode = (row[2] ?: '') as String
        String formattedExpiry = expirationDate ? dateFormat.format(expirationDate) : ''
        Object[] args = [productCode, lotNumber, formattedExpiry] as Object[]
        String defaultMessage = "Cannot pick lot ${lotNumber} of product ${productCode} — it expired on ${formattedExpiry}."
        return messageSource.getMessage(ERROR_CODE, args, defaultMessage, request.locale)
    }
}
