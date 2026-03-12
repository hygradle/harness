package dev.hygradle.harness.early

import com.hypixel.hytale.plugin.early.ClassTransformer

// In development, players should have all permissions.
class PermissionsTransformer : ClassTransformer {
  override fun transform(name: String, path: String, bytes: ByteArray): ByteArray? =
      when (name) {
        "com.hypixel.hytale.server.core.entity.entities.Player" ->
            withClazz(name, bytes) {
              declaredMethods
                  .filter { it.name == "hasPermission" }
                  .forEach { it.setBody("{ return true; }") }
            }
        else -> null
      }
}
