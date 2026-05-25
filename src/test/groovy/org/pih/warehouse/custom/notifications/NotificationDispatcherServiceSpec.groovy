package org.pih.warehouse.custom.notifications

import grails.testing.services.ServiceUnitTest
import org.pih.warehouse.core.User
import spock.lang.Specification

/**
 * Unit coverage for NotificationDispatcherService: the in-app channel always
 * fires; the email channel only fires when sendEmail is true and at least one
 * recipient has an email address.
 */
class NotificationDispatcherServiceSpec extends Specification
        implements ServiceUnitTest<NotificationDispatcherService> {

    private static final String TITLE = 'Shipment shipped'
    private static final String BODY = '<p>body</p>'

    def customNotificationService = Mock(CustomNotificationService)
    def mailService = Mock(MailServiceStub)

    def setup() {
        service.customNotificationService = customNotificationService
        service.mailService = mailService
    }

    private User userWithEmail(String email = 'recipient@example.com') {
        new User(username: 'u-' + email, email: email)
    }

    private User userWithoutEmail() {
        new User(username: 'no-email', email: null)
    }

    def "notify with default sendEmail records in-app and emails only recipients with an email"() {
        given:
        User withEmail = userWithEmail('recipient@example.com')
        User withoutEmail = userWithoutEmail()
        List<User> users = [withEmail, withoutEmail]

        when:
        service.notify(users, TITLE, BODY, NotificationType.SHIPMENT)

        then:
        1 * customNotificationService.notifyUsers(users, TITLE, BODY, NotificationType.SHIPMENT)
        1 * mailService.sendHtmlMail(TITLE, BODY, ['recipient@example.com'])
    }

    def "notify with sendEmail false records in-app and never emails"() {
        given:
        List<User> users = [userWithEmail('recipient@example.com')]

        when:
        service.notify(users, TITLE, BODY, NotificationType.STOCK_ALERT, false)

        then:
        1 * customNotificationService.notifyUsers(users, TITLE, BODY, NotificationType.STOCK_ALERT)
        0 * mailService.sendHtmlMail(_, _, _)
    }

    def "notify does not email when no recipient has an email address"() {
        given:
        List<User> users = [userWithoutEmail()]

        when:
        service.notify(users, TITLE, BODY, NotificationType.SYSTEM)

        then:
        1 * customNotificationService.notifyUsers(users, TITLE, BODY, NotificationType.SYSTEM)
        0 * mailService.sendHtmlMail(_, _, _)
    }

    // Local stub type so the Mock exposes the sendHtmlMail(String, String, Collection)
    // overload without pulling the upstream MailService onto the unit-test classpath.
    static class MailServiceStub {
        Boolean sendHtmlMail(String subject, String body, Collection to) { return true }
    }
}
