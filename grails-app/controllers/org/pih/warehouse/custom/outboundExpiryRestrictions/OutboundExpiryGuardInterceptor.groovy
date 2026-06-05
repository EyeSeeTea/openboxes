package org.pih.warehouse.custom.outboundExpiryRestrictions

import grails.converters.JSON
import org.pih.warehouse.api.StockMovementType
import org.pih.warehouse.custom.outboundExpiryRestrictions.support.ExpiryRule
import org.pih.warehouse.inventory.InventoryItem
import org.pih.warehouse.inventory.OutboundStockMovement
import org.pih.warehouse.requisition.RequisitionItem

import java.math.BigDecimal
import java.text.DateFormat

class OutboundExpiryGuardInterceptor {

    static final String ERROR_CODE = 'outboundExpiryRestrictions.expired.cannotShip'

    // Reason: must run AFTER SecurityInterceptor (default order 0) so unauthenticated
    // requests are short-circuited before this guard touches the DB or leaks ID existence.
    // Matches the pattern used by RoleInterceptor and SentryInterceptor.
    int order = LOWEST_PRECEDENCE

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

        // STOCK_MOVEMENT → enforce; RETURN_ORDER → allow; null → enforce (fail closed).
        OutboundStockMovement parentMovement = OutboundStockMovement.findByRequisition(requisitionItem.requisition)
        if (parentMovement && parentMovement.stockMovementType != StockMovementType.STOCK_MOVEMENT) {
            return true
        }

        List<String> ids = picklistItems
                .findAll { it?.inventoryItem?.id && isPositiveQuantity(it.quantityPicked) }
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

    // Reason: must parse as BigDecimal (not Integer) to match upstream's parser. Integer truncates
    // "1.5" to 0, which would let an expired-lot row slip past the guard; upstream then crashes
    // with ArithmeticException AFTER clearPicklist() runs, wiping the requisition's existing picks.
    private static boolean isPositiveQuantity(quantityPicked) {
        if (quantityPicked == null) {
            return false
        }
        if (quantityPicked instanceof Number) {
            return ((Number) quantityPicked).doubleValue() > 0
        }
        String s = quantityPicked.toString().trim()
        if (!s) {
            return false
        }
        try {
            return new BigDecimal(s).signum() > 0
        } catch (NumberFormatException ignored) {
            return false
        }
    }
}
