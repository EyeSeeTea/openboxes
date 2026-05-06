package org.pih.warehouse.custom.outboundExpiryRestrictions.support

import org.pih.warehouse.api.AvailableItem

final class ExpiryRule {

    private ExpiryRule() {}

    // A lot whose expirationDate equals today (midnight) is still pickable — diverges
    // from upstream ProductAvailabilityService:555, which compares against the current
    // wall-clock and would treat the same lot as already expired by mid-morning.

    static boolean isExpired(Date expirationDate, Date today) {
        return expirationDate != null && expirationDate < today
    }

    static Integer sumPickableQuantity(List<AvailableItem> availableItems) {
        Date today = new Date().clearTime()
        def total = availableItems
                ?.findAll { it.quantityAvailable > 0 && !isExpired(it?.inventoryItem?.expirationDate, today) }
                ?.sum { it.quantityAvailable }
        return (total && total > 0 ? total : 0) as Integer
    }
}
