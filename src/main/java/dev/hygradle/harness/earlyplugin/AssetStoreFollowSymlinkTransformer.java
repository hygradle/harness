package dev.hygradle.harness.earlyplugin;

import com.hypixel.hytale.plugin.early.ClassTransformer;
import javassist.ByteArrayClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

public class AssetStoreFollowSymlinkTransformer implements ClassTransformer {

  @NullableDecl
  @Override
  public byte[] transform(@NonNullDecl String name, @NonNullDecl String path,
      @NonNullDecl byte[] bytes) {

    if (!name.equals("com.hypixel.hytale.assetstore.AssetStore")) {
      return null;
    }

    try {
      ClassPool pool = new ClassPool(true);
      pool.insertClassPath(new ByteArrayClassPath(name, bytes));

      CtClass clazz = pool.get(name);
      CtMethod method = clazz.getDeclaredMethod("loadAssetsFromDirectory");

      method.instrument(new ExprEditor() {
        @Override
        public void edit(MethodCall m) throws javassist.CannotCompileException {
          if (m.getClassName().equals("java.util.Set") && m.getMethodName().equals("of")) {
            m.replace("$_ = java.util.Set.of(java.nio.file.FileVisitOption.FOLLOW_LINKS);");
          }
        }
      });

      byte[] clazzBytes = clazz.toBytecode();
      clazz.detach();
      System.out.println(
          "[AssetStoreFollowSymlinkTransformer] Successfully transformed AssetStore.loadAssetsFromDirectory to follow symlinks");
      return clazzBytes;
    } catch (Exception e) {
      System.err.println(
          "[AssetStoreFollowSymlinkTransformer] Failed to transform AssetStore: " + e);
      e.printStackTrace();
      return null;
    }
  }
}
