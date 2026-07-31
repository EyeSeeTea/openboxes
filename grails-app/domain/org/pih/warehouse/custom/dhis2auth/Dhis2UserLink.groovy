package org.pih.warehouse.custom.dhis2auth

import org.pih.warehouse.core.User

class Dhis2UserLink implements Serializable {

    String id
    User user
    String dhis2Uid
    String dhis2Username
    Date lastLoginAt
    Date deactivatedAt
    Date dateCreated
    Date lastUpdated

    static mapping = {
        table 'custom_dhis2_user_link'
        id generator: 'uuid'
        user column: 'user_id'
        dhis2Uid column: 'dhis2_uid'
        dhis2Username column: 'dhis2_username'
        lastLoginAt column: 'last_login_at'
        deactivatedAt column: 'deactivated_at'
        dateCreated column: 'created_at'
        lastUpdated column: 'updated_at'
    }

    static constraints = {
        user unique: true
        // v42 links have no DHIS2 UID (identity is the username); v40/v41 links carry the 11-char UID.
        // nullable allows the v42 (no-UID) case; blank rejects an empty-string UID.
        dhis2Uid nullable: true, unique: true, blank: false, size: 11..11
        dhis2Username unique: true, nullable: false, blank: false, maxSize: 255
        lastLoginAt nullable: true
        deactivatedAt nullable: true
    }
}
