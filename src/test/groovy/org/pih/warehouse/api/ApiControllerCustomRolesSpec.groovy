package org.pih.warehouse.api

import grails.testing.gorm.DataTest
import grails.testing.web.controllers.ControllerUnitTest
import org.grails.web.json.JSONObject
import org.pih.warehouse.core.ActivityCode
import org.pih.warehouse.core.Location
import org.pih.warehouse.core.LocationGroup
import org.pih.warehouse.core.LocationType
import org.pih.warehouse.core.RoleType
import org.pih.warehouse.core.User
import spock.lang.Specification

class ApiControllerCustomRolesSpec extends Specification implements DataTest, ControllerUnitTest<ApiController> {

    def setup() {
        mockDomains(User, Location, LocationType, LocationGroup)
    }

    def "getMenuConfig uses default menu for authenticated user with custom policy"() {
        given:
        User user = new User(id: "user-id", username: "John").save(validate: false)
        LocationType depot = new LocationType(id: "type-id", name: "Depot").save(validate: false)
        LocationGroup boston = new LocationGroup(id: "group-id", name: "Boston").save(validate: false)
        Location location = new Location(
                id: "location-id",
                name: "Boston",
                locationType: depot,
                locationGroup: boston,
                supportedActivities: [ActivityCode.MANAGE_INVENTORY.id] as Set,
        ).save(validate: false)

        controller.grailsApplication.config.openboxes.megamenu = [id: "default"]
        controller.grailsApplication.config.openboxes.requestorMegamenu = [id: "requestor"]
        controller.grailsApplication.config.openboxes.menuSectionsUrlParts = [inbound: "/stockMovement"]
        controller.session.user = user
        controller.session.warehouse = location
        controller.userService = [
                hasHighestRole: { User currentUser, String locationId, RoleType roleType -> true }
        ]
        controller.customRolePolicyService = [
                hasAnyCustomPolicy: { User currentUser, String locationId -> true }
        ]
        controller.megamenuService = [
                buildAndTranslateMenu: { def menuConfig, User currentUser, Location currentLocation ->
                    assert menuConfig.id == "default"
                    return [[id: "custom-role-menu"]]
                }
        ]

        when:
        controller.getMenuConfig()

        then:
        JSONObject response = new JSONObject(controller.response.contentAsString)
        response.data.menuConfig[0].id == "custom-role-menu"
        response.data.menuSectionsUrlParts.inbound == "/stockMovement"
    }
}
