package org.pih.warehouse.custom.notifications

import grails.converters.JSON
import org.pih.warehouse.auth.AuthService

class CustomNotificationController {

    private static final List<String> ISO_DATE_FORMATS = [
        "yyyy-MM-dd'T'HH:mm:ss.SSSX",
        "yyyy-MM-dd'T'HH:mm:ssX",
    ]

    def customNotificationService

    private Date parseIsoDate(String value) {
        if (!value) {
            return null
        }
        for (String fmt : ISO_DATE_FORMATS) {
            try {
                return Date.parse(fmt, value)
            } catch (Exception ignored) {
                // try next format
            }
        }
        log.warn "custom_notifications_unparseable_date value='${value}'"
        return null
    }

    def list() {
        def currentUser = AuthService.currentUser
        if (!currentUser) {
            render status: 401
            return
        }
        Boolean unreadOnly = params.boolean('unreadOnly', false)
        Integer limit = Math.min(Math.max(params.int('limit', 20), 1), 100)
        Integer offset = Math.max(params.int('offset', 0), 0)
        Date since = parseIsoDate(params.since as String)
        Date updatedSince = parseIsoDate(params.updatedSince as String)
        List notifications = customNotificationService.listForUser(currentUser, unreadOnly, limit, offset, since, updatedSince)
        Integer unreadCount = customNotificationService.countUnread(currentUser)
        def data = notifications.collect { notification ->
            [
                id              : notification.id,
                type            : notification.notificationType,
                title           : notification.title,
                body            : notification.body,
                linkUrl         : notification.linkUrl,
                read            : notification.isRead,
                createdAt       : notification.dateCreated,
            ]
        }
        render([data: data, unreadCount: unreadCount] as JSON)
    }

    def unreadCount() {
        def currentUser = AuthService.currentUser
        if (!currentUser) {
            render status: 401
            return
        }
        render([unreadCount: customNotificationService.countUnread(currentUser)] as JSON)
    }

    def markRead(String id) {
        def currentUser = AuthService.currentUser
        if (!currentUser) {
            render status: 401
            return
        }
        Boolean success = customNotificationService.markRead(id, currentUser)
        if (!success) {
            render status: 404
            return
        }
        render status: 200
    }

    def markAllRead() {
        def currentUser = AuthService.currentUser
        if (!currentUser) {
            render status: 401
            return
        }
        Integer count = customNotificationService.markAllRead(currentUser)
        render([updatedCount: count] as JSON)
    }
}
