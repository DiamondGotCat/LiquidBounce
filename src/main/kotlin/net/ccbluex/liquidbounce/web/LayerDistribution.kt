/*
 *
 *  * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *  *
 *  * Copyright (c) 2015 - 2023 CCBlueX
 *  *
 *  * LiquidBounce is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * LiquidBounce is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 *
 */

package net.ccbluex.liquidbounce.web

import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.web.theme.Page
import net.janrupf.ujr.example.glfw.web.WebWindow

// idk yet
enum class Layer {
    IN_GAME_LAYER,
    SCREEN_LAYER,
    SPLASH_LAYER
}

class LayerDistribution {

    /**
     * This layer does not require any screen to function. It will show up in-game, other contents might overlay it.
     */
    fun newInGameLayer(page: Page): WebWindow {
        val window = makeWindow()
        window.view.loadURL("https://duckduckgo.com/")

        return window
    }

    private fun makeWindow() = GameWebView.webController.createWindow({ mc.window.framebufferWidth }) { mc.window.framebufferHeight }

}
