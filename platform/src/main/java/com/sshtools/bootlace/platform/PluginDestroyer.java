package com.sshtools.bootlace.platform;

import com.sshtools.bootlace.api.Plugin;
import com.sshtools.bootlace.api.PluginLayer;

public interface PluginDestroyer {
	void destroy(Plugin plugin, PluginLayer player);
}
