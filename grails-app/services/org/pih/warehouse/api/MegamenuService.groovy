/**
 * Copyright (c) 2012 Partners In Health.  All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file epl-v10.html at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 **/
package org.pih.warehouse.api

import grails.core.GrailsApplication
import org.pih.warehouse.core.ActivityCode
import org.pih.warehouse.core.Location
import org.pih.warehouse.core.RoleType
import org.pih.warehouse.core.User

class MegamenuService {

    def userService
    GrailsApplication grailsApplication
    def grailsLinkGenerator

    private static final List<String> STOREKEEPER_HIDDEN_SECTIONS = [
            "purchasing",
            "outbound",
            "requisitionTemplate",
    ]
    private static final List<String> REGIONAL_WAREHOUSE_HIDDEN_SECTIONS = [
            "purchasing",
    ]

    private static final String STOREKEEPER_INBOUND_CREATE_HREF = "/stockMovement/createInbound"
    private static final Set<String> RPC_SUPERUSER_MENU_SECTIONS = ["purchasing", "products", "requisitionTemplate"] as Set<String>
    private static final Set<RoleType> RPC_SUPERUSER_MENU_MIN_ROLES = [
            RoleType.ROLE_ASSISTANT,
            RoleType.ROLE_MANAGER,
            RoleType.ROLE_ADMIN,
            RoleType.ROLE_SUPERUSER
    ] as Set<RoleType>

    private getMessageTagLib() {
        return grailsApplication.mainContext.getBean('org.pih.warehouse.MessageTagLib')
    }

    private boolean userHasMinimumMenuRole(User user, Location location, Collection roleTypes, String sectionId = null) {
        if (sectionId in RPC_SUPERUSER_MENU_SECTIONS &&
                userService.hasRpcSuperuserPolicy(user, location?.id) &&
                roleTypes?.any { RPC_SUPERUSER_MENU_MIN_ROLES.contains(it as RoleType) }) {
            return true
        }
        Set<String> acceptedRoleTypeNames = (RoleType.expand(roleTypes)*.name()) as Set<String>
        Set<String> effectiveRoleNames = (userService.getEffectiveRoles(user, location)*.roleType*.name()).findAll { it } as Set<String>
        return effectiveRoleNames.any { acceptedRoleTypeNames.contains(it) }
    }

    private boolean userHasSupplementalMenuRole(User user, Location location, Collection roleTypes, String sectionId = null) {
        if (sectionId in RPC_SUPERUSER_MENU_SECTIONS &&
                userService.hasRpcSuperuserPolicy(user, location?.id) &&
                roleTypes?.any { it in [RoleType.ROLE_SUPERUSER, RoleType.ROLE_ADMIN, RoleType.ROLE_REGIONAL_WAREHOUSE] }) {
            return true
        }
        Set<String> acceptedRoleTypeNames = (roleTypes*.name()) as Set<String>
        Set<String> effectiveRoleNames = (userService.getEffectiveRoles(user, location)*.roleType*.name()).findAll { it } as Set<String>
        return effectiveRoleNames.any { acceptedRoleTypeNames.contains(it) }
    }

    Map buildAndTranslateSections(section, String key, User user, Location location) {
        def label = getMessageTagLib().message(code: section.label, default: section.defaultLabel)
        def translatedSection
        if (section.href) {
            translatedSection = [
                    id: key,
                    label: label,
                    href: grailsLinkGenerator.link(uri: section.href)
            ]
            return translatedSection
        } else if (section.subsections) {
            translatedSection = [
                    id: key,
                    label: label,
                    subsections: buildAndTranslateSubsections(section.subsections, user, location, key)
            ]
            return translatedSection
        } else if (section.menuItems) {
            translatedSection = [
                    id: key,
                    label: label,
                    menuItems: buildAndTranslateMenuItems(section.menuItems, user, location, key)
            ]
            return translatedSection
        }
        return [:]
    }

    List buildAndTranslateSubsections(List subsections, User user, Location location, String sectionId = null) {
        def builtSubsections = []
        subsections.each {
            def minRole = it.minimumRequiredRole
            if (it.enabled == false) {
                return
            }
            if (minRole && !userHasMinimumMenuRole(user, location, [minRole], sectionId)) {
                return
            }
            def roles = it.supplementalRoles
            if (roles && !userHasSupplementalMenuRole(user, location, roles, sectionId)) {
                return
            }
            ActivityCode[] activitiesAny = it.requiredActivitiesAny ?: []
            if (activitiesAny && !location.supportsAny(activitiesAny)) {
                return
            }

            ActivityCode[] activitiesAll = it.requiredActivitiesAll ?: []
            if (activitiesAll && !location.supportsAll(activitiesAll)) {
                return
            }
            def label = getMessageTagLib().message(code: it.label, default: it.defaultLabel)
            builtSubsections << [
                label: label,
                menuItems: buildAndTranslateMenuItems(it.menuItems, user, location, sectionId)
            ]
        }
        return builtSubsections
    }

    List buildAndTranslateMenuItems(List menuItems, User user, Location location, String sectionId = null) {
        def builtMenuItems = []
        menuItems.each {
            if (it.enabled == false) {
                return
            }
            def minRole = it.minimumRequiredRole
            if (minRole && !userHasMinimumMenuRole(user, location, [minRole], sectionId)) {
                return
            }
            def roles = it.supplementalRoles
            if (roles && !userHasSupplementalMenuRole(user, location, roles, sectionId)) {
                return
            }
            ActivityCode[] activitiesAny = it.requiredActivitiesAny ?: []
            if (activitiesAny && !location.supportsAny(activitiesAny)) {
                return
            }

            ActivityCode[] activitiesAll = it.requiredActivitiesAll ?: []
            if (activitiesAll && !location.supportsAll(activitiesAll)) {
                return
            }
            def label = getMessageTagLib().message(code: it.label, default: it.defaultLabel)
            if (it.href) {
                builtMenuItems << [
                    label: label,
                    href: grailsLinkGenerator.link(uri: it.href)
                ]
            } else if (it.subsections) {
                builtMenuItems << [
                    label: label,
                    subsections: buildAndTranslateSubsections(it.subsections, user, location, sectionId)
                ]
            }
        }
        return builtMenuItems
    }

    ArrayList buildAndTranslateMenu(Map menuConfig, User user, Location location) {
        def parsedMenuConfig = []
        menuConfig.each { key, value ->
            def minRole = value.minimumRequiredRole
            if (minRole && !userHasMinimumMenuRole(user, location, [minRole], key)) {
                return
            }
            def roles = value.supplementalRoles
            if (roles && !userHasSupplementalMenuRole(user, location, roles, key)) {
                return
            }
            ActivityCode[] activitiesAny = value.requiredActivitiesAny ?: []
            if (activitiesAny && !location.supportsAny(activitiesAny)) {
                return
            }

            ActivityCode[] activitiesAll = value.requiredActivitiesAll ?: []
            if (activitiesAll && !location.supportsAll(activitiesAll)) {
                return
            }
            if (value.enabled) {
                def translatedSections = buildAndTranslateSections(value, key, user, location)
                if (translatedSections) {
                    parsedMenuConfig << translatedSections
                }
            }
        }
        boolean hasRegionalWarehousePolicy = userService.hasRegionalWarehousePolicy(user, location?.id)
        boolean hasRpcSuperuserPolicy = userService.hasRpcSuperuserPolicy(user, location?.id)
        boolean hasFacilityStorekeeperPolicy = !hasRegionalWarehousePolicy &&
                !hasRpcSuperuserPolicy &&
                userService.hasFacilityStorekeeperPolicy(user, location?.id)

        if (hasFacilityStorekeeperPolicy) {
            parsedMenuConfig = applyFacilityStorekeeperMenuPolicy(parsedMenuConfig)
        }
        if (hasRegionalWarehousePolicy && !hasRpcSuperuserPolicy) {
            parsedMenuConfig = applyRegionalWarehouseMenuPolicy(parsedMenuConfig)
        }
        return parsedMenuConfig
    }

    private ArrayList applyFacilityStorekeeperMenuPolicy(ArrayList menuConfig) {
        ArrayList filteredMenu = (menuConfig ?: []).findAll { section ->
            !STOREKEEPER_HIDDEN_SECTIONS.contains(section?.id)
        } as ArrayList

        filteredMenu.each { section ->
            if (section?.id == "inbound" && section?.subsections) {
                section.subsections = section.subsections.findAll { it != null }.collect { subsection ->
                    subsection.menuItems = (subsection?.menuItems ?: []).findAll { menuItem ->
                        String href = menuItem?.href ?: ""
                        !href.contains(STOREKEEPER_INBOUND_CREATE_HREF)
                    } ?: []
                    return subsection
                }
                section.subsections = section.subsections.findAll { it?.menuItems }
            }
        }

        return filteredMenu
    }

    private ArrayList applyRegionalWarehouseMenuPolicy(ArrayList menuConfig) {
        ArrayList filteredMenu = (menuConfig ?: []).findAll { section ->
            !REGIONAL_WAREHOUSE_HIDDEN_SECTIONS.contains(section?.id)
        } as ArrayList

        filteredMenu.each { section ->
            if (section?.id == "inbound" && section?.subsections) {
                section.subsections = section.subsections.findAll { it != null }.collect { subsection ->
                    subsection.menuItems = (subsection?.menuItems ?: []).findAll { menuItem ->
                        String href = menuItem?.href ?: ""
                        !href.contains(STOREKEEPER_INBOUND_CREATE_HREF)
                    } ?: []
                    return subsection
                }
                section.subsections = section.subsections.findAll { it?.menuItems }
            }
        }

        return filteredMenu
    }
}
