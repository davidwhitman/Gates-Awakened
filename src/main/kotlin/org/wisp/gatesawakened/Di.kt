package org.wisp.gatesawakened

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.SettingsAPI
import com.fs.starfarer.api.campaign.SectorAPI
import org.wisp.gatesawakened.logging.DebugLogger

class Di(
    val sector: SectorAPI = Global.getSector(),
    val settings: SettingsAPI = Global.getSettings(),
    val logger: DebugLogger = Global.getLogger(Di::class.java)
)

/**
 * Singleton instance of the service locator. Set a new one of these for unit tests.
 */
var di: Di = Di()