package org.pih.warehouse.custom.damagedAdjustments

import org.pih.warehouse.core.ReasonCode

class CustomReasonCodeService {

    static transactional = false

    def grailsApplication

    // Absent config = enabled; only literal `false` opts out.
    boolean isDamagedEnabled() {
        return grailsApplication.config.openboxes.custom.adjustments.damaged.enabled != false
    }

    List<ReasonCode> listInventoryAdjustmentReasonCodes() {
        List<ReasonCode> base = ReasonCode.listInventoryAdjustmentReasonCodes()
        return damagedEnabled ? base : base.findAll { it != ReasonCode.DAMAGED }
    }
}
