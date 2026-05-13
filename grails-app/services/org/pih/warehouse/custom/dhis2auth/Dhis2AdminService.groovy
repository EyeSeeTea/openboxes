package org.pih.warehouse.custom.dhis2auth

import org.pih.warehouse.core.User

class Dhis2AdminService {

    // Pending users are a small set (manual approvals), so we keep the default
    // lazy fetch on `roles` rather than fetchMode 'roles', FetchMode.JOIN — the
    // latter breaks pagination (LIMIT applied before deduplication on the join).
    def findPendingDhis2Users(Map params) {
        String q = params.q ? "%${params.q}%" : null
        User.createCriteria().list(params) {
            eq('active', false)
            sqlRestriction("exists (select 1 from custom_dhis2_user_link dl where dl.user_id = {alias}.id)")
            if (q) {
                or {
                    ilike('username', q)
                    ilike('firstName', q)
                    ilike('lastName', q)
                    ilike('email', q)
                }
            }
        }
    }
}
