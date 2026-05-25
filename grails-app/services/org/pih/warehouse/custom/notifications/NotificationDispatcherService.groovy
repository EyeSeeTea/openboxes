package org.pih.warehouse.custom.notifications

import org.pih.warehouse.core.User

/**
 * Single entry point for notifying users. Always records an in-app notification
 * (keyed by User, so recipients without an email are still notified) and, unless
 * sendEmail is false, also sends an email to the recipients that have one.
 *
 * Call sites that send their own specialised email — per-recipient bodies, CSV
 * attachments, etc. — pass sendEmail = false so only the in-app channel is added
 * and their existing email behaviour is left untouched.
 */
class NotificationDispatcherService {

    static transactional = false

    def customNotificationService
    def mailService

    void notify(Collection<User> users, String title, String body, NotificationType type, boolean sendEmail = true) {
        // In-app channel: self-gated by openboxes.notifications.inApp.enabled and
        // independent of the mail configuration or send success.
        customNotificationService.notifyUsers(users, title, body, type)

        if (sendEmail) {
            List<String> emails = users?.findAll { it?.email }*.email?.unique()
            if (emails) {
                // Email channel: self-gated by MailService.isMailEnabled.
                mailService.sendHtmlMail(title, body, emails)
            }
        }
    }
}
