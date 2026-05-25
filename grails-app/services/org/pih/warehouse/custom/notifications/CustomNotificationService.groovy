package org.pih.warehouse.custom.notifications

import grails.gorm.transactions.Transactional
import org.pih.warehouse.core.User

class CustomNotificationService {

    def grailsApplication

    /**
     * Record one notification per user, keyed by the User object (never by email),
     * so recipients without an email address are still notified. Independent of the
     * mail-enabled config and of any email send. No-ops when the in-app flag is off.
     */
    void notifyUsers(Collection<User> users, String title, String body, NotificationType type = NotificationType.EMAIL_TRIGGER) {
        if (!inAppNotificationsEnabled || !users) {
            return
        }
        String safeTitle = titleOrDefault(title)
        String typeName = (type ?: NotificationType.EMAIL_TRIGGER).name()
        List<User> uniqueUsers = users.findAll { it != null }.unique { it.id }
        // withNewSession: callers include GPars/Quartz background threads with no
        // OSIV-bound session, so GORM saves would otherwise throw 'No Session
        // found for current thread'.
        CustomNotification.withNewSession {
            CustomNotification.withTransaction {
                uniqueUsers.each { User user ->
                    try {
                        CustomNotification notification = new CustomNotification(
                            user: user,
                            notificationType: typeName,
                            title: safeTitle,
                            body: body,
                        )
                        if (!notification.save(flush: false)) {
                            log.error "custom_notification_record_failed user_id='${user.id}' errors=${notification.errors.allErrors*.code}"
                        }
                    } catch (Exception ex) {
                        log.error "custom_notification_record_failed user_id='${user?.id}' title='${title}'", ex
                    }
                }
            }
        }
    }

    private boolean isInAppNotificationsEnabled() {
        def enabled = grailsApplication.config.openboxes.custom.notifications.inApp.enabled
        // Default to enabled when the key is absent (config returns an empty ConfigObject).
        return enabled instanceof Boolean ? enabled : true
    }

    private static String titleOrDefault(String title) {
        String trimmed = title?.trim()
        return !trimmed ? '(no subject)' : (trimmed.size() > 255 ? trimmed[0..251] + '...' : trimmed)
    }

    List<CustomNotification> listForUser(User user, Boolean unreadOnly = false, Integer limit = 20, Integer offset = 0, Date since = null, Date updatedSince = null) {
        CustomNotification.createCriteria().list(max: limit, offset: offset) {
            eq('user', user)
            if (unreadOnly) {
                eq('isRead', false)
            }
            if (since) {
                ge('dateCreated', since)
            }
            if (updatedSince) {
                ge('lastUpdated', updatedSince)
            }
            order('dateCreated', 'desc')
        }
    }

    @Transactional
    Boolean markRead(String notificationId, User user) {
        CustomNotification notification = CustomNotification.get(notificationId)
        if (!notification || notification.user?.id != user.id) {
            return false
        }
        notification.isRead = true
        notification.readAt = new Date()
        notification.save(flush: true)
        return true
    }

    @Transactional
    Integer markAllRead(User user) {
        CustomNotification.executeUpdate(
            'update CustomNotification n set n.isRead = true, n.readAt = :now where n.user = :user and n.isRead = false',
            [now: new Date(), user: user]
        )
    }

    Integer countUnread(User user) {
        CustomNotification.countByUserAndIsRead(user, false)
    }
}
