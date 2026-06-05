package org.pih.warehouse.custom.outboundExpiryRestrictions

import grails.testing.web.interceptor.InterceptorUnitTest
import org.pih.warehouse.api.StockMovementType
import org.pih.warehouse.inventory.InventoryItem
import org.pih.warehouse.inventory.OutboundStockMovement
import org.pih.warehouse.requisition.Requisition
import org.pih.warehouse.requisition.RequisitionItem
import org.springframework.context.MessageSource
import spock.lang.Specification

class OutboundExpiryGuardInterceptorSpec extends Specification
        implements InterceptorUnitTest<OutboundExpiryGuardInterceptor> {

    static final String EXPIRED_LOT_ID = 'lot-expired-1'
    static final String FRESH_LOT_ID = 'lot-fresh-1'
    static final String NULL_EXPIRY_LOT_ID = 'lot-null-expiry-1'
    static final String REQUISITION_ITEM_ID = 'req-item-1'
    static final String MISSING_ID = 'does-not-exist'

    Map<String, List> rowsById

    void setup() {
        interceptor.messageSource = Mock(MessageSource) {
            getMessage(_, _, _, _) >> { args -> args[2] as String }
        }

        Date today = new Date().clearTime()
        rowsById = [
                (EXPIRED_LOT_ID)    : ['EXP-001', today - 1, 'VAC-X'],
                (FRESH_LOT_ID)      : ['FRESH-001', today + 30, 'VAC-X'],
                (NULL_EXPIRY_LOT_ID): ['NULLEXP-001', null, 'VAC-X'],
        ]
        InventoryItem.metaClass.static.executeQuery = { String hql, Map params ->
            ((List<String>) params.ids).collect { rowsById[it] }.findAll()
        }

        Requisition req = new Requisition(name: 'TEST-REQ')
        RequisitionItem reqItem = new RequisitionItem(requisition: req)
        RequisitionItem.metaClass.static.get = { Serializable id ->
            (id as String) == REQUISITION_ITEM_ID ? reqItem : null
        }
    }

    void cleanup() {
        InventoryItem.metaClass = null
        RequisitionItem.metaClass = null
        OutboundStockMovement.metaClass = null
    }

    private void stubParentStockMovement(StockMovementType type) {
        OutboundStockMovement.metaClass.static.findByRequisition = { Requisition r ->
            r ? new OutboundStockMovement(stockMovementType: type) : null
        }
    }

    private void stubMissingStockMovement() {
        OutboundStockMovement.metaClass.static.findByRequisition = { Requisition r -> null }
    }

    private void postPicklist(String reqItemId, List<String> inventoryItemIds) {
        postPicklistWithQuantities(reqItemId, inventoryItemIds.collectEntries { [(it): 1] })
    }

    private void postPicklistWithQuantities(String reqItemId, Map<String, ?> quantitiesByInventoryItemId) {
        def picklistItems = quantitiesByInventoryItemId.collect { id, qty ->
            [inventoryItem: [id: id], binLocation: [id: 'bin-1'], quantityPicked: qty]
        }
        request.method = 'POST'
        request.contentType = 'application/json'
        request.json = [picklistItems: picklistItems, reasonCode: '']
        params.id = reqItemId
        withRequest(controller: 'stockMovementItemApi', action: 'updatePicklist')
    }

    def "matches stockMovementItemApi.updatePicklist"() {
        when:
        withRequest(controller: 'stockMovementItemApi', action: 'updatePicklist')

        then:
        interceptor.doesMatch()
    }

    def "does not match other actions on the same controller"() {
        when:
        withRequest(controller: 'stockMovementItemApi', action: 'createPicklist')

        then:
        !interceptor.doesMatch()
    }

    def "rejects an expired lot for STOCK_MOVEMENT with HTTP 400 and the expected errorCode"() {
        given:
        stubParentStockMovement(StockMovementType.STOCK_MOVEMENT)
        postPicklist(REQUISITION_ITEM_ID, [EXPIRED_LOT_ID])

        when:
        boolean proceed = interceptor.before()

        then:
        proceed == false
        response.status == 400
        response.json.errorCode == OutboundExpiryGuardInterceptor.ERROR_CODE
        response.json.errorMessages.size() == 1
    }

    def "allows a fresh-only payload for STOCK_MOVEMENT"() {
        given:
        stubParentStockMovement(StockMovementType.STOCK_MOVEMENT)
        postPicklist(REQUISITION_ITEM_ID, [FRESH_LOT_ID])

        when:
        boolean proceed = interceptor.before()

        then:
        proceed == true
    }

    def "allows null-expirationDate lots"() {
        given:
        stubParentStockMovement(StockMovementType.STOCK_MOVEMENT)
        postPicklist(REQUISITION_ITEM_ID, [NULL_EXPIRY_LOT_ID])

        when:
        boolean proceed = interceptor.before()

        then:
        proceed == true
    }

    def "rejects payloads that mix expired and fresh lots"() {
        given:
        stubParentStockMovement(StockMovementType.STOCK_MOVEMENT)
        postPicklist(REQUISITION_ITEM_ID, [FRESH_LOT_ID, EXPIRED_LOT_ID])

        when:
        boolean proceed = interceptor.before()

        then:
        proceed == false
        response.status == 400
        response.json.errorMessages.size() == 1
    }

    def "lets a RETURN_ORDER through even with an expired lot (defence-in-depth fall-through)"() {
        given:
        stubParentStockMovement(StockMovementType.RETURN_ORDER)
        postPicklist(REQUISITION_ITEM_ID, [EXPIRED_LOT_ID])

        when:
        boolean proceed = interceptor.before()

        then:
        proceed == true
    }

    def "lets the request through when picklistItems is empty (controller will reject)"() {
        given:
        stubParentStockMovement(StockMovementType.STOCK_MOVEMENT)
        postPicklist(REQUISITION_ITEM_ID, [])

        when:
        boolean proceed = interceptor.before()

        then:
        proceed == true
    }

    def "fails open when the requisitionItem id does not resolve"() {
        given:
        stubParentStockMovement(StockMovementType.STOCK_MOVEMENT)
        postPicklist(MISSING_ID, [EXPIRED_LOT_ID])

        when:
        boolean proceed = interceptor.before()

        then:
        proceed == true
    }

    def "ignores expired lots in the payload that are not actually being picked (#scenario)"() {
        given:
        stubParentStockMovement(StockMovementType.STOCK_MOVEMENT)
        postPicklistWithQuantities(REQUISITION_ITEM_ID, [
                (FRESH_LOT_ID)  : 13,
                (EXPIRED_LOT_ID): notPickedValue,
        ])

        when:
        boolean proceed = interceptor.before()

        then:
        proceed == true

        where:
        scenario           | notPickedValue
        'empty string'     | ''
        'whitespace'       | '   '
        'zero integer'     | 0
        'zero string'      | '0'
        'null'             | null
        'negative integer' | -1
    }

    def "still rejects when an expired lot is being picked alongside a fresh one"() {
        given:
        stubParentStockMovement(StockMovementType.STOCK_MOVEMENT)
        postPicklistWithQuantities(REQUISITION_ITEM_ID, [
                (FRESH_LOT_ID)  : 5,
                (EXPIRED_LOT_ID): 3,
        ])

        when:
        boolean proceed = interceptor.before()

        then:
        proceed == false
        response.status == 400
        response.json.errorCode == OutboundExpiryGuardInterceptor.ERROR_CODE
    }

    def "lets the request through when an inventoryItem.id is unknown to the DB (executeQuery returns fewer rows than requested)"() {
        given:
        stubParentStockMovement(StockMovementType.STOCK_MOVEMENT)
        postPicklist(REQUISITION_ITEM_ID, ['lot-unknown-1', FRESH_LOT_ID])

        when:
        boolean proceed = interceptor.before()

        then:
        proceed == true
    }

    def "still rejects when the payload mixes a known-expired lot and an unknown id"() {
        given:
        stubParentStockMovement(StockMovementType.STOCK_MOVEMENT)
        postPicklist(REQUISITION_ITEM_ID, ['lot-unknown-1', EXPIRED_LOT_ID])

        when:
        boolean proceed = interceptor.before()

        then:
        proceed == false
        response.status == 400
        response.json.errorCode == OutboundExpiryGuardInterceptor.ERROR_CODE
    }

    def "still applies the expiry check when the parent traversal returns null (no RETURN_ORDER fall-through)"() {
        given:
        stubMissingStockMovement()
        postPicklist(REQUISITION_ITEM_ID, [EXPIRED_LOT_ID])

        when:
        boolean proceed = interceptor.before()

        then:
        proceed == false
        response.status == 400
        response.json.errorCode == OutboundExpiryGuardInterceptor.ERROR_CODE
    }
}
