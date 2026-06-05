package org.pih.warehouse.custom.damagedAdjustments

class DamagedAdjustmentInterceptor {

    def customReasonCodeService

    // Runs early (before RoleInterceptor) — no session state dependency.
    int order = HIGHEST_PRECEDENCE + 1

    DamagedAdjustmentInterceptor() {
        match(controller: 'inventory', action: 'createDamaged')
    }

    boolean before() {
        if (!customReasonCodeService.damagedEnabled) {
            log.warn "Damaged adjustment blocked for user=${session.user?.username} (openboxes.custom.adjustments.damaged.enabled=false)"
            redirect(controller: 'errors', action: 'handleForbidden')
            return false
        }
        return true
    }
}
