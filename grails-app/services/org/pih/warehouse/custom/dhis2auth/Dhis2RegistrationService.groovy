package org.pih.warehouse.custom.dhis2auth

import org.pih.warehouse.core.User
import org.pih.warehouse.custom.dhis2auth.Dhis2OAuthService.Dhis2User

// Transactional by default per Grails conventions — do not add @Transactional.
class Dhis2RegistrationService {

    User findOrRegister(Dhis2User dhis2User) {
        Dhis2UserLink link = Dhis2UserLink.findByDhis2Uid(dhis2User.uid)
        link ? refreshUser(link, dhis2User) : register(dhis2User)
    }

    private User refreshUser(Dhis2UserLink link, Dhis2User dhis2User) {
        User user = link.user
        def (String firstName, String lastName) = parseDisplayName(dhis2User.displayName)
        user.firstName = firstName
        user.lastName = lastName
        if (dhis2User.email) {
            user.email = dhis2User.email
        }

        if (dhis2User.username != link.dhis2Username) {
            User collision = User.findByUsername(dhis2User.username)
            if (!collision || collision.id == user.id) {
                user.username = dhis2User.username
            } else {
                log.warn "dhis2_username_collision uid=${dhis2User.uid} requested_username=${dhis2User.username} kept_username=${user.username}"
            }
        }

        link.dhis2Username = dhis2User.username
        link.lastLoginAt = new Date()
        link.save(flush: true, failOnError: true)
        if (user.isDirty()) {
            user.save(flush: true, failOnError: true)
        }
        user
    }

    private User register(Dhis2User dhis2User) {
        def (String firstName, String lastName) = parseDisplayName(dhis2User.displayName)
        String username = resolveUsername(dhis2User.username)

        // password must be non-blank per User constraints — DHIS2-only users get a locked sentinel
        User user = new User(
            username: username,
            firstName: firstName,
            lastName: lastName,
            email: dhis2User.email ?: '',
            active: false,
            password: '*DHIS2*',
            passwordConfirm: '*DHIS2*',
        )
        user.save(flush: true, failOnError: true)

        new Dhis2UserLink(
            user: user,
            dhis2Uid: dhis2User.uid,
            dhis2Username: dhis2User.username,
            lastLoginAt: new Date(),
        ).save(flush: true, failOnError: true)

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

    private static List<String> parseDisplayName(String displayName) {
        if (!displayName) return ['', '']
        List<String> parts = displayName.split(' ', 2) as List<String>
        [parts[0], parts.size() > 1 ? parts[1] : '']
    }
}
