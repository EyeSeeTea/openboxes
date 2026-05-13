package org.pih.warehouse.custom.dhis2auth

import grails.testing.gorm.DataTest
import grails.testing.services.ServiceUnitTest
import org.pih.warehouse.core.Person
import org.pih.warehouse.core.User
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
