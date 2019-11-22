package org.wisp.gatesawakened.questLib

import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin
import com.fs.starfarer.api.impl.campaign.intel.misc.BreadcrumbIntel
import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.SectorMapAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import org.wisp.gatesawakened.di
import org.wisp.gatesawakened.wispLib.addPara

/**
 * @param iconPath get via [com.fs.starfarer.api.SettingsAPI.getSpriteName]
 * @param infoCreator the small summary on the left Intel panel sidebar
 * @param smallDescriptionCreator the intel description on the right Intel panel sidebar
 */
abstract class IntelDefinition(
    @Transient var title: (IntelDefinition.() -> String)? = null,
    @Transient var iconPath: (IntelDefinition.() -> String)? = null,
    var durationInDays: Float = Float.NaN,
    @Transient var infoCreator: (IntelDefinition.(info: TooltipMakerAPI?) -> Unit)? = null,
    @Transient var smallDescriptionCreator: (IntelDefinition.(info: TooltipMakerAPI, width: Float, height: Float) -> Unit)? = null,
    val showDaysSinceCreated: Boolean = false,
    val intelTags: List<String>,
    startLocation: SectorEntityToken? = null,
    endLocation: SectorEntityToken? = null,
    var removeIntelIfAnyOfTheseEntitiesDie: List<SectorEntityToken> = emptyList(),
    var soundName: String? = null,
    important: Boolean = false
) : BaseIntelPlugin() {
    companion object {
        val padding = 3f
        val bulletPointPadding = 10f
    }

    private val startLocationCopy: SectorEntityToken?
    private val endLocationCopy: SectorEntityToken?

    init {
        isImportant = important

        startLocationCopy = startLocation?.let { BreadcrumbIntel.makeDoubleWithSameOrbit(it) }
        endLocationCopy = endLocation?.let { BreadcrumbIntel.makeDoubleWithSameOrbit(it) }

        iconPath?.run { di.settings.loadTexture(this.invoke(this@IntelDefinition)) }

        di.sector.addScript(this)
    }

    /**
     * Create an instance of the implementing class. We then copy the transient fields in that class
     * to this one in [readResolve], since they do not get created by the deserializer.
     * We cannot use `this::class.java.newInstance()` because then the implementing class is required to have
     * a no-args constructor.
     */
    abstract fun createInstanceOfSelf(): IntelDefinition

    /**
     * When this class is created by deserializing from a save game,
     * it can't deserialize the anonymous methods, so we mark them as transient,
     * then manually assign them using this method, which gets called automagically
     * by the XStream serializer.
     */
    open fun readResolve(): Any {
        val newInstance = createInstanceOfSelf()
        title = newInstance.title
        iconPath = newInstance.iconPath
        infoCreator = newInstance.infoCreator
        smallDescriptionCreator = newInstance.smallDescriptionCreator


        iconPath?.run { di.settings.loadTexture(this.invoke(this@IntelDefinition)) }
        return this
    }

    final override fun addGenericButton(info: TooltipMakerAPI?, width: Float, text: String?, data: Any?): ButtonAPI {
        return super.addGenericButton(info, width, text, data)
    }

    override fun shouldRemoveIntel(): Boolean {
        if (removeIntelIfAnyOfTheseEntitiesDie.any { !it.isAlive }
            || endLocationCopy?.isAlive == false) {
            return true
        }

        val intelStartedTimestamp = playerVisibleTimestamp

        // Remove intel if duration has elapsed
        if (durationInDays.isFinite()
            && intelStartedTimestamp != null
            && di.sector.clock.getElapsedDaysSince(intelStartedTimestamp) >= durationInDays
        ) {
            return true
        }

        return super.shouldRemoveIntel()
    }

    final override fun createIntelInfo(info: TooltipMakerAPI, mode: IntelInfoPlugin.ListInfoMode?) {
        title?.let {
            info.addPara(
                textColor = getTitleColor(mode),
                padding = 0f
            ) { title!!.invoke(this@IntelDefinition) }
        }
        infoCreator?.invoke(this, info)
    }

    final override fun createSmallDescription(info: TooltipMakerAPI, width: Float, height: Float) {
        smallDescriptionCreator?.invoke(this, info, width, height)

        if (showDaysSinceCreated && daysSincePlayerVisible > 0) {
            addDays(info, "ago.", daysSincePlayerVisible, Misc.getTextColor(), bulletPointPadding)
        }
    }

    final override fun hasSmallDescription(): Boolean = smallDescriptionCreator != null

    override fun getIcon(): String = iconPath?.invoke(this@IntelDefinition)
        ?: di.settings.getSpriteName("intel", "fleet_log")
        ?: super.getIcon()

    override fun getCommMessageSound(): String {
        return soundName
            ?: getSoundLogUpdate()
            ?: super.getCommMessageSound()
    }

    override fun getIntelTags(map: SectorMapAPI?): MutableSet<String> {
        return super.getIntelTags(map)
            .apply { this += intelTags }
    }

    override fun getSortString(): String = "Location"

    override fun getSmallDescriptionTitle(): String? = title?.invoke(this@IntelDefinition)

    override fun getMapLocation(map: SectorMapAPI?): SectorEntityToken? =
        endLocationCopy?.starSystem?.center
            ?: endLocationCopy

    override fun getArrowData(map: SectorMapAPI?): MutableList<IntelInfoPlugin.ArrowData>? {
        if (startLocationCopy == null)
            return null

        // If start and end are same, no arrow
        if (startLocationCopy.containingLocation == endLocationCopy?.containingLocation
            && startLocationCopy.containingLocation?.isHyperspace != true
        ) {
            return null
        }

        return mutableListOf(
            IntelInfoPlugin.ArrowData(startLocationCopy, endLocationCopy)
                .apply {
                    color = factionForUIColors?.baseUIColor
                })
    }

    override fun notifyEnded() {
        super.notifyEnded()
        di.sector.removeScript(this)
    }
}