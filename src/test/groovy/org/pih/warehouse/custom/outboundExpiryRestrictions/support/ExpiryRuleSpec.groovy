package org.pih.warehouse.custom.outboundExpiryRestrictions.support

import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class ExpiryRuleSpec extends Specification {

    private static Date today() {
        return new Date().clearTime()
    }

    private static Date daysFromToday(int delta) {
        return today() + delta
    }

    def "isExpired returns false when expirationDate is null"() {
        expect:
        ExpiryRule.isExpired(null, today()) == false
    }

    def "isExpired returns #expected for delta=#delta days from today"() {
        expect:
        ExpiryRule.isExpired(daysFromToday(delta), today()) == expected

        where:
        delta || expected
        -365  || true
        -7    || true
        -1    || true
        0     || false
        1     || false
        365   || false
    }
}
