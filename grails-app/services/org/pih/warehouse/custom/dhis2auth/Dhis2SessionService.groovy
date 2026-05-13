package org.pih.warehouse.custom.dhis2auth

import grails.gorm.transactions.NotTransactional
import org.pih.warehouse.core.User

/**
 * Mirrors the session-setup steps AuthController.handleLogin performs on success
 * (D1.1). Called for active DHIS2 users after OAuth callback.
 */
class Dhis2SessionService {

    @NotTransactional
    void establishSession(User user, session) {
        session.user = user
        session.userName = user.username
        session.pendingDhis2UserId = null
        if (user.warehouse && user.rememberLastLocation) {
            session.warehouse = user.warehouse
        }
    }

    @NotTransactional
    void setPendingSession(User user, session) {
        session.pendingDhis2UserId = user.id
    }

    @NotTransactional
    void clearPendingSession(session) {
        session.pendingDhis2UserId = null
    }
}
