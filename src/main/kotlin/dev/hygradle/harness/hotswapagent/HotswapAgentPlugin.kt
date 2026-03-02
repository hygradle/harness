package dev.hygradle.harness.hotswapagent

import com.hypixel.hytale.server.core.plugin.PluginManager
import org.hotswap.agent.annotation.LoadEvent
import org.hotswap.agent.annotation.OnClassLoadEvent
import org.hotswap.agent.annotation.Plugin
import org.hotswap.agent.javassist.CtClass

@Plugin(name = "Hygradle Harness", testedVersions = ["*"])
class HotswapAgentPlugin(val pluginManager: PluginManager) {
  init {
    println("HYGRADLE HARNESS BLASTING OFF")
  }

  @OnClassLoadEvent(classNameRegexp = ".*", events = [LoadEvent.DEFINE])
  fun onClassLoad(clazz: CtClass, loader: ClassLoader) {
    println(loader.name)
  }
}
