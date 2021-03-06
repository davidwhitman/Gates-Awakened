package com.fs.starfarer.api.impl.campaign.rulecmd

// Must use this package because this plugin is called by rules.csv


import ch.tutteli.kbox.joinToString
import com.fs.starfarer.api.campaign.InteractionDialogAPI
import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.campaign.StarSystemAPI
import com.fs.starfarer.api.campaign.TextPanelAPI
import com.fs.starfarer.api.impl.campaign.abilities.TransponderAbility
import com.fs.starfarer.api.impl.campaign.ids.Abilities
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.loading.Description
import com.fs.starfarer.api.util.Misc
import org.lwjgl.input.Keyboard
import org.wisp.gatesawakened.*
import org.wisp.gatesawakened.constants.MOD_PREFIX
import org.wisp.gatesawakened.jumping.Jump
import org.wisp.gatesawakened.midgame.Midgame
import org.wisp.gatesawakened.wispLib.addPara
import kotlin.math.absoluteValue

class JumpDialog : PaginatedOptions() {
    private var selectedOptionBeingConfirmed: String? = null
    private var isShowingTransponderConfirmation = false

    override fun init(dialog: InteractionDialogAPI) {
        this.dialog = dialog

        // Show the image and "The adamantine ring awaits..." text
        dialog.visualPanel.showImagePortion(
            "illustrations", "dead_gate",
            640f, 400f, 0f, 0f, 480f, 300f
        )
        dialog.textPanel.addPara(
            di.settings.getDescription(
                dialog.interactionTarget.customDescriptionId,
                Description.Type.CUSTOM
            ).text1
        )

        if (isPlayerBeingWatched()) {
            dialog.textPanel.addPara {
                "A nearby fleet is " + mark("tracking your movements") + ", making it unwise to approach the Gate."
            }

            addOption(Option.LEAVE.text, Option.LEAVE.id)
            showOptions()
            dialog.optionPanel.setShortcut(Option.LEAVE.id, Keyboard.KEY_ESCAPE, false, false, false, true)
            return
        } else {
            // Show the initial dialog options
            optionSelected(null, Option.INIT.id)
        }
    }

    override fun optionSelected(optionText: String?, optionData: Any?) {
        val activatedGates =
            Common.getGates(GateFilter.Active, excludeCurrentGate = true, includeGatesFromOtherMods = true)

        if (optionData in Option.values().map { it.id }) {
            // If player chose a normal dialog option (not jump)

            // PaginatedOptions.options (which is different than `dialog.options`)
            // doesn't clear itself because the user may change pages without changing the total available options
            // so we want to clear it manually if we recognize a selection that is not a page change.
            options.clear()
            val gate = dialog.interactionTarget

            when (Option.values().single { it.id == optionData }) {
                Option.INIT,
                Option.RECONSIDER -> {
                    if (gate.isActive) {
                        addOption(Option.FLY_THROUGH.text, Option.FLY_THROUGH.id)
                    } else if (Midgame.remainingActivationCodes > 0) {
                        addOption(
                            Option.ACTIVATE.text.replace("%d", Midgame.remainingActivationCodes.toString()),
                            Option.ACTIVATE.id
                        )
                    }

                    if (gate.canBeDeactivated && Midgame.wasQuestCompleted) {
                        addOption(Option.DEACTIVATE.text, Option.DEACTIVATE.id)
                    }

                    addOption(Option.LEAVE.text, Option.LEAVE.id)
                }
                Option.ACTIVATE -> {
                    val wasNewGateActivated = gate.activate()

                    if (wasNewGateActivated) {
                        Midgame.remainingActivationCodes--
                        dialog.textPanel.addPara {
                            "You follow the activation instructions carefully. " +
                                    "A barely perceptible energy signature is the only indication that it worked."
                        }

                    } else {
                        dialog.textPanel.addPara {
                            "Hmm...it didn't work."
                        }
                    }

                    printRemainingActivationCodes()

                    optionSelected(null, Option.INIT.id)
                }
                Option.FLY_THROUGH -> {
                    activatedGates.forEach { activatedGate ->
                        val jumpCostInFuel = Common.jumpCostInFuel(activatedGate.gate.distanceFromPlayerInHyperspace)
                        val fuelRemainingAfterJump = di.sector.playerFleet.cargo.fuel - jumpCostInFuel

                        val gateModText = when (activatedGate.sourceMod) {
                            GateMod.BoggledPlayerGateConstruction -> " (warning: unrecognized Gate protocol)"
                            else -> String.empty
                        }

                        var jumpText =
                            "Jump to ${activatedGate.systemName} ($jumpCostInFuel fuel)$gateModText"

                        if (fuelRemainingAfterJump < 0) {
                            jumpText += " (${fuelRemainingAfterJump.absoluteValue} more required)"
                        }

                        addOption(
                            jumpText,
                            activatedGate.systemId
                        )
                    }

                    addOption(Option.RECONSIDER.text, Option.RECONSIDER.id)
                }
                Option.DEACTIVATE -> {
                    if (gate.canBeDeactivated) {
                        dialog.textPanel.addPara {
                            "Carefully, you follow the deactivation instructions you found in the cave."
                        }

                        if (gate.deactivate()) {
                            Midgame.remainingActivationCodes++

                            dialog.textPanel.addPara {
                                "The Gate quietly loses power."
                            }
                        } else {
                            dialog.textPanel.addPara {
                                "However, the deactivation doesn't seem to work."
                            }
                        }
                    }

                    printRemainingActivationCodes()
                    optionSelected(null, Option.INIT.id)
                }
                Option.JUMP_CONFIRM_TURN_TRANSPONDER_ON -> {
                    val transponder = di.sector.playerFleet.getAbility(Abilities.TRANSPONDER)

                    if (transponder != null && !transponder.isActive) {
                        transponder.activate()
                    }

                    optionSelected(null, selectedOptionBeingConfirmed)
                }
                Option.JUMP_CONFIRM ->
                    optionSelected(null, selectedOptionBeingConfirmed)
                Option.LEAVE -> dialog.dismiss()
            }
        } else if (optionData is String && optionData in activatedGates.map { it.systemId }) {
            // Player chose to jump!
            val newSystem = Common.getSystems().firstOrNull { it.id == optionData }

            if (newSystem == null) {
                dialog.dismiss()
                return
            }

            // Check to see if we should delay jumping and ask user if they want to turn their transponder on first.
            val isPromptingUserToTurnOnTransponder = confirmTransponderIfNeeded(
                selectedOptionId = optionData,
                newSystem = newSystem
            )

            if (!isPromptingUserToTurnOnTransponder) {
                val jumpSuccessful = jumpToGate(text = dialog.textPanel, newSystem = newSystem)

                if (jumpSuccessful) {
                    dialog.dismiss()
                } else {
                    optionSelected(null, Option.INIT.id)
                }
            }
        } else {
            // If we don't recognize the selection, let [PaginatedOptions] deal with it.
            // It's probably a Next/Previous page or an Escape
            super.optionSelected(optionText, optionData)
        }

        showOptions()

        // setShortcut changes "Leave" to "Leave (Escape)" with a yellow highlight. And of course you can press escape to leave.
        // The option needs to be present on the optionPanel before setShortcut is called, and the options are only added
        // when showOptions is called. So this has to go down here.
        dialog.optionPanel.setShortcut(Option.RECONSIDER.id, Keyboard.KEY_ESCAPE, false, false, false, true)
        dialog.optionPanel.setShortcut(Option.LEAVE.id, Keyboard.KEY_ESCAPE, false, false, false, true)

    }

    /**
     * Show transponder warning if needed.
     * Logic ripped directly from [com.fs.starfarer.api.impl.campaign.JumpPointInteractionDialogPluginImpl].
     *
     * @return true if confirmation is needed before jumping
     */
    private fun confirmTransponderIfNeeded(
        selectedOptionId: String,
        newSystem: StarSystemAPI
    ): Boolean {
        if (!isShowingTransponderConfirmation) {
            val player = di.sector.playerFleet
            if (!newSystem.isHyperspace && !player.isTransponderOn
            ) {
                val wouldBecomeHostile = TransponderAbility.getFactionsThatWouldBecomeHostile(player)
                var wouldMindTransponderOff = false
                var isPopulated = false
                for (market in di.sector.economy.getMarkets(newSystem)) {
                    if (market.isHidden) continue
                    if (market.faction.isPlayerFaction) continue

                    isPopulated = true
                    if (!market.faction.isHostileTo(Factions.PLAYER) &&
                        !market.isFreePort &&
                        !market.faction.getCustomBoolean(Factions.CUSTOM_ALLOWS_TRANSPONDER_OFF_TRADE)
                    ) {
                        wouldMindTransponderOff = true
                    }
                }

                if (isPopulated) {
                    if (wouldMindTransponderOff) {
                        dialog.textPanel.addPara {
                            "Your transponder is off, and patrols " +
                                    "in the " +
                                    newSystem.nameWithLowercaseType +
                                    " are likely to give you trouble over the fact, if you're spotted."
                        }
                    } else {
                        dialog.textPanel.addPara {
                            "Your transponder is off, but any patrols in the " +
                                    newSystem.nameWithLowercaseType +
                                    " are unlikely to raise the issue."
                        }
                    }

                    if (wouldBecomeHostile.isNotEmpty()) {
                        var str = "Turning the transponder on now would reveal your hostile actions to "
                        str += wouldBecomeHostile.joinToString(
                            separator = ", ",
                            lastSeparator = ", and ",
                            append = { faction, sb -> sb.append(faction.displayNameWithArticle) })
                        dialog.textPanel.addPara(str, Misc.getNegativeHighlightColor())
                    }

                    options.clear()

                    addOption(
                        Option.JUMP_CONFIRM_TURN_TRANSPONDER_ON.text,
                        Option.JUMP_CONFIRM_TURN_TRANSPONDER_ON.id
                    )
                    addOption(
                        Option.JUMP_CONFIRM.text,
                        Option.JUMP_CONFIRM.id
                    )
                    selectedOptionBeingConfirmed = selectedOptionId

                    addOption(Option.LEAVE.text, Option.LEAVE.id)
                    dialog.optionPanel.setShortcut(Option.LEAVE.id, Keyboard.KEY_ESCAPE, false, false, false, true)

                    isShowingTransponderConfirmation = true
                    return true
                }
            }
        }

        return false
    }

    private fun printRemainingActivationCodes() {
        dialog.textPanel.addPara {
            "You have " + mark(Midgame.remainingActivationCodes.toString()) + " activation ${if (Midgame.remainingActivationCodes == 1) "code" else "codes"} left."
        }
    }

    /**
     * Whether there are any nearby fleets watching the player's movements. If so, then they shouldn't be allowed to use the gate.
     * Logic adapted from [com.fs.starfarer.api.impl.campaign.rulecmd.salvage.HostileFleetNearbyAndAware].
     */
    private fun isPlayerBeingWatched(): Boolean {
        val playerFleet = di.sector.playerFleet

        val fleetsWatchingPlayer = playerFleet.containingLocation.fleets
            .filter { nearbyFleet ->
                nearbyFleet.ai != null
                        && !nearbyFleet.faction.isPlayerFaction
                        && nearbyFleet.battle == null
                        && playerFleet.getVisibilityLevelTo(nearbyFleet) != SectorEntityToken.VisibilityLevel.NONE
                        && nearbyFleet.fleetData.membersListCopy.isNotEmpty()
                        && Misc.getDistance(playerFleet.location, nearbyFleet.location) <= 750f
            }

        return fleetsWatchingPlayer.isNotEmpty()
    }

    private fun jumpToGate(
        text: TextPanelAPI,
        newSystem: StarSystemAPI
    ): Boolean {
        // Can only jump using activated gates
        // We shouldn't even see this dialog if the gate isn't activated, but still.
        if (!dialog.interactionTarget.isActive) {
            text.addPara { "Your fleet passes through the inactive gate..." }
            text.addPara { "and nothing happens." }
            return false
        }

        // Jump player fleet to new system
        val gates = Common.getGates(
            filter = GateFilter.Active,
            excludeCurrentGate = false,
            includeGatesFromOtherMods = true
        )
            .filter { it.systemId == newSystem.id }
            .map { it.gate }

        try {
            return when (val result = Jump.jumpPlayer(
                sourceGate = dialog.interactionTarget,
                destinationGate = gates.first()
            )) {
                is Jump.JumpResult.Success -> {
                    text.addPara { "Your fleet passes through the gate..." }
                    true
                }
                is Jump.JumpResult.FuelRequired -> {
                    text.addPara { "You lack the " + mark(result.fuelCost) + " fuel necessary to use the gate." }
                    false
                }
            }
        } catch (e: Exception) {
            // Can happen if can't find a valid gate in the system to jump to
            di.errorReporter.reportCrash(e)
            return false
        }
    }

    override fun doesCommandAddOptions(): Boolean = true

    /**
     * [PaginatedOptions] requires a String id, so we can't just use [Option] itself as the option data
     */
    enum class Option(val text: String, val id: String) {
        INIT("", "${MOD_PREFIX}init"),
        FLY_THROUGH("Fly through the gate", "${MOD_PREFIX}fly_through"),
        JUMP_CONFIRM_TURN_TRANSPONDER_ON(
            "Turn the transponder on and then jump",
            "${MOD_PREFIX}jump_after_turning_on_transponder"
        ),
        JUMP_CONFIRM("Jump, keeping the transponder off", "${MOD_PREFIX}jump_without_changing_transponder"),
        ACTIVATE("Use an activation code (%d left)", "${MOD_PREFIX}activate_gate"),
        DEACTIVATE("Deactivate to reclaim an activation code", "${MOD_PREFIX}deactivate_gate"),
        RECONSIDER("Reconsider", "${MOD_PREFIX}reconsider"),
        LEAVE("Leave", "${MOD_PREFIX}leave")
    }
}