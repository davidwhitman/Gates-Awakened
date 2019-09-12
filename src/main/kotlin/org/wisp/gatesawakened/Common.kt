package org.wisp.gatesawakened

import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.campaign.StarSystemAPI
import org.wisp.gatesawakened.constants.Memory
import org.wisp.gatesawakened.constants.Tags
import org.wisp.gatesawakened.constants.isBlacklisted
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A collection of information and stateless utility methods.
 */
internal object Common {
    val isDebugModeEnabled: Boolean
        get() = di.settings.getBoolean("gatesAwakened_Debug")

    private val fuelCostPerLY: Float
        get() {
            val fuelMultiplierFromSettings = di.settings.getFloat("gatesAwakened_FuelMultiplier")
            return max(
                1F,
                (di.sector.playerFleet.logistics.fuelCostPerLightYear * fuelMultiplierFromSettings)
            )
        }

    var remainingActivationCodes: Int
        get() = di.sector.memoryWithoutUpdate[Memory.GATE_ACTIVATION_CODES_REMAINING] as? Int ?: 0
        set(value) {
            di.sector.memoryWithoutUpdate[Memory.GATE_ACTIVATION_CODES_REMAINING] = value
        }

    fun jumpCostInFuel(distanceInLY: Float): Int = (fuelCostPerLY * distanceInLY).roundToInt()

    fun getSystems(): List<StarSystemAPI> =
        di.sector.starSystems
            .filterNot { it.isBlacklisted }

    /**
     * List of non-blacklisted gates (filterable), sorted by shortest distance from player first
     */
    fun getGates(filter: GateFilter, excludeCurrentGate: Boolean): List<GateInfo> {
        return getSystems()
            .flatMap { system -> system.getEntitiesWithTag(Tags.TAG_GATE) }
            .asSequence()
            .filter { gate ->
                when (filter) {
                    GateFilter.All -> true
                    GateFilter.Active -> gate.hasTag(Tags.TAG_GATE_ACTIVATED)
                    GateFilter.Inactive -> !gate.hasTag(Tags.TAG_GATE_ACTIVATED)
                    GateFilter.IntroCore -> gate.hasTag(Tags.TAG_GATE_INTRO_CORE)
                    GateFilter.IntroFringe -> gate.hasTag(Tags.TAG_GATE_INTRO_FRINGE)
                }
            }
            .map {
                GateInfo(
                    gate = it,
                    systemId = it.starSystem.id,
                    systemName = it.starSystem.baseName
                )
            }
            .filter {
                if (excludeCurrentGate)
                    it.gate.distanceFromPlayer > 0
                else
                    true
            }
            .sortedBy { it.gate.distanceFromPlayer }
            .toList()
    }
}

internal data class GateInfo(
    val gate: Gate,
    val systemId: String,
    val systemName: String
)

internal enum class GateFilter {
    All,
    Active,
    Inactive,
    IntroCore,
    IntroFringe
}

val Any?.exhaustiveWhen: Unit?
    get() = this?.run { }

typealias Gate = SectorEntityToken