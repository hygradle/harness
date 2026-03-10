package dev.hygradle.harness.hotswap.agent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.logging.AgentLogger;

@Plugin(name = "HygradleHarness", testedVersions = {"*"})
public class HotswapAgentPlugin {

  private static final AgentLogger LOGGER = AgentLogger.getLogger(HotswapAgentPlugin.class);
  private static final int RELOAD_TIMEOUT_MS = 500;

  private static ClassLoader appClassLoaderRef;
  private static List<Object> classpathPluginIdentifiers;
  private static ReflectionCache reflection;
  private static SchedulerAccess schedulerAccess;
  private static final Map<Object, Object> reloadCommands = new HashMap<>();

  @Init
  public static void init(ClassLoader appClassLoader) {
    if (appClassLoader == null) {
      return;
    }
    appClassLoaderRef = appClassLoader;
    LOGGER.info("Hygradle plugin initialized for classloader {}", appClassLoader);
  }

  @OnClassLoadEvent(
      classNameRegexp = "com.hypixel.hytale.server.core.plugin.PluginClassLoader",
      events = LoadEvent.DEFINE)
  public static void onPluginClassLoaderDefined(CtClass clazz) {
    LOGGER.info("PluginClassLoader defined — reload support active");
  }

  @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE)
  public static void onClassRedefined(CtClass ctClass) {
    if (appClassLoaderRef == null) {
      return;
    }
    handleClassRedefined(ctClass.getName());
  }

  private static void handleClassRedefined(String className) {
    try {
      if (classpathPluginIdentifiers == null) {
        classpathPluginIdentifiers = discoverClasspathPlugins();
        if (classpathPluginIdentifiers.isEmpty()) {
          return;
        }
      }

      LOGGER.info("Class redefined: {}", className);

      SchedulerAccess sa = getSchedulerAccess();
      for (Object pluginIdentifier : classpathPluginIdentifiers) {
        Object cmd =
            reloadCommands.computeIfAbsent(
                pluginIdentifier, id -> sa.createCommand(() -> reloadPlugin(id)));
        sa.scheduleCommand(cmd, RELOAD_TIMEOUT_MS);
      }
    } catch (Exception e) {
      LOGGER.debug("Failed to process class redefinition for {}: {}", className, e.getMessage());
    }
  }

  private static SchedulerAccess getSchedulerAccess() throws ReflectiveOperationException {
    if (schedulerAccess == null) {
      schedulerAccess = new SchedulerAccess();
    }
    return schedulerAccess;
  }

  @SuppressWarnings("unchecked")
  private static List<Object> discoverClasspathPlugins() {
    List<Object> result = new ArrayList<>();
    try {
      ReflectionCache r = getReflection();
      Object pluginManager = r.pluginManagerGet.invoke(null);
      if (pluginManager == null) {
        return result;
      }

      Map<Path, ?> classLoaders = (Map<Path, ?>) r.classLoadersField.get(pluginManager);

      for (Map.Entry<Path, ?> entry : classLoaders.entrySet()) {
        // Server builtins are loaded from JAR files, dev plugins from directories
        if (entry.getKey().toString().endsWith(".jar")) {
          continue;
        }

        Object pcl = entry.getValue();
        boolean inClasspath = (boolean) r.isInServerClassPath.invoke(pcl);
        if (!inClasspath) {
          continue;
        }

        Object javaPlugin = r.pluginField.get(pcl);
        if (javaPlugin == null) {
          continue;
        }

        Object identifier = r.getIdentifier.invoke(javaPlugin);
        result.add(identifier);
        LOGGER.info("Discovered classpath dev plugin: {}", identifier);
      }
    } catch (Exception e) {
      LOGGER.error("Failed to discover classpath plugins", e);
    }
    return result;
  }

  private static ReflectionCache getReflection() throws ReflectiveOperationException {
    if (reflection == null) {
      reflection = new ReflectionCache(appClassLoaderRef);
    }
    return reflection;
  }

  private static void reloadPlugin(Object pluginIdentifier) {
    try {
      ReflectionCache r = getReflection();
      Object pluginManager = r.pluginManagerGet.invoke(null);
      Object plugin = r.getPlugin.invoke(pluginManager, pluginIdentifier);
      if (plugin == null) {
        LOGGER.error("Plugin {} not found, skipping reload", pluginIdentifier);
        return;
      }

      Object state = r.getState.invoke(plugin);
      if (!"ENABLED".equals(state.toString())) {
        LOGGER.warning(
            "Plugin {} is not ENABLED (state: {}), skipping reload", pluginIdentifier, state);
        return;
      }

      LOGGER.info("Reloading plugin {}...", pluginIdentifier);

      r.shutdown0.invoke(plugin, false);
      LOGGER.info("Plugin {} shut down, restarting lifecycle...", pluginIdentifier);

      Object preloadFuture = r.preLoad.invoke(plugin);
      if (preloadFuture == null) {
        r.setup0.invoke(plugin);
        r.start0.invoke(plugin);
        LOGGER.info("Plugin {} reloaded successfully", pluginIdentifier);
      } else {
        ((CompletableFuture<?>) preloadFuture)
            .thenRun(
                () -> {
                  try {
                    r.setup0.invoke(plugin);
                    r.start0.invoke(plugin);
                    LOGGER.info("Plugin {} reloaded successfully", pluginIdentifier);
                  } catch (Exception e) {
                    LOGGER.error(
                        "Failed to restart plugin {} after preLoad", e, pluginIdentifier);
                  }
                });
      }
    } catch (Exception e) {
      LOGGER.error("Failed to reload plugin {}", e, pluginIdentifier);
    }
  }

  /** Accesses HotswapAgent's Scheduler and Command via reflection to avoid compile-time deps. */
  private static class SchedulerAccess {
    private final Object scheduler;
    private final Method scheduleCommandMethod;
    private final Class<?> commandInterface;

    SchedulerAccess() throws ReflectiveOperationException {
      Class<?> pmClass = Class.forName("org.hotswap.agent.config.PluginManager");
      Object pm = pmClass.getMethod("getInstance").invoke(null);
      scheduler = pmClass.getMethod("getScheduler").invoke(pm);
      commandInterface = Class.forName("org.hotswap.agent.command.Command");
      scheduleCommandMethod =
          scheduler.getClass().getMethod("scheduleCommand", commandInterface, int.class);
    }

    void scheduleCommand(Object cmd, int timeout) throws ReflectiveOperationException {
      scheduleCommandMethod.invoke(scheduler, cmd, timeout);
    }

    Object createCommand(Runnable action) {
      return Proxy.newProxyInstance(
          commandInterface.getClassLoader(),
          new Class<?>[] {commandInterface},
          (proxy, method, args) ->
              switch (method.getName()) {
                case "executeCommand" -> {
                  action.run();
                  yield null;
                }
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> "ReloadCommand";
                default -> null;
              });
    }
  }

  private static class ReflectionCache {
    final Method pluginManagerGet;
    final Method getPlugin;
    final Method getState;
    final Field classLoadersField;
    final Field pluginField;
    final Method isInServerClassPath;
    final Method getIdentifier;
    final Method shutdown0;
    final Method setup0;
    final Method start0;
    final Method preLoad;

    ReflectionCache(ClassLoader cl) throws ReflectiveOperationException {
      Class<?> pluginManagerClass =
          cl.loadClass("com.hypixel.hytale.server.core.plugin.PluginManager");
      Class<?> pluginIdClass =
          cl.loadClass("com.hypixel.hytale.common.plugin.PluginIdentifier");
      Class<?> pclClass =
          cl.loadClass("com.hypixel.hytale.server.core.plugin.PluginClassLoader");
      Class<?> pluginBaseClass =
          cl.loadClass("com.hypixel.hytale.server.core.plugin.PluginBase");

      pluginManagerGet = pluginManagerClass.getMethod("get");
      getPlugin = pluginManagerClass.getMethod("getPlugin", pluginIdClass);

      classLoadersField = pluginManagerClass.getDeclaredField("classLoaders");
      classLoadersField.setAccessible(true);

      pluginField = pclClass.getDeclaredField("plugin");
      pluginField.setAccessible(true);

      isInServerClassPath = pclClass.getMethod("isInServerClassPath");

      getIdentifier = pluginBaseClass.getMethod("getIdentifier");
      getState = pluginBaseClass.getMethod("getState");

      shutdown0 = pluginBaseClass.getDeclaredMethod("shutdown0", boolean.class);
      shutdown0.setAccessible(true);

      setup0 = pluginBaseClass.getDeclaredMethod("setup0");
      setup0.setAccessible(true);

      start0 = pluginBaseClass.getDeclaredMethod("start0");
      start0.setAccessible(true);

      preLoad = pluginBaseClass.getMethod("preLoad");
    }
  }
}
