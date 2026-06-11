package org.pih.warehouse.custom.dhis2auth

import grails.gorm.transactions.Transactional
import org.pih.warehouse.core.User
import org.pih.warehouse.custom.dhis2auth.Dhis2OAuthService.Dhis2OAuthException
import org.pih.warehouse.custom.dhis2auth.Dhis2OAuthService.Dhis2User

@Transactional
class Dhis2RegistrationService {

    private static final String PLACEHOLDER_LAST_NAME = '(DHIS2)'
    private static final int MAX_USERNAME_SUFFIX = 20

    User findOrRegister(Dhis2User dhis2User) {
        dhis2User.uid ? findOrRegisterByUid(dhis2User) : findOrRegisterByUsername(dhis2User)
    }

    private User findOrRegisterByUid(Dhis2User dhis2User) {
        Dhis2UserLink link = Dhis2UserLink.findByDhis2Uid(dhis2User.uid)
        link ? refreshUser(link, dhis2User) : register(dhis2User)
    }

    private User findOrRegisterByUsername(Dhis2User dhis2User) {
        Dhis2UserLink link = Dhis2UserLink.findByDhis2Username(dhis2User.username)
        if (!link) {
            return registerByUsername(dhis2User)
        }
        if (link.deactivatedAt) {
            return reactivateTombstonedLink(link, dhis2User)
        }
        link.lastLoginAt = new Date()
        link.save(flush: true, failOnError: true)
        link.user
    }

    // The username may have been recycled to a different person, so force
    // re-approval (inactive) instead of silently re-establishing the old account.
    private User reactivateTombstonedLink(Dhis2UserLink link, Dhis2User dhis2User) {
        link.deactivatedAt = null
        link.lastLoginAt = new Date()
        if (link.user.active) {
            link.user.active = false
            link.user.save(flush: true, failOnError: true)
        }
        link.save(flush: true, failOnError: true)
        log.warn "dhis2_username_link_pending_recheck username=${dhis2User.username} userId=${link.user.id}"
        link.user
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
        User user = createDhis2User(username, firstName, lastName, dhis2User.email ?: '', dhis2User.uid, dhis2User.username)
        log.info "dhis2_user_registered uid=${dhis2User.uid} username=${username} active=false"
        user
    }

    // v42: identity is the DHIS2 username only (no UID/name/email reachable). OB's User
    // requires first/last name, so we synthesise placeholders the admin fills at activation.
    private User registerByUsername(Dhis2User dhis2User) {
        String username = resolveUsername(dhis2User.username)
        User user = createDhis2User(username, dhis2User.username, PLACEHOLDER_LAST_NAME, '', null, dhis2User.username)
        log.info "dhis2_user_registered_by_username username=${username} active=false"
        user
    }

    private User createDhis2User(String username, String firstName, String lastName, String email,
                                 String dhis2Uid, String dhis2Username) {
        // password must be non-blank per User constraints — DHIS2-only users get a locked sentinel
        User user = new User(
            username: username,
            firstName: firstName,
            lastName: lastName,
            email: email,
            active: false,
            password: '*DHIS2*',
            passwordConfirm: '*DHIS2*',
        )
        user.save(flush: true, failOnError: true)

        new Dhis2UserLink(
            user: user,
            dhis2Uid: dhis2Uid,
            dhis2Username: dhis2Username,
            lastLoginAt: new Date(),
        ).save(flush: true, failOnError: true)
        user
    }

    private String resolveUsername(String dhis2Username) {
        if (!User.findByUsername(dhis2Username)) {
            return dhis2Username
        }
        Integer attempt = (1..MAX_USERNAME_SUFFIX).find { !User.findByUsername(suffixedUsername(dhis2Username, it)) }
        if (attempt == null) {
            throw new Dhis2OAuthException("Could not allocate a unique OB username for DHIS2 user ${dhis2Username}")
        }
        String suffixed = suffixedUsername(dhis2Username, attempt)
        log.warn "dhis2_username_collision dhis2_username=${dhis2Username} using_suffix=${suffixed}"
        suffixed
    }

    private static String suffixedUsername(String dhis2Username, int attempt) {
        attempt == 1 ? "${dhis2Username}-dhis2" : "${dhis2Username}-dhis2-${attempt}"
    }

    private static List<String> parseDisplayName(String displayName) {
        if (!displayName) return ['', '']
        List<String> parts = displayName.split(' ', 2) as List<String>
        [parts[0], parts.size() > 1 ? parts[1] : '']
    }
}
