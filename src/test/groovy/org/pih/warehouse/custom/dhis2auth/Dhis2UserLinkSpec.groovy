package org.pih.warehouse.custom.dhis2auth

import grails.testing.gorm.DataTest
import org.pih.warehouse.core.Person
import org.pih.warehouse.core.User
import spock.lang.Specification
import spock.lang.Unroll

class Dhis2UserLinkSpec extends Specification implements DataTest {

    Class[] getDomainClassesToMock() {
        [Person, User, Dhis2UserLink]
    }

    @Unroll
    void "dhis2Uid '#uid' validation produces hasErrors=#expectErrors"() {
        given:
        User user = mockUser()

        when:
        Dhis2UserLink link = new Dhis2UserLink(user: user, dhis2Uid: uid)
        link.validate()

        then:
        link.hasErrors() == expectErrors

        where:
        uid            | expectErrors
        'ABCDE12345X'  | false
        '123456789AB'  | false
        'SHORT'        | true
        'TOOLONGUID1X' | true
        ''             | true
    }

    void "dhis2Username is nullable"() {
        given:
        Dhis2UserLink link = new Dhis2UserLink(user: mockUser(), dhis2Uid: 'ABCDE12345X', dhis2Username: null)

        when:
        link.validate()

        then:
        !link.hasErrors()
    }

    private User mockUser() {
        new User(
            username: "user-${UUID.randomUUID()}",
            firstName: 'Test',
            lastName: 'User',
            password: 'password1',
            passwordConfirm: 'password1',
        ).save(failOnError: true)
    }
}
