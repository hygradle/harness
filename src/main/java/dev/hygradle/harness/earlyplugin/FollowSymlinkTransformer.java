package dev.hygradle.harness.earlyplugin;

import com.hypixel.hytale.plugin.early.ClassTransformer;
import javassist.ByteArrayClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.Modifier;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

public class FollowSymlinkTransformer implements ClassTransformer {

  @NullableDecl
  @Override
  public byte[] transform(@NonNullDecl String name, @NonNullDecl String path,
      @NonNullDecl byte[] bytes) {

    if (!name.equals("com.hypixel.hytale.server.core.util.io.FileUtil")) {
      return null;
    }

    try {
      ClassPool pool = new ClassPool(true);
      pool.insertClassPath(new ByteArrayClassPath(name, bytes));

      CtClass clazz = pool.get(name);

      CtField setField = clazz.getDeclaredField("DEFAULT_WALK_TREE_OPTIONS_SET");
      setField.setModifiers(setField.getModifiers() & ~Modifier.FINAL);

      CtField arrayField = clazz.getDeclaredField("DEFAULT_WALK_TREE_OPTIONS_ARRAY");
      arrayField.setModifiers(arrayField.getModifiers() & ~Modifier.FINAL);

      clazz.makeClassInitializer().insertAfter("""
          DEFAULT_WALK_TREE_OPTIONS_SET = java.util.Set.of(java.nio.file.FileVisitOption.FOLLOW_LINKS);
          DEFAULT_WALK_TREE_OPTIONS_ARRAY = new java.nio.file.FileVisitOption[] {
            java.nio.file.FileVisitOption.FOLLOW_LINKS
          };
          """);

      byte[] clazzBytes = clazz.toBytecode();
      clazz.detach();
      System.out.println("[FollowSymlinkTransformer] Successfully transformed FileUtil to follow symlinks");
      return clazzBytes;
    } catch (Exception e) {
      System.err.println("[FollowSymlinkTransformer] Failed to transform FileUtil: " + e);
      e.printStackTrace();
      return null;
    }
  }
}
