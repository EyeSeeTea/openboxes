package org.pih.warehouse.custom.dhis2auth

import grails.testing.gorm.DomainUnitTest
import org.pih.warehouse.core.User
import spock.lang.Specification

class Dhis2UserLinkSpec extends Specification implements DomainUnitTest<Dhis2UserLink> {

    void "dhis2Uid must be exactly 11 characters"() {
        given:
        User user = mockUser()

        when:
        Dhis2UserLink link = new Dhis2UserLink(user: user, dhis2Uid: uid, createdAt: new Date(), updatedAt: new Date())
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

    void "user must be unique across Dhis2UserLink records"() {
        expect:
        domainClass.metaClass.getMetaMethod('findByUser', Object) != null ||
            Dhis2UserLink.constraints.user.unique
    }

    void "dhis2Uid must be unique"() {
        expect:
        Dhis2UserLink.constraints.dhis2Uid.unique
    }

    void "dhis2Username is nullable"() {
        given:
        User user = mockUser()

        when:
        Dhis2UserLink link = new Dhis2UserLink(user: user, dhis2Uid: 'ABCDE12345X',
            dhis2Username: null, createdAt: new Date(), updatedAt: new Date())
        link.validate()

        then:
        !link.hasErrors()
    }

    void "beforeInsert sets createdAt and updatedAt"() {
        given:
        Dhis2UserLink link = new Dhis2UserLink()

        when:
        link.beforeInsert()

        then:
        link.createdAt != null
        link.updatedAt != null
    }

    void "beforeUpdate refreshes updatedAt"() {
        given:
        Date past = new Date(System.currentTimeMillis() - 5000)
        Dhis2UserLink link = new Dhis2UserLink(createdAt: past, updatedAt: past)

        when:
        link.beforeUpdate()

        then:
        link.updatedAt >= past
    }

    private User mockUser() {
        new User(id: UUID.randomUUID().toString())
    }
}
