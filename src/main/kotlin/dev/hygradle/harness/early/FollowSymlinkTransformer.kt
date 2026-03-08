package dev.hygradle.harness.early

import com.hypixel.hytale.plugin.early.ClassTransformer
import javassist.ByteArrayClassPath
import javassist.ClassPool
import javassist.CtClass
import javassist.Modifier
import javassist.expr.ExprEditor
import javassist.expr.MethodCall

/**
 * Patches both the FileUtil and AssetStore callsites to ensure they follow symlinks when loading
 * assets.
 */
class FollowSymlinkTransformer : ClassTransformer {
  override fun transform(name: String, path: String, bytes: ByteArray): ByteArray? =
      when (name) {
        // Ensures Common asset loading follows symlinks
        "com.hypixel.hytale.server.core.util.io.FileUtil" ->
            withClazz(name, bytes) {
              getDeclaredField("DEFAULT_WALK_TREE_OPTIONS_SET").also {
                it.modifiers = it.modifiers and Modifier.FINAL.inv()
              }

              getDeclaredField("DEFAULT_WALK_TREE_OPTIONS_ARRAY").also {
                it.modifiers = it.modifiers and Modifier.FINAL.inv()
              }

              makeClassInitializer()
                  .insertAfter(
                      """
                      DEFAULT_WALK_TREE_OPTIONS_SET = java.util.Set.of(java.nio.file.FileVisitOption.FOLLOW_LINKS);

                      DEFAULT_WALK_TREE_OPTIONS_ARRAY = new java.nio.file.FileVisitOption[] {
                          java.nio.file.FileVisitOption.FOLLOW_LINKS
                      };
                      """
                          .trimIndent()
                  )
            }

        "com.hypixel.hytale.assetstore.AssetStore" ->
            withClazz(name, bytes) {
              getDeclaredMethod("loadAssetsFromDirectory")
                  .instrument(
                      object : ExprEditor() {
                        override fun edit(m: MethodCall) {
                          if (m.className == "java.util.Set" && m.methodName == "of")
                              m.replace(
                                  $$"$_ = java.util.Set.of(java.nio.file.FileVisitOption.FOLLOW_LINKS);"
                              )
                        }
                      }
                  )
            }

        else -> null
      }
}

internal fun withClazz(name: String, bytes: ByteArray, block: CtClass.() -> Unit): ByteArray {
  val pool = ClassPool(true).also { it.insertClassPath(ByteArrayClassPath(name, bytes)) }
  val clazz = pool.get(name)

  block.invoke(clazz)

  val resultBytes = clazz.toBytecode()
  clazz.detach()
  return resultBytes
}
