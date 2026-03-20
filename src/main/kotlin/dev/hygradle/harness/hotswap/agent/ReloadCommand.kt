package dev.hygradle.harness.hotswap.agent

import org.hotswap.agent.command.MergeableCommand
import org.hotswap.agent.logging.AgentLogger

internal class ReloadCommand(
    val affectedPluginId: Any,
    val graph: PluginGraph,
    val hytale: Hytale,
) : MergeableCommand() {

  override fun executeCommand() {
    runCatching {
          val affected =
              popMergedCommands().mapTo(mutableSetOf(affectedPluginId)) {
                (it as ReloadCommand).affectedPluginId
              }

          graph.computeCascade(affected).ifEmpty {
            LOGGER.debug("No dev plugins affected, skipping reload")
            return
          }
        }
        .onSuccess {
          LOGGER.debug("Reload cascade: $it")
          PluginReloader.reload(it, graph, hytale)
        }
        .onFailure { LOGGER.error("Reload failed: ${it.message}", it) }
  }

  override fun equals(other: Any?): Boolean = other is ReloadCommand

  override fun hashCode(): Int = 1

  companion object {
    val LOGGER: AgentLogger = AgentLogger.getLogger(ReloadCommand::class.java)
  }
}
