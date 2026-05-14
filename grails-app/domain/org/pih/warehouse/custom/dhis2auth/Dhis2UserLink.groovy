package org.pih.warehouse.custom.dhis2auth

import org.pih.warehouse.core.User

class Dhis2UserLink implements Serializable {

    String id
    User user
    String dhis2Uid
    String dhis2Username
    Date lastLoginAt
    Date dateCreated
    Date lastUpdated

    static mapping = {
        table 'custom_dhis2_user_link'
        id generator: 'uuid'
        user column: 'user_id'
        dhis2Uid column: 'dhis2_uid'
        dhis2Username column: 'dhis2_username'
        lastLoginAt column: 'last_login_at'
        dateCreated column: 'created_at'
        lastUpdated column: 'updated_at'
    }

    static constraints = {
        user unique: true
        dhis2Uid unique: true, blank: false, size: 11..11
        dhis2Username nullable: true, maxSize: 255
        lastLoginAt nullable: true
    }
}
