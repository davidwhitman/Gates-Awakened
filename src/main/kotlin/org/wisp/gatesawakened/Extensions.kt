package org.wisp.gatesawakened

import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.campaign.StarSystemAPI
import com.fs.starfarer.api.campaign.TextPanelAPI
import com.fs.starfarer.api.util.Misc
import org.wisp.gatesawakened.constants.Strings
import org.wisp.gatesawakened.constants.Tags

internal fun TextPanelAPI.appendPara(text: String, vararg highlights: String) =
    this.addPara(text, Misc.getHighlightColor(), *highlights)

internal val SectorEntityToken.isGate: Boolean
    get() = com.fs.starfarer.api.impl.campaign.ids.Tags.GATE in this.tags

internal val Gate.isActive: Boolean
    get() = Tags.TAG_GATE_ACTIVATED in this.tags || Tags.TAG_ACTIVE_GATES_GATE_ACTIVATED in this.tags

/**
 * Gates that are active from other mods (Active Gates) cannot be deactivated by Gates Awakened.
 */
internal val Gate.canBeDeactivated: Boolean
    get() = Tags.TAG_GATE_ACTIVATED in this.tags

/**
 * Activate a gate. Does not affect activation codes.
 */
internal fun Gate.activate(): Boolean {
    if (this.isGate && Tags.TAG_GATE_ACTIVATED !in this.tags) {
        this.tags += Tags.TAG_GATE_ACTIVATED
        this.name = Strings.activeGateName
        Common.updateActiveGateIntel()
        return true
    }

    return false
}

/**
 * Deactivate a gate. Does not affect activation codes.
 */
internal fun Gate.deactivate(): Boolean {
    if (this.isGate && this.canBeDeactivated) {
        this.tags -= Tags.TAG_GATE_ACTIVATED
        this.name = Strings.inactiveGateName
        Common.updateActiveGateIntel()
        return true
    }

    return false
}

internal val SectorEntityToken.distanceFromCenter: Float
    get() = this.starSystem.distanceFromCenter

internal val StarSystemAPI.distanceFromCenter: Float
    get() = Misc.getDistanceLY(
        this.location,
        di.sector.hyperspace.location
    )

internal val SectorEntityToken.distanceFromPlayer: Float
    get() = this.starSystem.distanceFromPlayer

internal val StarSystemAPI.distanceFromPlayer: Float
    get() = Misc.getDistanceLY(
        this.location,
        di.sector.playerFleet.locationInHyperspace
    )

internal val String.Companion.empty
    get() = ""