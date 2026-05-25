package org.pih.warehouse.custom.notifications

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import org.pih.warehouse.common.base.IntegrationSpec

@Integration
@Rollback
class CustomNotificationControllerIntegrationSpec extends IntegrationSpec {

    CustomNotificationService customNotificationService

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

    def "recordSendAsNotifications creates one notification per matched user and skips unknown recipients"() {
        given:
        def alice = buildUser('alice@example.com')
        def bob = buildUser('bob@example.com')

        when:
        customNotificationService.recordSendAsNotifications(
            ['alice@example.com', 'bob@example.com', 'unknown@example.com'], 'Hello', '<p>body</p>')

        then:
        customNotificationService.countUnread(alice) == 1
        customNotificationService.countUnread(bob) == 1
        CustomNotification.findByUser(alice).title == 'Hello'
        CustomNotification.findByUser(alice).body == '<p>body</p>'
    }

    def "recordSendAsNotifications falls back to username when no email matches"() {
        given:
        def carol = buildUser('carol.internal@example.com', 'carol@example.com')

        when:
        customNotificationService.recordSendAsNotifications(['carol@example.com'], 'Fallback', null)

        then:
        customNotificationService.countUnread(carol) == 1
    }

    def "recordSendAsNotifications deduplicates repeated recipient addresses"() {
        given:
        def dave = buildUser('dave@example.com')

        when:
        customNotificationService.recordSendAsNotifications(
            ['dave@example.com', 'dave@example.com'], 'Dedup', null)

        then:
        customNotificationService.countUnread(dave) == 1
    }

    // -------------------------------------------------------------------------
    // recordSendAsNotifications — subject fallback / truncation
    // -------------------------------------------------------------------------

    private static final String NO_SUBJECT_TITLE = '(no subject)'
    private static final int MAX_TITLE_LENGTH = 255

    def "recordSendAsNotifications stores '(no subject)' when subject is null"() {
        given:
        def user = buildUser('null-subject@example.com')

        when:
        customNotificationService.recordSendAsNotifications(['null-subject@example.com'], null, null)

        then:
        CustomNotification.findByUser(user).title == NO_SUBJECT_TITLE
    }

    def "recordSendAsNotifications stores '(no subject)' when subject is blank whitespace"() {
        given:
        def user = buildUser('blank-subject@example.com')

        when:
        customNotificationService.recordSendAsNotifications(['blank-subject@example.com'], '   ', null)

        then:
        CustomNotification.findByUser(user).title == NO_SUBJECT_TITLE
    }

    def "recordSendAsNotifications truncates a subject longer than 255 characters to 252 chars plus ellipsis"() {
        given:
        def user = buildUser('long-subject@example.com')
        String longSubject = 'A' * 300
        String expectedTitle = ('A' * 252) + '...'

        when:
        customNotificationService.recordSendAsNotifications(['long-subject@example.com'], longSubject, null)

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
