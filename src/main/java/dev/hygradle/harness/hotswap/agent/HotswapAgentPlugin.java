package dev.hygradle.harness.hotswap.agent;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.logging.AgentLogger;

@Plugin(name = "Hygradle", testedVersions = {"*"})
public class HotswapAgentPlugin {

  public static final AgentLogger LOGGER = AgentLogger.getLogger(HotswapAgentPlugin.class);

  @Init
  ClassLoader appClassLoader;

  @Init
  Scheduler scheduler;

  @OnClassLoadEvent(classNameRegexp = "com.hypixel.hytale.server.core.plugin.PluginClassLoader", events = LoadEvent.DEFINE)
  public static void onPluginClassLoaderDefined(CtClass clazz) {
    LOGGER.info("PLUGIN CLASS LOADER DEFINED");
  }
}
