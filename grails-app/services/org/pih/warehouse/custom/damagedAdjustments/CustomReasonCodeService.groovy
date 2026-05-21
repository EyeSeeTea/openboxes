package org.pih.warehouse.custom.damagedAdjustments

import org.pih.warehouse.core.ReasonCode

class CustomReasonCodeService {

    static transactional = false

    def grailsApplication

    List<ReasonCode> listInventoryAdjustmentReasonCodes() {
        List<ReasonCode> base = ReasonCode.listInventoryAdjustmentReasonCodes()
        boolean damagedEnabled = grailsApplication.config.openboxes.custom.adjustments.damaged.enabled != false
        return damagedEnabled ? base : base.findAll { it != ReasonCode.DAMAGED }
    }
}
