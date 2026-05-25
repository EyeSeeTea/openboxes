package org.pih.warehouse.custom.notifications

/**
 * Source that produced a CustomNotification. Stored as its name() in the
 * custom_notification.notification_type column.
 */
enum NotificationType {

    /** Recorded as a side-effect of an application email send (MailService.doSendMail). */
    EMAIL_TRIGGER
}
