package org.pih.warehouse.custom.dhis2auth

import org.pih.warehouse.core.User

// Mirrors the session-setup AuthController.handleLogin performs on success.
// If handleLogin changes upstream, mirror it here.
class Dhis2SessionService {

    static transactional = false

    void establishSession(User user, session) {
        session.user = user
        session.userName = user.username
        session.pendingDhis2UserId = null
        if (user.warehouse && user.rememberLastLocation) {
            session.warehouse = user.warehouse
        }
    }

    void setPendingSession(User user, session) {
        session.pendingDhis2UserId = user.id
    }

    void clearPendingSession(session) {
        session.pendingDhis2UserId = null
    }
}
