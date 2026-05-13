package org.pih.warehouse.custom.dhis2auth

import org.pih.warehouse.core.User

class Dhis2AdminService {

    def findPendingDhis2Users(Map params) {
        User.createCriteria().list(params) {
            eq('active', false)
            sqlRestriction("exists (select 1 from custom_dhis2_user_link dl where dl.user_id = {alias}.id)")
        }
    }
}
