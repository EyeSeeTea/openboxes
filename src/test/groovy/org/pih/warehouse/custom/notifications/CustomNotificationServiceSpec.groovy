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
}
