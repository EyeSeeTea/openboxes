package org.pih.warehouse.custom.notifications

import org.pih.warehouse.core.User

class CustomNotification {

    String id
    User user
    String notificationType
    String title
    String body
    String linkUrl
    Boolean isRead = false
    Date readAt
    Date dateCreated
    Date lastUpdated

    static constraints = {
        notificationType blank: false, maxSize: 64
        title blank: false, maxSize: 255
        body nullable: true
        linkUrl nullable: true, maxSize: 2048
        readAt nullable: true
        user nullable: false
    }

    static mapping = {
        table 'custom_notification'
        id generator: 'uuid'
        user column: 'user_id'
        isRead column: 'is_read'
        readAt column: 'read_at'
        dateCreated column: 'date_created'
        lastUpdated column: 'last_updated'
        notificationType column: 'notification_type'
        linkUrl column: 'link_url'
    }
}
