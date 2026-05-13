package org.pih.warehouse.custom.dhis2auth

import grails.testing.gorm.DomainUnitTest
import org.pih.warehouse.core.User
import spock.lang.Specification
import spock.lang.Unroll

class Dhis2UserLinkSpec extends Specification implements DomainUnitTest<Dhis2UserLink> {

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

    void "two Dhis2UserLink records cannot share the same user"() {
        given:
        User user = mockUser()
        new Dhis2UserLink(user: user, dhis2Uid: 'AAAAAAAAAAA').save(failOnError: true)

        when:
        Dhis2UserLink duplicate = new Dhis2UserLink(user: user, dhis2Uid: 'BBBBBBBBBBB')
        duplicate.validate()

        then:
        duplicate.hasErrors()
        duplicate.errors.getFieldError('user').code == 'unique'
    }

    void "two Dhis2UserLink records cannot share the same dhis2Uid"() {
        given:
        new Dhis2UserLink(user: mockUser(), dhis2Uid: 'AAAAAAAAAAA').save(failOnError: true)

        when:
        Dhis2UserLink duplicate = new Dhis2UserLink(user: mockUser(), dhis2Uid: 'AAAAAAAAAAA')
        duplicate.validate()

        then:
        duplicate.hasErrors()
        duplicate.errors.getFieldError('dhis2Uid').code == 'unique'
    }

    void "dhis2Username is nullable"() {
        given:
        Dhis2UserLink link = new Dhis2UserLink(user: mockUser(), dhis2Uid: 'ABCDE12345X', dhis2Username: null)

        when:
        link.validate()

        then:
        !link.hasErrors()
    }

    void "GORM auto-timestamps dateCreated and lastUpdated are populated on save"() {
        given:
        Dhis2UserLink link = new Dhis2UserLink(user: mockUser(), dhis2Uid: 'ABCDE12345X')

        when:
        link.save(failOnError: true)

        then:
        link.dateCreated != null
        link.lastUpdated != null
    }

    private User mockUser() {
        new User(id: UUID.randomUUID().toString())
    }
}
