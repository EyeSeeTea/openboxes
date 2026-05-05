package org.pih.warehouse.custom.outboundExpiryRestrictions.support

final class ExpiryRule {

    private ExpiryRule() {}

    // A lot whose expirationDate equals today (midnight) is still pickable — diverges
    // from upstream ProductAvailabilityService:555, which compares against the current
    // wall-clock and would treat the same lot as already expired by mid-morning.

    static boolean isExpired(Date expirationDate) {
        return isExpired(expirationDate, new Date().clearTime())
    }

    static boolean isExpired(Date expirationDate, Date today) {
        return expirationDate != null && expirationDate < today
    }
}
