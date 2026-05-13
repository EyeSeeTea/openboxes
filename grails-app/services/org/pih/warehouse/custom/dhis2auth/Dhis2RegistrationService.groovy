package org.pih.warehouse.custom.dhis2auth

import grails.gorm.transactions.Transactional
import org.pih.warehouse.core.User
import org.pih.warehouse.custom.dhis2auth.Dhis2OAuthClient.Dhis2User

@Transactional
class Dhis2RegistrationService {

    User findOrRegister(Dhis2User dhis2User) {
        Dhis2UserLink link = Dhis2UserLink.findByDhis2Uid(dhis2User.uid)
        if (link) {
            return refreshUser(link, dhis2User)
        }
        return register(dhis2User)
    }

    private User refreshUser(Dhis2UserLink link, Dhis2User dhis2User) {
        User user = link.user
        String[] nameParts = parseDisplayName(dhis2User.displayName)
        user.firstName = nameParts[0]
        user.lastName = nameParts[1]
        if (dhis2User.email) {
            user.email = dhis2User.email
        }

        if (dhis2User.username != link.dhis2Username) {
            String newUsername = dhis2User.username
            User collision = User.findByUsername(newUsername)
            if (!collision || collision.id == user.id) {
                user.username = newUsername
            } else {
                log.warn "dhis2_username_collision uid=${dhis2User.uid} requested_username=${newUsername} kept_username=${user.username}"
            }
        }

        link.dhis2Username = dhis2User.username
        link.lastLoginAt = new Date()
        link.save(flush: true, failOnError: true)
        user.save(flush: true, failOnError: true)
        user
    }

    private User register(Dhis2User dhis2User) {
        String[] nameParts = parseDisplayName(dhis2User.displayName)
        String username = resolveUsername(dhis2User.username)

        User user = new User(
            username: username,
            firstName: nameParts[0],
            lastName: nameParts[1],
            email: dhis2User.email ?: '',
            active: false,
            // password must be non-blank per User constraints — set a locked value
            password: '*DHIS2*',
            passwordConfirm: '*DHIS2*',
        )
        user.save(flush: true, failOnError: true)

        Dhis2UserLink link = new Dhis2UserLink(
            user: user,
            dhis2Uid: dhis2User.uid,
            dhis2Username: dhis2User.username,
            lastLoginAt: new Date(),
        )
        link.save(flush: true, failOnError: true)

        log.info "dhis2_user_registered uid=${dhis2User.uid} username=${username} active=false"
        user
    }

    private String resolveUsername(String dhis2Username) {
        if (!User.findByUsername(dhis2Username)) {
            return dhis2Username
        }
        String suffixed = "${dhis2Username}-dhis2"
        log.warn "dhis2_username_collision dhis2_username=${dhis2Username} using_suffix=${suffixed}"
        suffixed
    }

    private static String[] parseDisplayName(String displayName) {
        if (!displayName) {
            return ['', ''] as String[]
        }
        int spaceIdx = displayName.indexOf(' ')
        if (spaceIdx < 0) {
            return [displayName, ''] as String[]
        }
        [displayName.substring(0, spaceIdx), displayName.substring(spaceIdx + 1)] as String[]
    }
}
