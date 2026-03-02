package dev.hygradle.harness.hotswapagent

import com.hypixel.hytale.plugin.early.ClassTransformer
import org.hotswap.agent.javassist.ClassPool
import org.hotswap.agent.javassist.CtClass
import org.hotswap.agent.javassist.CtNewMethod

class HotswapAgentInjector : ClassTransformer {
  override fun transform(name: String, path: String, bytes: ByteArray): ByteArray =
      when (name) {
        "com.hypixel.hytale.server.core.plugin.PluginManager" -> {
          val pool = ClassPool.getDefault()
          val clazz = pool.makeClass(bytes.inputStream())

          instrumentPluginManager(clazz)
        }
        "com.hypixel.hytale.server.core.plugin.PluginClassLoader" -> {
          val pool = ClassPool.getDefault()
          val clazz = pool.makeClass(bytes.inputStream())

          instrumentPluginClassloader(clazz)
        }

        else -> bytes
      }

  private fun instrumentPluginManager(clazz: CtClass): ByteArray {
    clazz.addMethod(
        CtNewMethod.make(
            """
            public void registerHotswapAgentPlugin() {
              try {
                  org.hotswap.agent.config.PluginManager
                    .getInstance()
                    .getPluginRegistry()
                    .initializePluginInstance(
                      new dev.hygradle.harness.hotswapagent.HotswapAgentPlugin(this)
                    );
              } catch (NoClassDefFoundError e) {}
            }
            """
                .trimIndent(),
            clazz,
        )
    )

    clazz.getDeclaredMethod("setup").insertBefore("registerHotswapAgentPlugin();")

    return clazz.toBytecode()
  }

  private fun instrumentPluginClassloader(clazz: CtClass): ByteArray {
    println("INSTRUMENTING THE PLUGIN CLASS LOADER")
    return clazz.toBytecode()
  }
}
