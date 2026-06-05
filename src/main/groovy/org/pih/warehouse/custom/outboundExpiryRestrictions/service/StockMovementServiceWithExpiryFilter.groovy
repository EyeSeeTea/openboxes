package org.pih.warehouse.custom.outboundExpiryRestrictions.service

import groovy.util.logging.Slf4j
import org.pih.warehouse.api.AvailableItem
import org.pih.warehouse.custom.outboundExpiryRestrictions.support.ExpiryRule
import org.pih.warehouse.inventory.StockMovementService

// Reason: no class-level @Transactional — the parent StockMovementService is
// already @Transactional via grails.gorm.transactions.Transactional, and mixing
// the deprecated grails.transaction.Transactional on the subclass causes
// proxy-selection issues in Grails 3.3 (different post-processors). The override
// is read-only; the parent's transaction semantics carry through inherited methods.
@Slf4j
class StockMovementServiceWithExpiryFilter extends StockMovementService {

    @Override
    List getSuggestedItems(List<AvailableItem> availableItems, Integer quantityRequested) {
        if (!availableItems) {
            return super.getSuggestedItems(availableItems, quantityRequested)
        }
        Date today = new Date().clearTime()
        List<AvailableItem> filtered = filterExpired(availableItems, today)
        if (filtered.size() != availableItems.size()) {
            log.info "outbound_expiry_autopick_filter dropped=${availableItems.size() - filtered.size()} of=${availableItems.size()}"
        }
        return super.getSuggestedItems(filtered, quantityRequested)
    }

    static List<AvailableItem> filterExpired(List<AvailableItem> availableItems, Date today) {
        return availableItems.findAll { AvailableItem item ->
            !ExpiryRule.isExpired(item?.inventoryItem?.expirationDate, today)
        }
    }
}
