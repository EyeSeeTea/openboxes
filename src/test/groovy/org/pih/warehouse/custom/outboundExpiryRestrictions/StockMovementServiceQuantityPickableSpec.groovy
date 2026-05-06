package org.pih.warehouse.custom.outboundExpiryRestrictions

import org.pih.warehouse.api.AvailableItem
import org.pih.warehouse.custom.outboundExpiryRestrictions.support.ExpiryRule
import org.pih.warehouse.inventory.InventoryItem
import spock.lang.Specification

class StockMovementServiceQuantityPickableSpec extends Specification {

    private static AvailableItem item(int qty, Date expirationDate) {
        new AvailableItem(
                inventoryItem: new InventoryItem(expirationDate: expirationDate),
                quantityAvailable: qty,
                quantityOnHand: qty,
        )
    }

    private static Date daysFromToday(int delta) {
        return new Date().clearTime() + delta
    }

    def "all expired items return 0"() {
        given:
        List<AvailableItem> items = [
                item(10, daysFromToday(-1)),
                item(20, daysFromToday(-30)),
        ]

        expect:
        ExpiryRule.sumPickableQuantity(items) == 0
    }

    def "all fresh items sum normally"() {
        given:
        List<AvailableItem> items = [
                item(10, daysFromToday(30)),
                item(20, daysFromToday(60)),
        ]

        expect:
        ExpiryRule.sumPickableQuantity(items) == 30
    }

    def "mixed items sum only the fresh ones"() {
        given:
        List<AvailableItem> items = [
                item(10, daysFromToday(-1)),
                item(20, daysFromToday(30)),
                item(50, daysFromToday(-365)),
        ]

        expect:
        ExpiryRule.sumPickableQuantity(items) == 20
    }

    def "null expirationDate counts as pickable"() {
        given:
        List<AvailableItem> items = [
                item(10, null),
                item(20, daysFromToday(30)),
        ]

        expect:
        ExpiryRule.sumPickableQuantity(items) == 30
    }

    def "today's date counts as pickable (strict-< semantics)"() {
        given:
        List<AvailableItem> items = [
                item(15, daysFromToday(0)),
                item(25, daysFromToday(-1)),
        ]

        expect:
        ExpiryRule.sumPickableQuantity(items) == 15
    }

    def "zero-quantity items are excluded even when fresh"() {
        given:
        List<AvailableItem> items = [
                item(0, daysFromToday(30)),
                item(7, daysFromToday(30)),
        ]

        expect:
        ExpiryRule.sumPickableQuantity(items) == 7
    }

    def "empty list returns 0"() {
        expect:
        ExpiryRule.sumPickableQuantity([]) == 0
    }

    def "null list returns 0"() {
        expect:
        ExpiryRule.sumPickableQuantity(null) == 0
    }
}
