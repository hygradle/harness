package dev.hygradle.harness.hotswap.agent

import com.hypixel.hytale.common.plugin.PluginIdentifier
import io.netty.util.concurrent.ScheduledFuture
import org.hotswap.agent.annotation.Init
import org.hotswap.agent.annotation.LoadEvent
import org.hotswap.agent.annotation.OnClassLoadEvent
import org.hotswap.agent.annotation.Plugin
import org.hotswap.agent.javassist.CtClass
import org.hotswap.agent.logging.AgentLogger
import org.hotswap.agent.util.ReflectionHelper
import java.lang.reflect.Method

//@Plugin(name = "hygradle-harness", testedVersions = ["*"])
class AgentPlugin {
  companion object {
    private lateinit var appClassLoader: ClassLoader

    private lateinit var pluginIdentifiers: List<Any>

    private val pendingReloads = HashMap<Any, ScheduledFuture<*>>()

    @JvmStatic
    @Init
    fun init(classLoader: ClassLoader?) {
      if (classLoader == null) return
      appClassLoader = classLoader
    }

    @JvmStatic
    @OnClassLoadEvent(classNameRegexp = ".*", events = [LoadEvent.REDEFINE])
    fun onClassRedefined(clazz: CtClass) {
      if (!this::pluginIdentifiers.isInitialized) {}

      LOGGER.info("Class redefined: ${clazz.name}")
    }

    const val RELOAD_DEBOUNCE = 500

    val LOGGER: AgentLogger = AgentLogger.getLogger(AgentPlugin::class.java)

    class Reflector(cl: ClassLoader) {
      val PluginManager: Class<*> = cl.loadClass("com.hypixel.hytale.server.core.plugin.PluginManager")
      val pluginManagerGet: Method = PluginManager.getMethod("get")
    }
  }
}
