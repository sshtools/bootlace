package com.sshtools.bootlace.platform;

import java.util.ServiceLoader;
import java.util.Set;

import com.sshtools.bootlace.api.Plugin;
import com.sshtools.bootlace.api.DefaultLayer;

@FunctionalInterface
public interface PluginInitializer {
	 Plugin initialize(ServiceLoader.Provider<Plugin> pluginLoader, DefaultLayer layer, Set<ModuleLayer> parents);
}
