package org.pih.warehouse.custom.damagedAdjustments

class DamagedAdjustmentInterceptor {

    // Runs early (before RoleInterceptor) — no session state dependency.
    int order = HIGHEST_PRECEDENCE + 1

    DamagedAdjustmentInterceptor() {
        match(controller: 'inventory', action: 'createDamaged')
    }

    boolean before() {
        boolean enabled = grailsApplication.config.openboxes.custom.adjustments.damaged.enabled ?: false
        if (!enabled) {
            log.info "Damaged adjustment blocked by openboxes.custom.adjustments.damaged.enabled=false"
            redirect(controller: 'errors', action: 'handleForbidden')
            return false
        }
        return true
    }
}
