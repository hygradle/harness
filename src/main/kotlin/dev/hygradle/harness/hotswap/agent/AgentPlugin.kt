package dev.hygradle.harness.hotswap.agent

import com.hypixel.hytale.common.plugin.PluginIdentifier
import io.netty.util.concurrent.ScheduledFuture
import org.hotswap.agent.annotation.Init
import org.hotswap.agent.annotation.LoadEvent
import org.hotswap.agent.annotation.OnClassLoadEvent
import org.hotswap.agent.annotation.Plugin
import org.hotswap.agent.javassist.CtClass
import org.hotswap.agent.logging.AgentLogger

@Plugin(name = "hygradle-harness", testedVersions = ["*"])
class AgentPlugin {
  companion object {
    private lateinit var appClassLoader: ClassLoader

    private val pendingReloads = HashMap<PluginIdentifier, ScheduledFuture<*>>()

    @JvmStatic
    @Init
    fun init(classLoader: ClassLoader) {
      appClassLoader = classLoader
    }

    @JvmStatic
    @OnClassLoadEvent(classNameRegexp = ".*", events = [LoadEvent.REDEFINE])
    fun onClassRedefined(clazz: CtClass) {
      LOGGER.info("Class refined: ${clazz.name}")
    }

    const val RELOAD_DEBOUNCE = 500

    val LOGGER: AgentLogger = AgentLogger.getLogger(AgentPlugin::class.java)
  }
}
