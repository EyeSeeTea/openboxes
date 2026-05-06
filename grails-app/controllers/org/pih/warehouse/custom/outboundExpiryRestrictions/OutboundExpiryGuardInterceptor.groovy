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
                .findAll { it?.inventoryItem?.id && parsePickedQuantity(it.quantityPicked) > 0 }
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

    // Reason: the formatted user-facing string lives only in the i18n bundle
    // (`outboundExpiryRestrictions-messages.properties`) so translators have a single
    // source of truth. The defaultMessage is just a non-interpolated safety net for the
    // catastrophic case where the bundle is missing — it never renders in normal operation.
    private static final String MISSING_BUNDLE_FALLBACK = 'Expired stock cannot be picked.'

    private String formatMessage(row, DateFormat dateFormat) {
        String lotNumber = (row[0] ?: '') as String
        Date expirationDate = (Date) row[1]
        String productCode = (row[2] ?: '') as String
        String formattedExpiry = expirationDate ? dateFormat.format(expirationDate) : ''
        Object[] args = [productCode, lotNumber, formattedExpiry] as Object[]
        return messageSource.getMessage(ERROR_CODE, args, MISSING_BUNDLE_FALLBACK, request.locale)
    }

    private static int parsePickedQuantity(quantityPicked) {
        if (quantityPicked == null) {
            return 0
        }
        if (quantityPicked instanceof Number) {
            return ((Number) quantityPicked).intValue()
        }
        String s = quantityPicked.toString().trim()
        if (!s) {
            return 0
        }
        try {
            return Integer.parseInt(s)
        } catch (NumberFormatException ignored) {
            return 0
        }
    }
}
