package org.pih.warehouse

import grails.testing.web.taglib.TagLibUnitTest
import org.pih.warehouse.custom.roles.CustomRolePolicyService
import org.pih.warehouse.core.User
import org.pih.warehouse.core.UserService
import spock.lang.Specification

class AuthTagLibCustomRolesSpec extends Specification implements TagLibUnitTest<AuthTagLib> {

    def setup() {
        session.user = Stub(User) {
            getId() >> 'user-1'
        }
        session.warehouse = [id: 'loc-1']
    }

    def "canSendStocklistEmail should hide body for restricted custom roles"() {
        given:
        tagLib.customRolePolicyService = Stub(CustomRolePolicyService) {
            shouldHideStocklistEmail(_, _) >> true
        }

        when:
        String output = applyTemplate('<g:canSendStocklistEmail>Email</g:canSendStocklistEmail>')

        then:
        output == ''
    }

    def "canManageStocklists should render body when custom policy allows stocklist management"() {
        given:
        tagLib.userService = Stub(UserService) {
            isUserAdmin(_) >> false
        }
        tagLib.customRolePolicyService = Stub(CustomRolePolicyService) {
            canManageStocklists(_, _) >> true
        }

        when:
        String output = applyTemplate('<g:canManageStocklists>Allowed</g:canManageStocklists>')

        then:
        output == 'Allowed'
    }
}
