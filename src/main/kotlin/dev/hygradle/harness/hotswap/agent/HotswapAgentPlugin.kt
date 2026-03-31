@file:Suppress("UNUSED")

package dev.hygradle.harness.hotswap.agent

import java.nio.file.Path
import org.hotswap.agent.annotation.Init
import org.hotswap.agent.annotation.LoadEvent
import org.hotswap.agent.annotation.OnClassLoadEvent
import org.hotswap.agent.annotation.Plugin
import org.hotswap.agent.config.PluginManager as HAPluginManager
import org.hotswap.agent.logging.AgentLogger

@Plugin(name = "HygradleAgent", testedVersions = ["*"])
class HotswapAgentPlugin {
  companion object {
    const val RELOAD_DEBOUNCE_MS = 100

    val LOGGER: AgentLogger = AgentLogger.getLogger(HotswapAgentPlugin::class.java)

    private lateinit var appClassLoader: ClassLoader

    private val scheduler
      get() = HAPluginManager.getInstance().scheduler

    private val hytale by lazy { Hytale(appClassLoader) }

    @JvmStatic
    @Init
    fun init(classLoader: ClassLoader?) {
      if (classLoader == null) return else appClassLoader = classLoader
      LOGGER.debug("HygradleAgent initialized for classloader $classLoader")
    }

    @JvmStatic
    @OnClassLoadEvent(classNameRegexp = ".*", events = [LoadEvent.REDEFINE])
    fun onClassRedefined(clazz: Class<*>) {
      if (!this::appClassLoader.isInitialized) return
      if (hytale.pluginManager.get() == null) return

      val sourcePath =
          try {
            clazz.protectionDomain.codeSource.location.let { Path.of(it.toURI()) }
          } catch (_: Exception) {
            return
          }

      val graph = PluginGraph(hytale)

      val pluginId = graph.resolvePlugin(sourcePath)

      if (pluginId == null) {
        LOGGER.debug(
            "Ignoring redefine for ${clazz.name} from $sourcePath; known dev plugin sources: ${graph.codeSourceMap.keys}"
        )
        return
      }

      LOGGER.debug(
          "Scheduling reload for $pluginId after redefine of ${clazz.name} from $sourcePath"
      )

      scheduler.scheduleCommand(
          ReloadCommand(pluginId, graph, hytale),
          RELOAD_DEBOUNCE_MS,
      )
    }
  }
}
