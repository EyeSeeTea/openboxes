package org.pih.warehouse.custom.notifications

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import org.pih.warehouse.common.base.IntegrationSpec

@Integration
@Rollback
class CustomNotificationControllerIntegrationSpec extends IntegrationSpec {

    CustomNotificationService customNotificationService
    def grailsApplication

    def "customNotificationService bean is available in the application context"() {
        expect:
        customNotificationService != null
    }

    def "listForUser returns empty list when user has no notifications"() {
        given:
        def user = buildUser()

        when:
        List results = customNotificationService.listForUser(user, false, 20)

        then:
        results.size() == 0
    }

    def "listForUser returns unread-only notifications when unreadOnly is true"() {
        given:
        def user = buildUser()
        new CustomNotification(user: user, notificationType: NotificationType.EMAIL_TRIGGER.name(), title: 'Unread', isRead: false).save(failOnError: true)
        new CustomNotification(user: user, notificationType: NotificationType.EMAIL_TRIGGER.name(), title: 'Already read', isRead: true).save(failOnError: true)

        when:
        List unread = customNotificationService.listForUser(user, true, 20)
        List all = customNotificationService.listForUser(user, false, 20)

        then:
        unread.size() == 1
        unread[0].title == 'Unread'
        all.size() == 2
    }

    def "listForUser respects the limit parameter"() {
        given:
        def user = buildUser()
        5.times { i ->
            new CustomNotification(user: user, notificationType: NotificationType.EMAIL_TRIGGER.name(), title: "Msg ${i}", isRead: false).save(failOnError: true)
        }

        when:
        List results = customNotificationService.listForUser(user, false, 3)

        then:
        results.size() == 3
    }

    def "listForUser pages with offset returning newest first and no overlap"() {
        given:
        def user = buildUser()
        Date base = Date.parse("yyyy-MM-dd'T'HH:mm:ss", '2026-05-22T08:00:00')
        // Insert oldest-to-newest with explicit dateCreated so desc order is deterministic.
        4.times { i ->
            CustomNotification n = new CustomNotification(
                user: user, notificationType: NotificationType.EMAIL_TRIGGER.name(), title: "Msg ${i}", isRead: false
            ).save(failOnError: true)
            n.dateCreated = new Date(base.time + (i * 60000))
            n.save(failOnError: true, flush: true)
        }

        when:
        List firstPage = customNotificationService.listForUser(user, false, 2, 0)
        List secondPage = customNotificationService.listForUser(user, false, 2, 2)

        then:
        firstPage*.title == ['Msg 3', 'Msg 2']
        secondPage*.title == ['Msg 1', 'Msg 0']
        (firstPage*.id).intersect(secondPage*.id) == []
    }

    def "markRead returns false when notification belongs to a different user"() {
        given:
        def owner = buildUser()
        def intruder = buildUser()
        CustomNotification notification = new CustomNotification(
            user: owner, notificationType: NotificationType.EMAIL_TRIGGER.name(), title: 'Private', isRead: false
        ).save(failOnError: true)

        when:
        Boolean result = customNotificationService.markRead(notification.id, intruder)

        then:
        result == false
        CustomNotification.get(notification.id).isRead == false
    }

    def "markRead marks the notification as read for the owner"() {
        given:
        def user = buildUser()
        CustomNotification notification = new CustomNotification(
            user: user, notificationType: NotificationType.EMAIL_TRIGGER.name(), title: 'To read', isRead: false
        ).save(failOnError: true)

        when:
        Boolean result = customNotificationService.markRead(notification.id, user)

        then:
        result == true
        CustomNotification.get(notification.id).isRead == true
        CustomNotification.get(notification.id).readAt != null
    }

    def "countUnread returns 0 for a user with no notifications"() {
        given:
        def user = buildUser()

        expect:
        customNotificationService.countUnread(user) == 0
    }

    def "markAllRead marks all unread notifications for the user"() {
        given:
        def user = buildUser()
        3.times {
            new CustomNotification(user: user, notificationType: NotificationType.EMAIL_TRIGGER.name(), title: 'Msg', isRead: false).save(failOnError: true)
        }

        when:
        Integer updated = customNotificationService.markAllRead(user)

        then:
        updated == 3
        customNotificationService.countUnread(user) == 0
    }

    private static final String NO_SUBJECT_TITLE = '(no subject)'
    private static final int MAX_TITLE_LENGTH = 255

    // -------------------------------------------------------------------------
    // notifyUsers — keyed by User, reaches email-less recipients, flag-gated
    // -------------------------------------------------------------------------

    def "notifyUsers creates one notification per user including a user with no email"() {
        given:
        def withEmail = buildUser('withemail@example.com')
        def withoutEmail = buildUser()

        when:
        customNotificationService.notifyUsers([withEmail, withoutEmail], 'Hello', '<p>body</p>', NotificationType.SHIPMENT)

        then:
        customNotificationService.countUnread(withEmail) == 1
        customNotificationService.countUnread(withoutEmail) == 1
        CustomNotification.findByUser(withEmail).title == 'Hello'
        CustomNotification.findByUser(withEmail).body == '<p>body</p>'
        CustomNotification.findByUser(withEmail).notificationType == NotificationType.SHIPMENT.name()
    }

    def "notifyUsers deduplicates repeated users by id"() {
        given:
        def dave = buildUser('dave@example.com')

        when:
        customNotificationService.notifyUsers([dave, dave], 'Dedup', null, NotificationType.SYSTEM)

        then:
        customNotificationService.countUnread(dave) == 1
    }

    def "notifyUsers writes zero rows when the in-app flag is disabled"() {
        given:
        def user = buildUser('flagoff@example.com')
        def previous = grailsApplication.config.openboxes.custom.notifications.inApp.enabled
        grailsApplication.config.openboxes.custom.notifications.inApp.enabled = false

        when:
        customNotificationService.notifyUsers([user], 'Disabled', null, NotificationType.SYSTEM)

        then:
        customNotificationService.countUnread(user) == 0

        cleanup:
        grailsApplication.config.openboxes.custom.notifications.inApp.enabled = previous
    }

    def "notifyUsers stores '(no subject)' when title is null"() {
        given:
        def user = buildUser('null-subject@example.com')

        when:
        customNotificationService.notifyUsers([user], null, null, NotificationType.SYSTEM)

        then:
        CustomNotification.findByUser(user).title == NO_SUBJECT_TITLE
    }

    def "notifyUsers stores '(no subject)' when title is blank whitespace"() {
        given:
        def user = buildUser('blank-subject@example.com')

        when:
        customNotificationService.notifyUsers([user], '   ', null, NotificationType.SYSTEM)

        then:
        CustomNotification.findByUser(user).title == NO_SUBJECT_TITLE
    }

    def "notifyUsers truncates a title longer than 255 characters to 252 chars plus ellipsis"() {
        given:
        def user = buildUser('long-subject@example.com')
        String longTitle = 'A' * 300
        String expectedTitle = ('A' * 252) + '...'

        when:
        customNotificationService.notifyUsers([user], longTitle, null, NotificationType.SYSTEM)

        then:
        CustomNotification notification = CustomNotification.findByUser(user)
        notification.title.length() == MAX_TITLE_LENGTH
        notification.title == expectedTitle
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private org.pih.warehouse.core.User buildUser(String email = null, String username = null) {
        build(org.pih.warehouse.core.User, [
            username: username ?: "testuser${System.nanoTime()}",
            email   : email,
        ])
    }
}
