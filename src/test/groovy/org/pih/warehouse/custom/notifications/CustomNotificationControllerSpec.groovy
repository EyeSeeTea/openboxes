package org.pih.warehouse.custom.notifications

import grails.testing.web.controllers.ControllerUnitTest
import org.pih.warehouse.auth.AuthService
import org.pih.warehouse.core.User
import spock.lang.Specification

class CustomNotificationControllerSpec extends Specification implements ControllerUnitTest<CustomNotificationController> {

    def cleanup() {
        // Reset the static stub so it cannot leak into other specs.
        GroovySystem.metaClassRegistry.removeMetaClass(AuthService)
    }

    private void stubCurrentUser(User user) {
        AuthService.metaClass.static.getCurrentUser = { -> user }
    }

    def "list returns 401 when there is no authenticated user"() {
        given:
        stubCurrentUser(null)

        when:
        controller.list()

        then:
        response.status == 401
    }

    def "list renders the frontend DTO shape inside a data/unreadCount envelope"() {
        given:
        User user = Mock(User)
        stubCurrentUser(user)
        Date created = Date.parse("yyyy-MM-dd'T'HH:mm:ss", '2026-05-22T08:00:00')
        Map notification = [
            id              : 'abc-123',
            notificationType: NotificationType.EMAIL_TRIGGER.name(),
            title           : 'Low stock',
            body            : '<p>hi</p>',
            isRead          : false,
            dateCreated     : created,
        ]
        controller.customNotificationService = Mock(CustomNotificationService) {
            1 * listForUser(user, false, 20, 0, null, null) >> [notification]
            1 * countUnread(user) >> 1
        }

        when:
        controller.list()

        then:
        response.status == 200
        response.json.unreadCount == 1
        response.json.data.size() == 1
        with(response.json.data[0]) {
            id == 'abc-123'
            type == 'EMAIL_TRIGGER'
            title == 'Low stock'
            body == '<p>hi</p>'
            read == false
            // The frontend reads `createdAt`, not the GORM `dateCreated` property.
            createdAt != null
            !containsKey('dateCreated')
            !containsKey('isRead')
            !containsKey('linkUrl')
        }
    }

    def "markRead returns 404 when the service reports the notification is not the user's"() {
        given:
        User user = Mock(User)
        stubCurrentUser(user)
        controller.customNotificationService = Mock(CustomNotificationService) {
            1 * markRead('missing', user) >> false
        }

        when:
        controller.markRead('missing')

        then:
        response.status == 404
    }

    def "markAllRead renders the updated count"() {
        given:
        User user = Mock(User)
        stubCurrentUser(user)
        controller.customNotificationService = Mock(CustomNotificationService) {
            1 * markAllRead(user) >> 4
        }

        when:
        controller.markAllRead()

        then:
        response.status == 200
        response.json.updatedCount == 4
    }

    def "unreadCount returns 401 when there is no authenticated user"() {
        given:
        stubCurrentUser(null)
        controller.customNotificationService = Mock(CustomNotificationService)

        when:
        controller.unreadCount()

        then:
        response.status == 401
        0 * controller.customNotificationService.countUnread(_)
    }

    def "unreadCount renders the count from the service for an authenticated user"() {
        given:
        User user = Mock(User)
        stubCurrentUser(user)
        controller.customNotificationService = Mock(CustomNotificationService) {
            1 * countUnread(user) >> 7
        }

        when:
        controller.unreadCount()

        then:
        response.status == 200
        response.json.unreadCount == 7
    }
}
