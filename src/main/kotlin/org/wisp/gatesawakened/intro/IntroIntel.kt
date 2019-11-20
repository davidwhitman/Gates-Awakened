package org.wisp.gatesawakened.intro

import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin
import com.fs.starfarer.api.impl.campaign.ids.Tags
import com.fs.starfarer.api.impl.campaign.intel.misc.BreadcrumbIntel
import com.fs.starfarer.api.ui.SectorMapAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import org.wisp.gatesawakened.di
import org.wisp.gatesawakened.empty
import org.wisp.gatesawakened.midgame.Midgame
import org.wisp.gatesawakened.wispLib.addPara


class IntroIntel(target: SectorEntityToken) : BreadcrumbIntel(null, target) {

    companion object {
        private val iconSpritePath: String by lazy(LazyThreadSafetyMode.NONE) {
            val path = "graphics/intel/g8_gate_quest.png"
            di.settings.loadTexture(path)
            path
        }
    }

    override fun getName(): String = getTitle()

    override fun getTitle(): String = "Gate investigation" + if (Intro.wasQuestCompleted) " - Completed" else String.empty

    override fun getIcon(): String = iconSpritePath

    override fun createSmallDescription(info: TooltipMakerAPI, width: Float, height: Float) {
        info.addImage(di.settings.getSpriteName("illustrations", "dead_gate"), width, 10f)

        info.addPara { "You saw an image of a Gate and the name of a system on a tripad in a bar." }

        if (!Intro.wasQuestCompleted) {
            info.addPara { "Perhaps it's worth a visit to ${mark(target.starSystem.baseName)} to search for a Gate." }
        } else {
            info.addPara {
                "You followed a Gate in ${mark(target.starSystem.baseName)} that led to ${mark(target.starSystem.baseName)}, " +
                        "a discovery best kept quiet lest the factions interrogate you."
            }
            info.addPara {
                "Perhaps you will stumble across more Gate findings in the " +
                        if (Midgame.isMidgame()) "near future."
                        else "future, when you are more established."
            }
        }

        val days = daysSincePlayerVisible

        if (days >= 1) {
            addDays(info, "ago.", days, Misc.getTextColor(), 10f)
        }
    }

    override fun createIntelInfo(info: TooltipMakerAPI, mode: IntelInfoPlugin.ListInfoMode?) {
        super.createIntelInfo(info, mode)
        info.addPara(padding = 0f) { "Investigate a possible gate at ${mark(target.starSystem.baseName)}" }
    }

    override fun advance(amount: Float) {
        super.advance(amount)

        // If it's not already ending or ended and the quest was completed, mark the quest as complete
        if ((!isEnding || !isEnded) && Intro.wasQuestCompleted) {
            endAfterDelay()
        }
    }

    override fun hasSmallDescription() = true

    override fun getIntelTags(map: SectorMapAPI?): MutableSet<String> =
        super.getIntelTags(map)
            .apply {
                add(Tags.INTEL_EXPLORATION)
                add(Tags.INTEL_STORY)
                add(org.wisp.gatesawakened.constants.Tags.INTEL_ACTIVE_GATE)
            }
}