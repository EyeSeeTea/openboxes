package org.pih.warehouse.custom.notifications

/**
 * Source that produced a CustomNotification. Stored as its name() in the
 * custom_notification.notification_type column.
 */
enum NotificationType {

    /** Shipment shipped / received. */
    SHIPMENT,

    /** Requisition pending-approval / status update. */
    REQUISITION,

    /** Fulfillment notification. */
    FULFILLMENT,

    /** Stock / expiry alerts. */
    STOCK_ALERT,

    /** User-account creation / confirmation. */
    USER_ACCOUNT,

    /** Application-error / system notification. */
    SYSTEM,

    /** New product created. */
    PRODUCT,

    /** Fallback for events not cleanly classifiable, and the legacy email-send trigger. */
    EMAIL_TRIGGER
}
