package org.pih.warehouse.custom.dhis2auth

import grails.testing.gorm.DataTest
import org.pih.warehouse.core.Person
import org.pih.warehouse.core.User
import spock.lang.Specification
import spock.lang.Unroll

class Dhis2UserLinkSpec extends Specification implements DataTest {

    private static final String VALID_UID = 'ABCDE12345X'
    private static final String VALID_USERNAME = 'dhis2-user'

    Class[] getDomainClassesToMock() {
        [Person, User, Dhis2UserLink]
    }

    @Unroll
    void "dhis2Uid '#uid' validation produces hasErrors=#expectErrors"() {
        when:
        Dhis2UserLink link = new Dhis2UserLink(user: mockUser(), dhis2Uid: uid, dhis2Username: VALID_USERNAME)
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

    // v42 links carry no DHIS2 UID — identity is the username.
    void "dhis2Uid is nullable so v42 links validate without a UID"() {
        given:
        Dhis2UserLink link = new Dhis2UserLink(user: mockUser(), dhis2Uid: null, dhis2Username: VALID_USERNAME)

        when:
        link.validate()

        then:
        !link.hasErrors()
    }

    // Requirement changed for v42: the username is now the identity key and is required
    // (previously nullable when the UID was the only key).
    void "dhis2Username is required"() {
        given:
        Dhis2UserLink link = new Dhis2UserLink(user: mockUser(), dhis2Uid: VALID_UID, dhis2Username: null)

        when:
        link.validate()

        then:
        link.hasErrors()
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
