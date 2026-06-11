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
        // Strip CR/LF and cap length so a crafted param can't forge log lines.
        log.warn "custom_notifications_unparseable_date value='${value.take(64).replaceAll(/[\r\n]/, ' ')}'"
        return null
    }

    def list() {
        def currentUser = AuthService.currentUser
        if (!currentUser) {
            render status: 401
            return
        }
        Boolean unreadOnly = params.boolean('unreadOnly', false)
        Boolean read = params.boolean('read')
        Integer limit = Math.min(Math.max(params.int('limit', 20), 1), 100)
        Integer offset = Math.max(params.int('offset', 0), 0)
        Date since = parseIsoDate(params.since as String)
        Date updatedSince = parseIsoDate(params.updatedSince as String)
        Date before = parseIsoDate(params.before as String)
        String rawType = params.type as String
        String type = rawType ? (NotificationType.values()*.name().contains(rawType) ? rawType : null) : null
        // An unrecognised type value returns an empty list rather than an unfiltered list.
        if (rawType && !type) {
            render([data: [], unreadCount: customNotificationService.countUnread(currentUser)] as JSON)
            return
        }
        Map filters = [unreadOnly: unreadOnly, read: read, type: type, since: since, before: before]
        List notifications = customNotificationService.listForUser(currentUser,
            filters + [limit: limit, offset: offset, updatedSince: updatedSince])
        Integer unreadCount = customNotificationService.countUnread(currentUser)
        Integer totalCount = customNotificationService.countForUser(currentUser, filters)
        def data = notifications.collect { notification ->
            [
                id              : notification.id,
                type            : notification.notificationType,
                title           : notification.title,
                body            : notification.body,
                read            : notification.isRead,
                createdAt       : notification.dateCreated,
            ]
        }
        render([data: data, unreadCount: unreadCount, totalCount: totalCount] as JSON)
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

    def markUnread(String id) {
        def currentUser = AuthService.currentUser
        if (!currentUser) {
            render status: 401
            return
        }
        Boolean success = customNotificationService.markUnread(id, currentUser)
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
