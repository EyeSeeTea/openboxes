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
            1 * listForUser(user, [unreadOnly: false, read: null, limit: 20, offset: 0, since: null,
                                   updatedSince: null, type: null, before: null]) >> [notification]
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

    def "list passes type and before through to the service"() {
        given:
        User user = Mock(User)
        stubCurrentUser(user)
        Date before = Date.parse("yyyy-MM-dd", '2026-06-01')
        params.type = 'SHIPMENT'
        params.before = '2026-06-01T00:00:00Z'
        controller.customNotificationService = Mock(CustomNotificationService) {
            1 * listForUser(user, { Map p -> p.type == 'SHIPMENT' && p.before != null }) >> []
            1 * countUnread(user) >> 0
        }

        when:
        controller.list()

        then:
        response.status == 200
        response.json.data.size() == 0
    }

    def "list passes the read filter through to the service"() {
        given:
        User user = Mock(User)
        stubCurrentUser(user)
        params.read = 'true'
        controller.customNotificationService = Mock(CustomNotificationService) {
            1 * listForUser(user, { Map p -> p.read == true }) >> []
            1 * countUnread(user) >> 0
        }

        when:
        controller.list()

        then:
        response.status == 200
        response.json.data.size() == 0
    }

    def "list returns empty result for an unrecognised type without calling the service list"() {
        given:
        User user = Mock(User)
        stubCurrentUser(user)
        params.type = 'NOT_A_REAL_TYPE'
        controller.customNotificationService = Mock(CustomNotificationService) {
            0 * listForUser(*_)
            1 * countUnread(user) >> 0
        }

        when:
        controller.list()

        then:
        response.status == 200
        response.json.data.size() == 0
    }

    def "markUnread returns 401 when there is no authenticated user"() {
        given:
        stubCurrentUser(null)

        when:
        controller.markUnread('some-id')

        then:
        response.status == 401
    }

    def "markUnread returns 404 when the service reports the notification is not the user's"() {
        given:
        User user = Mock(User)
        stubCurrentUser(user)
        controller.customNotificationService = Mock(CustomNotificationService) {
            1 * markUnread('other-id', user) >> false
        }

        when:
        controller.markUnread('other-id')

        then:
        response.status == 404
    }

    def "markUnread returns 200 on success"() {
        given:
        User user = Mock(User)
        stubCurrentUser(user)
        controller.customNotificationService = Mock(CustomNotificationService) {
            1 * markUnread('notif-1', user) >> true
        }

        when:
        controller.markUnread('notif-1')

        then:
        response.status == 200
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
