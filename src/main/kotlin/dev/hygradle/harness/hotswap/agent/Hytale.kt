@file:Suppress("UNCHECKED_CAST")

package dev.hygradle.harness.hotswap.agent

import java.lang.reflect.Method
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.locks.ReadWriteLock

class Hytale(private val cl: ClassLoader) {
  val pluginIdentifier = PluginIdentifier()
  val pluginManager = PluginManager()
  val pluginBase = PluginBase()
  val pluginManifest = PluginManifest()
  val pluginClassLoader = PluginClassLoader()
  val assetModule = AssetModule()
  val assetStore = AssetStore()
  val assetRegistry = AssetRegistry()
  val hytaleServer = Server()

  inner class PluginManager {
    val clazz: Class<*> = cl.loadClass("com.hypixel.hytale.server.core.plugin.PluginManager")

    private val `get`: Method = clazz.getMethod("get")
    private val getPlugin: Method = clazz.getMethod("getPlugin", pluginIdentifier.clazz)
    private val classLoaders = clazz.getDeclaredField("classLoaders").apply { isAccessible = true }
    private val plugins = clazz.getDeclaredField("plugins").apply { isAccessible = true }
    private val lock = clazz.getDeclaredField("lock").apply { isAccessible = true }

    fun get(): Any? = `get`.invoke(null)

    fun getPlugin(id: Any): Any? = getPlugin.invoke(get(), id)

    fun classLoaders(): Map<Path, Any> = classLoaders.get(get()!!) as Map<Path, Any>

    fun plugins(): Map<Any, Any> = plugins.get(get()!!) as Map<Any, Any>

    fun lock() = lock.get(get()!!) as ReadWriteLock
  }

  inner class PluginBase {
    val clazz: Class<*> = cl.loadClass("com.hypixel.hytale.server.core.plugin.PluginBase")

    private val getIdentifier = clazz.getMethod("getIdentifier")
    private val getState = clazz.getMethod("getState")
    private val getManifest = clazz.getMethod("getManifest")
    private val shutdown0 =
        clazz.getDeclaredMethod("shutdown0", Boolean::class.javaPrimitiveType).apply {
          isAccessible = true
        }
    private val setup0 = clazz.getDeclaredMethod("setup0").apply { isAccessible = true }
    private val start0 = clazz.getDeclaredMethod("start0").apply { isAccessible = true }
    private val preLoad = clazz.getMethod("preLoad")

    fun getIdentifier(plugin: Any) = getIdentifier.invoke(plugin)!!

    fun getState(plugin: Any) = getState.invoke(plugin)!!

    fun getManifest(plugin: Any) = getManifest.invoke(plugin)!!

    fun shutdown0(plugin: Any, unregister: Boolean = false) {
      shutdown0.invoke(plugin, unregister)
    }

    fun setup0(plugin: Any) {
      setup0.invoke(plugin)
    }

    fun start0(plugin: Any) {
      start0.invoke(plugin)
    }

    fun preLoad(plugin: Any): CompletableFuture<Void>? =
        preLoad.invoke(plugin) as? CompletableFuture<Void>
  }

  inner class PluginManifest {
    val clazz: Class<*> = cl.loadClass("com.hypixel.hytale.common.plugin.PluginManifest")

    private val getMain = clazz.getMethod("getMain")
    private val getGroup = clazz.getMethod("getGroup")
    private val getDependencies = clazz.getMethod("getDependencies")
    private val getOptionalDependencies = clazz.getMethod("getOptionalDependencies")
    private val getLoadBefore = clazz.getMethod("getLoadBefore")

    fun getMain(manifest: Any) = getMain.invoke(manifest) as String

    fun getGroup(manifest: Any) = getGroup.invoke(manifest) as String

    fun getDependencies(manifest: Any) = (getDependencies.invoke(manifest) as Map<Any, Any>).keys

    fun getOptionalDependencies(manifest: Any) =
        (getOptionalDependencies.invoke(manifest) as Map<Any, Any>).keys

    fun getLoadBefore(manifest: Any) = (getLoadBefore.invoke(manifest) as Map<Any, Any>).keys
  }

  inner class PluginClassLoader {
    val clazz: Class<*> = cl.loadClass("com.hypixel.hytale.server.core.plugin.PluginClassLoader")

    private val isInServerClasspath = clazz.getMethod("isInServerClassPath")
    private val plugin = clazz.getDeclaredField("plugin").apply { isAccessible = true }

    fun isInServerClasspath(loader: Any) = isInServerClasspath.invoke(loader) as Boolean

    fun plugin(loader: Any): Any = plugin.get(loader)
  }

  inner class PluginIdentifier {
    val clazz: Class<*> = cl.loadClass("com.hypixel.hytale.common.plugin.PluginIdentifier")
  }

  inner class AssetModule {
    val clazz: Class<*> = cl.loadClass("com.hypixel.hytale.server.core.asset.AssetModule")

    private val `get`: Method = clazz.getMethod("get")
    private val initPendingStores =
        clazz.getDeclaredMethod("initPendingStores").apply { isAccessible = true }

    fun get() = `get`.invoke(null)!!

    fun initPendingStores(module: Any) {
      initPendingStores.invoke(module)
    }
  }

  inner class AssetStore {
    val clazz: Class<*> = cl.loadClass("com.hypixel.hytale.assetstore.AssetStore")

    private val _disableDynamicDependencies =
        clazz.getDeclaredField("DISABLE_DYNAMIC_DEPENDENCIES").apply { isAccessible = true }

    var disableDynamicDependencies: Boolean
      get() = _disableDynamicDependencies.get(null) as Boolean
      set(value) {
        _disableDynamicDependencies.set(null, value)
      }
  }

  inner class AssetRegistry {
    val clazz: Class<*> = cl.loadClass("com.hypixel.hytale.assetstore.AssetRegistry")
    private val assetLock = clazz.getDeclaredField("ASSET_LOCK").apply { isAccessible = true }

    fun assetLock() = assetLock.get(null) as ReadWriteLock
  }

  inner class Server {
    val clazz: Class<*> = cl.loadClass("com.hypixel.hytale.server.core.HytaleServer")

    private val `get` = clazz.getMethod("get")
    private val doneSetup =
        clazz.getDeclaredMethod("doneSetup", pluginBase.clazz).apply { isAccessible = true }
    private val doneStart =
        clazz.getDeclaredMethod("doneStart", pluginBase.clazz).apply { isAccessible = true }
    private val doneStop =
        clazz.getDeclaredMethod("doneStop", pluginBase.clazz).apply { isAccessible = true }

    fun get() = `get`.invoke(null)!!

    fun doneSetup(server: Any, plugin: Any) {
      doneSetup.invoke(server, plugin)
    }

    fun doneStart(server: Any, plugin: Any) {
      doneStart.invoke(server, plugin)
    }

    fun doneStop(server: Any, plugin: Any) {
      doneStop.invoke(server, plugin)
    }
  }
}
