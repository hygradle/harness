package dev.hygradle.harness.hotswap.agent

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import org.hotswap.agent.annotation.Init
import org.hotswap.agent.annotation.LoadEvent
import org.hotswap.agent.annotation.OnClassLoadEvent
import org.hotswap.agent.annotation.Plugin
import org.hotswap.agent.command.Command
import org.hotswap.agent.config.PluginManager as HAPluginManager
import org.hotswap.agent.javassist.CtClass
import org.hotswap.agent.logging.AgentLogger

private val log: AgentLogger = AgentLogger.getLogger(HotswapAgentPlugin::class.java)
private const val RELOAD_TIMEOUT_MS = 500

private var appClassLoaderRef: ClassLoader? = null
private var classpathPluginIds: List<Any>? = null
private val reloadCommands = mutableMapOf<Any, ReloadCommand>()
private val scheduler by lazy { HAPluginManager.getInstance().scheduler }

private val reflection by lazy { ReflectionCache(appClassLoaderRef!!) }

@Plugin(name = "HygradleHarness", testedVersions = ["*"])
class HotswapAgentPlugin {
  companion object {
    @JvmStatic
    @Init
    @Suppress("UNUSED")
    fun init(classLoader: ClassLoader?) {
      appClassLoaderRef = classLoader ?: return
      log.info("Hygradle plugin initialized for classloader {}", classLoader)
    }

    @JvmStatic
    @OnClassLoadEvent(
        classNameRegexp = "com.hypixel.hytale.server.core.plugin.PluginClassLoader",
        events = [LoadEvent.DEFINE],
    )
    @Suppress("UNUSED")
    fun onPluginClassLoaderDefined(clazz: CtClass) {
      log.info("PluginClassLoader defined — reload support active")
    }

    @JvmStatic
    @OnClassLoadEvent(classNameRegexp = ".*", events = [LoadEvent.REDEFINE])
    @Suppress("UNUSED")
    fun onClassRedefined(ctClass: CtClass) {
      appClassLoaderRef ?: return
      handleClassRedefined(ctClass.name)
    }
  }
}

private fun handleClassRedefined(className: String) {
  try {
    val ids =
        classpathPluginIds
            ?: discoverClasspathPlugins()
                .also { classpathPluginIds = it }
                .ifEmpty {
                  return
                }

    log.info("Class redefined: {}", className)

    for (id in ids) {
      val cmd = reloadCommands.getOrPut(id) { ReloadCommand { reloadPlugin(id) } }
      scheduler.scheduleCommand(cmd, RELOAD_TIMEOUT_MS)
    }
  } catch (e: Exception) {
    log.debug("Failed to process class redefinition for {}: {}", className, e.message)
  }
}

@Suppress("UNCHECKED_CAST")
private fun discoverClasspathPlugins(): List<Any> {
  try {
    val r = reflection
    val pluginManager = r.pluginManagerGet.invoke(null) ?: return emptyList()
    val classLoaders = r.classLoadersField.get(pluginManager) as Map<Path, *>

    return classLoaders
        .filterKeys { !it.toString().endsWith(".jar") }
        .values
        .filter { r.isInServerClassPath.invoke(it) == true }
        .mapNotNull { r.pluginField.get(it) }
        .map { r.getIdentifier.invoke(it)!! }
        .onEach { log.info("Discovered classpath dev plugin: {}", it) }
  } catch (e: Exception) {
    log.error("Failed to discover classpath plugins", e)
    return emptyList()
  }
}

private fun reloadPlugin(pluginIdentifier: Any) {
  try {
    val r = reflection
    val pluginManager = r.pluginManagerGet.invoke(null)
    val plugin =
        r.getPlugin.invoke(pluginManager, pluginIdentifier)
            ?: return log.error("Plugin {} not found, skipping reload", pluginIdentifier)

    val state = r.getState.invoke(plugin)
    if (state.toString() != "ENABLED") {
      log.warning("Plugin {} is not ENABLED (state: {}), skipping reload", pluginIdentifier, state)
      return
    }

    log.info("Reloading plugin {}...", pluginIdentifier)

    r.shutdown0.invoke(plugin, false)
    log.info("Plugin {} shut down, restarting lifecycle...", pluginIdentifier)

    val preloadFuture = r.preLoad.invoke(plugin) as? CompletableFuture<*>
    if (preloadFuture == null) {
      r.setup0.invoke(plugin)
      r.start0.invoke(plugin)
      log.info("Plugin {} reloaded successfully", pluginIdentifier)
    } else {
      preloadFuture.thenRun {
        try {
          r.setup0.invoke(plugin)
          r.start0.invoke(plugin)
          log.info("Plugin {} reloaded successfully", pluginIdentifier)
        } catch (e: Exception) {
          log.error("Failed to restart plugin {} after preLoad", e, pluginIdentifier)
        }
      }
    }
  } catch (e: Exception) {
    log.error("Failed to reload plugin {}", e, pluginIdentifier)
  }
}

private class ReloadCommand(private val action: () -> Unit) : Command {
  override fun executeCommand() { action() }
}

private class ReflectionCache(cl: ClassLoader) {
  private val pluginManagerClass =
      cl.loadClass("com.hypixel.hytale.server.core.plugin.PluginManager")
  private val pluginIdClass = cl.loadClass("com.hypixel.hytale.common.plugin.PluginIdentifier")
  private val pclClass = cl.loadClass("com.hypixel.hytale.server.core.plugin.PluginClassLoader")
  private val pluginBaseClass = cl.loadClass("com.hypixel.hytale.server.core.plugin.PluginBase")

  val pluginManagerGet: Method = pluginManagerClass.getMethod("get")
  val getPlugin: Method = pluginManagerClass.getMethod("getPlugin", pluginIdClass)

  val classLoadersField: Field =
      pluginManagerClass.getDeclaredField("classLoaders").apply { isAccessible = true }

  val pluginField: Field = pclClass.getDeclaredField("plugin").apply { isAccessible = true }

  val isInServerClassPath: Method = pclClass.getMethod("isInServerClassPath")
  val getIdentifier: Method = pluginBaseClass.getMethod("getIdentifier")
  val getState: Method = pluginBaseClass.getMethod("getState")

  val shutdown0: Method =
      pluginBaseClass.getDeclaredMethod("shutdown0", Boolean::class.javaPrimitiveType).apply {
        isAccessible = true
      }

  val setup0: Method = pluginBaseClass.getDeclaredMethod("setup0").apply { isAccessible = true }

  val start0: Method = pluginBaseClass.getDeclaredMethod("start0").apply { isAccessible = true }

  val preLoad: Method = pluginBaseClass.getMethod("preLoad")
}
