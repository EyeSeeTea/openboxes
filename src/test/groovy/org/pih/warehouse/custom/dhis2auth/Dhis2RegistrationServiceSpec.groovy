package org.pih.warehouse.custom.dhis2auth

import grails.testing.gorm.DataTest
import grails.testing.services.ServiceUnitTest
import org.pih.warehouse.core.Person
import org.pih.warehouse.core.User
import org.pih.warehouse.custom.dhis2auth.Dhis2OAuthService.Dhis2OAuthException
import org.pih.warehouse.custom.dhis2auth.Dhis2OAuthService.Dhis2User
import spock.lang.Specification
import spock.lang.Unroll

class Dhis2RegistrationServiceSpec extends Specification
    implements ServiceUnitTest<Dhis2RegistrationService>, DataTest {

    Class[] getDomainClassesToMock() {
        [Person, User, Dhis2UserLink]
    }

    @Unroll
    void "findOrRegister: #scenario"() {
        given:
        if (existingUser) {
            existingUser.save(flush: true, failOnError: true)
            if (existingLink) {
                existingLink.user = existingUser
                existingLink.save(flush: true, failOnError: true)
            }
        }

        when:
        User result = service.findOrRegister(dhis2User)

        then:
        result.username == expectedUsername
        result.active == expectedActive
        Dhis2UserLink.findByDhis2Uid(dhis2User.uid)?.user?.id == result.id

        where:
        scenario                  | dhis2User                                                       | existingUser                                    | existingLink                                                                             | expectedUsername | expectedActive
        "new user"                | new Dhis2User(uid: 'NEWUID1234A', username: 'alice', displayName: 'Alice Doe', email: 'a@b.com')   | null                                            | null                                                                                     | 'alice'          | false
        "returning user"          | new Dhis2User(uid: 'RETUID1234B', username: 'bob', displayName: 'Bob Smith', email: 'b@c.com')     | savedUser('bob', 'Bob', 'Old', 'old@c.com')     | new Dhis2UserLink(dhis2Uid: 'RETUID1234B', dhis2Username: 'bob')                         | 'bob'            | true
        "returning with new email"| new Dhis2User(uid: 'EMAILUID12C', username: 'carol', displayName: 'Carol New', email: 'new@c.com') | savedUser('carol', 'Carol', 'Old', 'old@c.com') | new Dhis2UserLink(dhis2Uid: 'EMAILUID12C', dhis2Username: 'carol')                       | 'carol'          | true
        "username collision"      | new Dhis2User(uid: 'COLLUID123D', username: 'existing', displayName: 'New Guy', email: 'n@g.com')  | savedUser('existing', 'Exist', 'Ing', 'e@g.com')| null                                                                                     | 'existing-dhis2' | false
    }

    void "returning user with updated email gets email refreshed"() {
        given:
        User user = savedUser('dan', 'Dan', 'Old', 'old@d.com')
        user.active = true
        user.save(flush: true)
        Dhis2UserLink link = new Dhis2UserLink(user: user, dhis2Uid: 'EMAILTST1DE', dhis2Username: 'dan')
        link.save(flush: true, failOnError: true)

        Dhis2User dhis2User = new Dhis2User(uid: 'EMAILTST1DE', username: 'dan', displayName: 'Dan New', email: 'new@d.com')

        when:
        User result = service.findOrRegister(dhis2User)

        then:
        result.email == 'new@d.com'
        result.firstName == 'Dan'
        result.lastName == 'New'
    }

    void "returning user: roles and active flag are not modified"() {
        given:
        User user = savedUser('eve', 'Eve', 'Active', 'e@e.com')
        user.active = true
        user.save(flush: true)
        Dhis2UserLink link = new Dhis2UserLink(user: user, dhis2Uid: 'ROLETST123F', dhis2Username: 'eve')
        link.save(flush: true, failOnError: true)

        when:
        User result = service.findOrRegister(new Dhis2User(uid: 'ROLETST123F', username: 'eve', displayName: 'Eve Updated', email: 'e@e.com'))

        then:
        result.active == true
        result.firstName == 'Eve'
        result.lastName == 'Updated'
    }

    void "v42 first login registers an inactive user keyed by username with a placeholder name"() {
        when:
        User result = service.findOrRegister(new Dhis2User(uid: null, username: 'frank'))

        then:
        result.username == 'frank'
        result.firstName == 'frank'
        result.lastName == '(DHIS2)'
        !result.active

        and:
        Dhis2UserLink link = Dhis2UserLink.findByDhis2Username('frank')
        link.dhis2Uid == null
        link.user.id == result.id
    }

    void "v42 returning user is linked by username without creating a duplicate"() {
        given:
        User user = savedUser('grace', 'Grace', '(DHIS2)', 'g@x.com')
        Date staleLogin = new Date(0)
        new Dhis2UserLink(user: user, dhis2Uid: null, dhis2Username: 'grace', lastLoginAt: staleLogin)
            .save(flush: true, failOnError: true)

        when:
        User result = service.findOrRegister(new Dhis2User(uid: null, username: 'grace'))

        then:
        result.id == user.id
        Dhis2UserLink.countByDhis2Username('grace') == 1
        User.countByUsername('grace') == 1
        Dhis2UserLink.findByDhis2Username('grace').lastLoginAt > staleLogin
    }

    void "v42 login on a tombstoned link forces re-approval (inactive, tombstone cleared)"() {
        given:
        User user = savedUser('heidi', 'Heidi', '(DHIS2)', 'h@x.com')
        new Dhis2UserLink(user: user, dhis2Uid: null, dhis2Username: 'heidi', deactivatedAt: new Date())
            .save(flush: true, failOnError: true)

        when:
        User result = service.findOrRegister(new Dhis2User(uid: null, username: 'heidi'))

        then:
        result.id == user.id
        !result.active
        Dhis2UserLink.findByDhis2Username('heidi').deactivatedAt == null
    }

    void "v42 login on a tombstoned link whose user is already inactive clears the tombstone and leaves the user inactive"() {
        given:
        User user = savedUser('ida', 'Ida', '(DHIS2)', 'i@x.com')
        user.active = false
        user.save(flush: true)
        new Dhis2UserLink(user: user, dhis2Uid: null, dhis2Username: 'ida', deactivatedAt: new Date())
            .save(flush: true, failOnError: true)

        when:
        User result = service.findOrRegister(new Dhis2User(uid: null, username: 'ida'))

        then:
        result.id == user.id
        !result.active
        Dhis2UserLink.findByDhis2Username('ida').deactivatedAt == null
    }

    void "registration escalates the suffix when the -dhis2 username is also taken"() {
        given:
        savedUser('existing', 'Exist', 'Ing', 'e@g.com')
        savedUser('existing-dhis2', 'Already', 'Suffixed', 's@g.com')

        when:
        User result = service.findOrRegister(new Dhis2User(uid: null, username: 'existing'))

        then:
        result.username == 'existing-dhis2-2'
        !result.active
        Dhis2UserLink.findByDhis2Username('existing').user.id == result.id
    }

    void "registration throws when every candidate username is exhausted"() {
        given: "the base name, the -dhis2 suffix, and every -dhis2-N up to the cap are taken"
        savedUser('clash', 'C', 'L', 'c@l.com')
        savedUser('clash-dhis2', 'C', 'L', 'cd@l.com')
        (2..20).each { savedUser("clash-dhis2-${it}", 'C', 'L', "c${it}@l.com") }

        expect:
        User.countByUsernameLike('clash%') == 21

        when:
        service.findOrRegister(new Dhis2User(uid: null, username: 'clash'))

        then:
        Dhis2OAuthException ex = thrown()
        ex.message.contains('clash')
    }

    void "registered DHIS2 user gets an unguessable random local password, never a shared sentinel"() {
        when:
        User alice = service.findOrRegister(new Dhis2User(uid: null, username: 'alice'))
        User bob = service.findOrRegister(new Dhis2User(uid: null, username: 'bob'))

        then: "no known sentinel value an attacker could type into the login form"
        alice.password != '*DHIS2*'
        bob.password != '*DHIS2*'

        and: "each account gets its own value, so one leaked password can't open another"
        alice.password != bob.password

        and: "long enough to be unguessable (32 random bytes, base64url)"
        alice.password.length() >= 43
    }

    private User savedUser(String username, String first, String last, String email) {
        new User(
            username: username,
            firstName: first,
            lastName: last,
            email: email,
            password: 'password1',
            passwordConfirm: 'password1',
            active: true,
        ).save(flush: true, failOnError: true)
    }
}
