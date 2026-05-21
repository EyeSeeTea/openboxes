package org.pih.warehouse.custom.damagedAdjustments

import grails.testing.services.ServiceUnitTest
import org.pih.warehouse.core.ReasonCode
import spock.lang.Specification

class CustomReasonCodeServiceSpec extends Specification implements ServiceUnitTest<CustomReasonCodeService> {

    private static final List<ReasonCode> ALL_CODES = ReasonCode.listInventoryAdjustmentReasonCodes()
    private static final List<ReasonCode> CODES_WITHOUT_DAMAGED = ALL_CODES.findAll { it != ReasonCode.DAMAGED }

    def "flag=false: returned list omits DAMAGED and equals the filtered base list"() {
        given:
        config.openboxes.custom.adjustments.damaged.enabled = false

        when:
        List<ReasonCode> result = service.listInventoryAdjustmentReasonCodes()

        then:
        result == CODES_WITHOUT_DAMAGED
        !(ReasonCode.DAMAGED in result)
    }

    def "flag=true: returned list equals ReasonCode.listInventoryAdjustmentReasonCodes() exactly"() {
        given:
        config.openboxes.custom.adjustments.damaged.enabled = true

        when:
        List<ReasonCode> result = service.listInventoryAdjustmentReasonCodes()

        then:
        result == ALL_CODES
    }
}
