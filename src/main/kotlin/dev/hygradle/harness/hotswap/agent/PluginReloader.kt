package dev.hygradle.harness.hotswap.agent

import java.util.concurrent.CompletableFuture
import kotlin.concurrent.withLock
import org.hotswap.agent.logging.AgentLogger

internal object PluginReloader {
  val LOGGER: AgentLogger = AgentLogger.getLogger(PluginReloader::class.java)

  fun reload(cascade: List<Any>, graph: PluginGraph, hytale: Hytale) {
    val server = hytale.hytaleServer.get()
    val pmLock = hytale.pluginManager.lock().writeLock()
    val assetLock = hytale.assetRegistry.assetLock().writeLock()

    pmLock.withLock { assetLock.withLock { doReload(cascade, graph, hytale, server) } }
  }

  private fun doReload(
      cascade: List<Any>,
      graph: PluginGraph,
      hytale: Hytale,
      server: Any,
  ) {
    val failed = mutableSetOf<Any>()
    val setupAttempted = mutableSetOf<Any>()

    // PRELOAD PHASE (forward order)
    val futures =
        cascade.mapNotNull { id ->
          val plugin = graph.pluginInstances[id] ?: return@mapNotNull null

          try {
            hytale.pluginBase.preLoad(plugin)
          } catch (e: Exception) {
            LOGGER.debug("preLoad failed for $id: ${e.message}")
            null
          }
        }

    if (futures.isNotEmpty()) {
      CompletableFuture.allOf(*futures.toTypedArray()).join()
    }

    // SHUTDOWN PHASE (reverse load order)
    for (id in cascade.asReversed()) {
      val plugin = graph.pluginInstances[id] ?: continue
      val state = hytale.pluginBase.getState(plugin).toString()

      if (state != "ENABLED") {
        LOGGER.debug("Plugin $id is not ENABLED (state: $state), skipping")
        failed.add(id)
        continue
      }

      runCatching {
            hytale.pluginBase.shutdown0(plugin, false)
            hytale.hytaleServer.doneStop(server, plugin)
            LOGGER.debug("Shut down plugin $id")
          }
          .onFailure {
            LOGGER.error("Failed to shut down plugin $id", it)
            failed.add(id)
          }
    }

    // SETUP PHASE (forward load order)
    val am = hytale.assetModule.get()

    for (id in cascade) {
      val plugin = graph.pluginInstances[id] ?: continue

      if (id in failed) continue

      if (graph.forwardDepsOf(id).any { it in failed }) {
        LOGGER.debug("Skipping setup for $id — dependency failed")
        failed.add(id)
        continue
      }

      val savedDynDeps = hytale.assetStore.disableDynamicDependencies

      try {
        hytale.assetStore.disableDynamicDependencies = false
        hytale.pluginBase.setup0(plugin)
        setupAttempted.add(id)
      } catch (e: Exception) {
        LOGGER.error("Failed to setup plugin $id", e)
        failed.add(id)
        continue
      } finally {
        hytale.assetStore.disableDynamicDependencies = savedDynDeps
      }

      runCatching {
            hytale.assetModule.initPendingStores(am)
            hytale.hytaleServer.doneSetup(server, plugin)
          }
          .onFailure {
            LOGGER.error("Post-setup failed for plugin $id", it)
            failed.add(id)
            continue
          }

      if (hytale.pluginBase.getState(plugin).toString() == "FAILED") {
        LOGGER.error("Plugin $id entered FAILED state after setup")
        failed.add(id)
      }
    }

    // START PHASE (forward load order)
    for (id in cascade) {
      val plugin = graph.pluginInstances[id] ?: continue

      if (id in failed) {
        if (id in setupAttempted) {
          runCatching { hytale.pluginBase.shutdown0(plugin, false) }
              .onFailure { LOGGER.debug("Rollback shutdown failed for $id: ${it.message}") }
        }

        continue
      }

      if (graph.forwardDepsOf(id).any { it in failed }) {
        LOGGER.debug("Skipping start for $id — dependency failed")
        failed.add(id)

        runCatching { hytale.pluginBase.shutdown0(plugin, false) }
            .onFailure { LOGGER.debug("Rollback shutdown failed for $id: ${it.message}") }

        continue
      }

      runCatching {
            hytale.pluginBase.start0(plugin)
            hytale.hytaleServer.doneStart(server, plugin)
          }
          .onFailure {
            LOGGER.error("Failed to start plugin $id", it)
            failed.add(id)
            continue
          }

      if (hytale.pluginBase.getState(plugin).toString() != "ENABLED") {
        LOGGER.error("Plugin $id did not reach ENABLED state after start")
        failed.add(id)
      }
    }

    // Summary
    val succeeded = cascade.filter { it !in failed }

    if (succeeded.isNotEmpty()) {
      LOGGER.info("Reloaded successfully: $succeeded")
    }

    if (failed.isNotEmpty()) {
      LOGGER.error("Failed to reload: $failed")
    }
  }
}
