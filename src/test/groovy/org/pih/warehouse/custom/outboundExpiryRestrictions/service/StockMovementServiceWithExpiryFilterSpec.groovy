package org.pih.warehouse.custom.outboundExpiryRestrictions.service

import org.pih.warehouse.api.AvailableItem
import org.pih.warehouse.inventory.InventoryItem
import spock.lang.Specification

class StockMovementServiceWithExpiryFilterSpec extends Specification {

    private static AvailableItem itemWithExpiry(Date expirationDate) {
        new AvailableItem(
                inventoryItem: new InventoryItem(expirationDate: expirationDate),
                quantityAvailable: 10,
                quantityOnHand: 10,
        )
    }

    private static Date daysFromToday(int days) {
        return new Date().clearTime() + days
    }

    def "filterExpired drops items with past expirationDate"() {
        given:
        AvailableItem expired = itemWithExpiry(daysFromToday(-30))
        AvailableItem fresh = itemWithExpiry(daysFromToday(30))

        when:
        List<AvailableItem> result = StockMovementServiceWithExpiryFilter.filterExpired([expired, fresh])

        then:
        result == [fresh]
    }

    def "filterExpired keeps items with null expirationDate"() {
        given:
        AvailableItem nullExpiry = itemWithExpiry(null)

        when:
        List<AvailableItem> result = StockMovementServiceWithExpiryFilter.filterExpired([nullExpiry])

        then:
        result == [nullExpiry]
    }

    def "filterExpired keeps items expiring today (strict-< matches ProductAvailabilityService:555)"() {
        given:
        AvailableItem expiringToday = itemWithExpiry(daysFromToday(0))

        when:
        List<AvailableItem> result = StockMovementServiceWithExpiryFilter.filterExpired([expiringToday])

        then:
        result == [expiringToday]
    }

    def "filterExpired returns empty list when every item is expired"() {
        given:
        List<AvailableItem> input = [
                itemWithExpiry(daysFromToday(-1)),
                itemWithExpiry(daysFromToday(-365)),
        ]

        when:
        List<AvailableItem> result = StockMovementServiceWithExpiryFilter.filterExpired(input)

        then:
        result == []
    }

    def "filterExpired preserves order of fresh items"() {
        given:
        AvailableItem fresh1 = itemWithExpiry(daysFromToday(10))
        AvailableItem expired = itemWithExpiry(daysFromToday(-10))
        AvailableItem fresh2 = itemWithExpiry(daysFromToday(20))

        when:
        List<AvailableItem> result = StockMovementServiceWithExpiryFilter.filterExpired([fresh1, expired, fresh2])

        then:
        result == [fresh1, fresh2]
    }
}
