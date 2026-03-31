package dev.hygradle.harness.hotswap.agent

import java.nio.file.Path
import org.hotswap.agent.logging.AgentLogger

internal class PluginGraph(hytale: Hytale) {
  val codeSourceMap: Map<Path, Any>
  val forwardDeps: Map<Any, Set<Any>>
  val reverseDeps: Map<Any, Set<Any>>
  val loadOrder: List<Any>
  val pluginInstances: Map<Any, Any>

  init {
    val plugins = hytale.pluginManager.plugins()

    // Discover loaded dev plugins from live instances.
    val devPlugins =
        plugins.values.mapNotNull { plugin ->
          val manifest = hytale.pluginBase.getManifest(plugin)

          if (hytale.pluginManifest.getGroup(manifest) == "Hytale") return@mapNotNull null

          val id = hytale.pluginBase.getIdentifier(plugin)

          val codeSource =
              try {
                plugin::class.java.protectionDomain.codeSource?.location?.let {
                  Path.of(it.toURI())
                }
              } catch (e: Exception) {
                LOGGER.debug("Could not resolve code source for plugin $id: ${e.message}")
                null
              } ?: return@mapNotNull null

          if (codeSource.toString().endsWith(".jar")) return@mapNotNull null

          DevPlugin(id, plugin, manifest, codeSource)
        }

    val devPluginIds = devPlugins.map { it.id }.toSet()
    codeSourceMap = devPlugins.associate { it.codeSource to it.id }
    pluginInstances = devPlugins.associate { it.id to it.plugin }

    // Build forward deps (plugin → plugins it depends on), filtered to dev plugins
    val fwdDeps =
        devPlugins.associateTo(mutableMapOf()) { dp ->
          val manifest = dp.manifest

          val deps =
              (hytale.pluginManifest.getDependencies(manifest) +
                      hytale.pluginManifest.getOptionalDependencies(manifest))
                  .filterTo(mutableSetOf()) { it in devPluginIds }

          dp.id to deps
        }

    // loadBefore inverse: if A.loadBefore contains B, then B depends on A
    for (dp in devPlugins) {
      for (target in hytale.pluginManifest.getLoadBefore(dp.manifest)) {
        if (target in devPluginIds) {
          fwdDeps.getOrPut(target) { mutableSetOf() } += dp.id
        }
      }
    }

    forwardDeps = fwdDeps

    // Build reverse deps via flatMap + groupBy
    reverseDeps =
        forwardDeps
            .flatMap { (id, deps) -> deps.map { dep -> dep to id } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, v) -> v.toSet() }

    // Load order derived from PluginManager.plugins (Object2ObjectLinkedOpenHashMap,
    // insertion-ordered)
    loadOrder = plugins.keys.filter { it in devPluginIds }

    LOGGER.debug("Built plugin graph: ${devPluginIds.size} dev plugins, load order: $loadOrder")
  }

  fun resolvePlugin(path: Path): Any? = codeSourceMap[path]

  fun computeCascade(affected: Set<Any>): List<Any> {
    val cascade = mutableSetOf<Any>()
    val queue = ArrayDeque(affected.filter { it in pluginInstances })

    while (queue.isNotEmpty()) {
      val id = queue.removeFirst()

      if (cascade.add(id)) {
        reverseDeps[id]?.forEach { queue.add(it) }
      }
    }

    return loadOrder.filter { it in cascade }
  }

  fun forwardDepsOf(id: Any): Set<Any> = forwardDeps[id] ?: emptySet()

  data class DevPlugin(
      val id: Any,
      val plugin: Any,
      val manifest: Any,
      val codeSource: Path,
  )

  companion object {
    val LOGGER: AgentLogger = AgentLogger.getLogger(PluginGraph::class.java)
  }
}
