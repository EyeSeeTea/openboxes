package org.pih.warehouse.custom.notifications

import grails.testing.gorm.DataTest
import grails.testing.services.ServiceUnitTest
import org.pih.warehouse.core.User
import spock.lang.Specification

/**
 * Unit-level coverage for the parts of CustomNotificationService that work against
 * the in-memory mock datastore. notifyUsers (withNewSession) and markAllRead
 * (HQL executeUpdate) need a real Hibernate datastore, so they are covered in
 * CustomNotificationControllerIntegrationSpec instead.
 */
class CustomNotificationServiceSpec extends Specification
        implements ServiceUnitTest<CustomNotificationService>, DataTest {

    def setupSpec() {
        mockDomains(CustomNotification, User)
    }

    private User buildUser(String username, String email) {
        new User(username: username, email: email, password: 'secret',
                 firstName: 'Test', lastName: 'User').save(failOnError: true)
    }

    private CustomNotification buildNotification(User user, Boolean isRead = false) {
        new CustomNotification(
            user: user,
            notificationType: NotificationType.EMAIL_TRIGGER.name(),
            title: 'Subject',
            isRead: isRead,
        ).save(failOnError: true)
    }

    def "markRead returns false when notification belongs to a different user"() {
        given:
        User owner = buildUser('owner', 'owner@example.com')
        User other = buildUser('other', 'other@example.com')
        CustomNotification notification = buildNotification(owner)

        when:
        Boolean result = service.markRead(notification.id, other)

        then:
        result == false
        CustomNotification.get(notification.id).isRead == false
    }

    def "markRead returns true and stamps readAt for the owning user"() {
        given:
        User owner = buildUser('owner2', 'owner2@example.com')
        CustomNotification notification = buildNotification(owner)

        when:
        Boolean result = service.markRead(notification.id, owner)

        then:
        result == true
        CustomNotification.get(notification.id).isRead == true
        CustomNotification.get(notification.id).readAt != null
    }

    def "countUnread counts only the user's unread notifications"() {
        given:
        User user = buildUser('eve', 'eve@example.com')
        buildNotification(user, false)
        buildNotification(user, false)
        buildNotification(user, true)

        expect:
        service.countUnread(user) == 2
    }

    private CustomNotification buildNotificationWithType(User user, String type) {
        new CustomNotification(
            user: user,
            notificationType: type,
            title: 'Subject',
            isRead: false,
        ).save(failOnError: true)
    }

    def "listForUser filters by type when type is provided"() {
        given:
        User user = buildUser('typeFilter', 'typef@example.com')
        buildNotificationWithType(user, NotificationType.SHIPMENT.name())
        buildNotificationWithType(user, NotificationType.REQUISITION.name())

        when:
        List results = service.listForUser(user, [type: NotificationType.SHIPMENT.name()])

        then:
        results.size() == 1
        results[0].notificationType == NotificationType.SHIPMENT.name()
    }

    def "listForUser returns empty when type does not match any row"() {
        given:
        User user = buildUser('typeNoMatch', 'typenom@example.com')
        buildNotificationWithType(user, NotificationType.SHIPMENT.name())

        when:
        List results = service.listForUser(user, [type: NotificationType.PRODUCT.name()])

        then:
        results.isEmpty()
    }

    def "listForUser filters by before when before is provided"() {
        given:
        User user = buildUser('beforeFilter', 'before@example.com')
        // GORM unit test: dateCreated is auto-set on save but we can verify the filter with a real date
        CustomNotification n = buildNotification(user, false)

        when:
        // before = far future — should include the notification
        List included = service.listForUser(user, [before: new Date() + 1])

        then:
        included*.id.contains(n.id)

        and:
        // before = far past — should exclude it
        List excluded = service.listForUser(user, [before: new Date() - 1])
        excluded*.id.every { it != n.id }
    }

    def "listForUser applies type + since + before as a closed range"() {
        given:
        User user = buildUser('rangeFilter', 'range@example.com')
        CustomNotification n = buildNotificationWithType(user, NotificationType.SYSTEM.name())

        when:
        List results = service.listForUser(user, [
            type  : NotificationType.SYSTEM.name(),
            since : new Date() - 1,
            before: new Date() + 1,
        ])

        then:
        results*.id.contains(n.id)
    }

    def "listForUser filters to unread when read=false"() {
        given:
        User user = buildUser('readFalse', 'readf@example.com')
        CustomNotification unread = buildNotification(user, false)
        buildNotification(user, true)

        when:
        List results = service.listForUser(user, [read: false])

        then:
        results.size() == 1
        results[0].id == unread.id
    }

    def "listForUser filters to read when read=true"() {
        given:
        User user = buildUser('readTrue', 'readt@example.com')
        buildNotification(user, false)
        CustomNotification read = buildNotification(user, true)

        when:
        List results = service.listForUser(user, [read: true])

        then:
        results.size() == 1
        results[0].id == read.id
    }

    def "listForUser read filter takes precedence over unreadOnly"() {
        given:
        User user = buildUser('readPrec', 'readp@example.com')
        buildNotification(user, false)
        CustomNotification read = buildNotification(user, true)

        when:
        List results = service.listForUser(user, [unreadOnly: true, read: true])

        then:
        results.size() == 1
        results[0].id == read.id
    }

    def "countForUser respects the read filter"() {
        given:
        User user = buildUser('countRead', 'countr@example.com')
        buildNotification(user, false)
        buildNotification(user, true)
        buildNotification(user, true)

        expect:
        service.countForUser(user, [read: true]) == 2
        service.countForUser(user, [read: false]) == 1
        service.countForUser(user, [:]) == 3
    }

    def "markUnread returns false when notification belongs to a different user"() {
        given:
        User owner = buildUser('unreadOwner', 'unread_o@example.com')
        User other = buildUser('unreadOther', 'unread_x@example.com')
        CustomNotification notification = new CustomNotification(
            user: owner,
            notificationType: NotificationType.SYSTEM.name(),
            title: 'Test',
            isRead: true,
            readAt: new Date(),
        ).save(failOnError: true)

        when:
        Boolean result = service.markUnread(notification.id, other)

        then:
        result == false
        CustomNotification.get(notification.id).isRead == true
    }

    def "markUnread sets isRead=false and clears readAt for the owning user"() {
        given:
        User owner = buildUser('unreadSelf', 'unread_s@example.com')
        CustomNotification notification = new CustomNotification(
            user: owner,
            notificationType: NotificationType.SYSTEM.name(),
            title: 'Test',
            isRead: true,
            readAt: new Date(),
        ).save(failOnError: true)

        when:
        Boolean result = service.markUnread(notification.id, owner)

        then:
        result == true
        CustomNotification.get(notification.id).isRead == false
        CustomNotification.get(notification.id).readAt == null
    }
}
