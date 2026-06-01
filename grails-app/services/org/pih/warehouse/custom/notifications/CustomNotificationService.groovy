package org.pih.warehouse.custom.notifications

import grails.gorm.transactions.Transactional
import org.pih.warehouse.core.User

class CustomNotificationService {

    /**
     * Record one notification per user, keyed by the User object (never by email),
     * so recipients without an email address are still notified. Independent of the
     * mail-enabled config and of any email send.
     */
    void notifyUsers(Collection<User> users, String title, String body, NotificationType type = NotificationType.EMAIL_TRIGGER) {
        if (!users) {
            return
        }
        String safeTitle = titleOrDefault(title)
        String typeName = (type ?: NotificationType.EMAIL_TRIGGER).name()
        List<User> uniqueUsers = users.findAll { it != null }.unique { it.id }
        // withTransaction participates in the caller's transaction when one exists
        // (request threads) and creates a new session+transaction when there isn't one
        // (Quartz/GPars background threads). withNewSession caused lock timeouts when
        // the caller held a write lock on the user row (e.g. handleSignup).
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
                    // Catches runtime exceptions only. Hibernate constraint violations mark
                    // the transaction for rollback before this catch executes, so a DB-level
                    // failure can still roll back the entire batch.
                    log.error "custom_notification_record_failed user_id='${user?.id}' title='${title}'", ex
                }
            }
        }
    }

    private static String titleOrDefault(String title) {
        String trimmed = title?.trim()
        return !trimmed ? '(no subject)' : (trimmed.size() > 255 ? trimmed[0..251] + '...' : trimmed)
    }

    List<CustomNotification> listForUser(User user, Map params = [:]) {
        Boolean unreadOnly = params.containsKey('unreadOnly') ? params.unreadOnly as Boolean : false
        Boolean read = params.read != null ? params.read as Boolean : null
        Integer limit = params.containsKey('limit') ? (params.limit as Integer) : 20
        Integer offset = params.containsKey('offset') ? (params.offset as Integer) : 0
        Date since = params.since as Date
        Date updatedSince = params.updatedSince as Date
        String type = params.type as String
        Date before = params.before as Date

        CustomNotification.createCriteria().list(max: limit, offset: offset) {
            eq('user', user)
            // Explicit read/unread filter takes precedence over the legacy unreadOnly flag.
            if (read != null) {
                eq('isRead', read)
            } else if (unreadOnly) {
                eq('isRead', false)
            }
            if (since) {
                ge('dateCreated', since)
            }
            if (updatedSince) {
                ge('lastUpdated', updatedSince)
            }
            if (type) {
                eq('notificationType', type)
            }
            if (before) {
                le('dateCreated', before)
            }
            order('dateCreated', 'desc')
        }
    }

    @Transactional
    Boolean markUnread(String notificationId, User user) {
        CustomNotification notification = CustomNotification.get(notificationId)
        if (!notification || notification.user?.id != user.id) {
            return false
        }
        notification.isRead = false
        notification.readAt = null
        // failOnError: a genuine persistence failure throws (→ 500), keeping it
        // distinct from the ownership miss above (→ false → 404). Never report success.
        notification.save(flush: true, failOnError: true)
        return true
    }

    @Transactional
    Boolean markRead(String notificationId, User user) {
        CustomNotification notification = CustomNotification.get(notificationId)
        if (!notification || notification.user?.id != user.id) {
            return false
        }
        notification.isRead = true
        notification.readAt = new Date()
        notification.save(flush: true, failOnError: true)
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

    Integer countForUser(User user, Map params = [:]) {
        Boolean unreadOnly = params.containsKey('unreadOnly') ? params.unreadOnly as Boolean : false
        Boolean read = params.read != null ? params.read as Boolean : null
        String type = params.type as String
        Date since = params.since as Date
        Date before = params.before as Date
        CustomNotification.createCriteria().count() {
            eq('user', user)
            if (read != null) {
                eq('isRead', read)
            } else if (unreadOnly) {
                eq('isRead', false)
            }
            if (since) {
                ge('dateCreated', since)
            }
            if (type) {
                eq('notificationType', type)
            }
            if (before) {
                le('dateCreated', before)
            }
        }
    }
}
