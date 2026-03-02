package dev.hygradle.harness.hotswapagent

import com.hypixel.hytale.server.core.plugin.PluginManager
import org.hotswap.agent.annotation.Plugin

@Plugin(name = "Hygradle Harness", testedVersions = ["*"])
class HotswapAgentPlugin(val pluginManager: PluginManager) {
  init {
    println("HYGRADLE HARNESS BLASTING OFF")
  }

  companion object {
    fun register(manager: PluginManager) =
        org.hotswap.agent.config.PluginManager.getInstance()
            .pluginRegistry
            .initializePluginInstance(HotswapAgentPlugin(manager))
  }
}
