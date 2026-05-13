package org.pih.warehouse.custom.dhis2auth

import org.hibernate.FetchMode
import org.pih.warehouse.core.User

class Dhis2AdminService {

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
            fetchMode 'roles', FetchMode.JOIN
        }
    }
}
