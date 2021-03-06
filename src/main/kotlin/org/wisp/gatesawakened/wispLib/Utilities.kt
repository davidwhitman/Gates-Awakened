package org.wisp.gatesawakened.wispLib

import com.fs.starfarer.api.GameState
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignUIAPI
import org.wisp.gatesawakened.Di
import org.wisp.gatesawakened.di
import java.awt.Color
import kotlin.reflect.KProperty


class CrashReporter(private val modName: String, private val modAuthor: String?, private val di: Di) {
    /**
     * Originally created by Sundog in
     * [Starship Legends](https://bitbucket.org/Nate_NBJ/starship-legends/src/default/src/starship_legends/ModPlugin.java)
     */
    fun reportCrash(exception: Exception): Boolean {
        try {
            val message = "$modName encountered an error!\nPlease let ${modAuthor ?: "the mod author"} know."
            val stackTrace = exception.stackTrace.joinToString(separator = System.lineSeparator()) { "    $it" }

            di.logger
                .error(exception.message + System.lineSeparator() + stackTrace)

            if (di.combatEngine != null && Global.getCurrentState() === GameState.COMBAT) {
                di.combatEngine.combatUI.addMessage(1, Color.ORANGE, exception.message)
                di.combatEngine.combatUI.addMessage(2, Color.RED, message)
            } else if (di.sector != null && Global.getCurrentState() === GameState.CAMPAIGN) {
                val ui: CampaignUIAPI = di.sector.campaignUI
                ui.addMessage(message, Color.RED)
                ui.addMessage(exception.message, Color.ORANGE)
                ui.showConfirmDialog(message + "\n\n" + exception.message, "Ok", null, null, null)

                if (ui.currentInteractionDialog != null) ui.currentInteractionDialog.dismiss()
            } else return false
            return true
        } catch (e: Exception) {
            return false
        }
    }
}

class PersistentNullableData<T>(private val prefKey: String?, private val defaultValue: T? = null) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T? {
        return di.persistentData[prefKey ?: property.name] as? T ?: defaultValue
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        di.persistentData[prefKey ?: property.name] = value
    }
}

class PersistentData<T>(private val prefKey: String?, private val defaultValue: T) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return di.persistentData[prefKey ?: property.name] as? T ?: defaultValue
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        di.persistentData[prefKey ?: property.name] = value
    }
}