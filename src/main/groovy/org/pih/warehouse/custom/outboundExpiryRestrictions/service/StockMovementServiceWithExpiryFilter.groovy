package org.pih.warehouse.custom.outboundExpiryRestrictions.service

import grails.transaction.Transactional
import groovy.util.logging.Slf4j
import org.pih.warehouse.api.AvailableItem
import org.pih.warehouse.custom.outboundExpiryRestrictions.support.ExpiryRule
import org.pih.warehouse.inventory.StockMovementService

@Slf4j
@Transactional
class StockMovementServiceWithExpiryFilter extends StockMovementService {

    @Override
    List getSuggestedItems(List<AvailableItem> availableItems, Integer quantityRequested) {
        if (!availableItems) {
            return super.getSuggestedItems(availableItems, quantityRequested)
        }
        List<AvailableItem> filtered = filterExpired(availableItems)
        if (filtered.size() != availableItems.size()) {
            log.info "outbound_expiry_autopick_filter dropped=${availableItems.size() - filtered.size()} of=${availableItems.size()}"
        }
        return super.getSuggestedItems(filtered, quantityRequested)
    }

    static List<AvailableItem> filterExpired(List<AvailableItem> availableItems) {
        Date today = new Date().clearTime()
        return availableItems.findAll { AvailableItem item ->
            !ExpiryRule.isExpired(item?.inventoryItem?.expirationDate, today)
        }
    }

    static Integer sumPickableQuantity(List<AvailableItem> availableItems) {
        Date today = new Date().clearTime()
        def total = availableItems
                ?.findAll { it.quantityAvailable > 0 && !ExpiryRule.isExpired(it?.inventoryItem?.expirationDate, today) }
                ?.sum { it.quantityAvailable }
        return (total && total > 0 ? total : 0) as Integer
    }
}
