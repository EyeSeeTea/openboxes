package org.pih.warehouse.custom.notifications

import grails.gorm.transactions.Transactional
import org.pih.warehouse.core.User

class CustomNotificationService {

    void recordSendAsNotifications(Collection<String> recipientEmails, String subject, String body = null) {
        String trimmedSubject = subject?.trim()
        String safeTitle = !trimmedSubject ? '(no subject)'
            : (trimmedSubject.size() > 255 ? trimmedSubject[0..251] + '...' : trimmedSubject)
        Set<String> uniqueEmails = (recipientEmails ?: []) as Set
        // Bind a Hibernate session and transaction explicitly: this method is
        // called from MailService.doSendMail which runs on a variety of threads
        // (HTTP request, Quartz jobs, GPars workers). Background threads do
        // not have an OSIV-bound session, so GORM queries throw 'No Session
        // found for current thread' without an explicit withNewSession.
        CustomNotification.withNewSession {
            CustomNotification.withTransaction {
                uniqueEmails.each { String email ->
                    try {
                        List<User> users = User.findAllByEmail(email)
                        if (!users) {
                            users = User.findAllByUsername(email)
                        }
                        users.each { User user ->
                            CustomNotification notification = new CustomNotification(
                                user: user,
                                notificationType: NotificationType.EMAIL_TRIGGER.name(),
                                title: safeTitle,
                                body: body,
                            )
                            if (!notification.save(flush: false)) {
                                log.error "custom_notification_record_failed email='${email}' user_id='${user.id}' errors=${notification.errors.allErrors*.code}"
                            }
                        }
                    } catch (Exception ex) {
                        log.error "custom_notification_record_failed email='${email}' subject='${subject}'", ex
                    }
                }
            }
        }
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
