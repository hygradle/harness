package dev.hygradle.harness.early

import com.hypixel.hytale.plugin.early.ClassTransformer
import javassist.expr.ExprEditor
import javassist.expr.MethodCall

class PluginLoadOrderTransformer : ClassTransformer {
  override fun transform(name: String, path: String, bytes: ByteArray): ByteArray? =
      when (name) {
        "com.hypixel.hytale.server.core.plugin.pending.PendingLoadPlugin" ->
            withClazz(name, bytes) {
              getDeclaredMethod("calculateLoadOrder")
                  .instrument(
                      object : ExprEditor() {
                        override fun edit(m: MethodCall) {
                          if (m.methodName == "isInServerClassPath") {
                            m.replace(
                                $$"""
                                $_ = $proceed()
                                    && ($0.getPath() == null
                                        || $0.getPath().toString().endsWith(".jar"));
                                """
                            )
                          }
                        }
                      }
                  )
            }
        else -> null
      }
}
