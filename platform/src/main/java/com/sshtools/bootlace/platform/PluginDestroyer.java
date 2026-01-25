package com.sshtools.bootlace.platform;

import com.sshtools.bootlace.api.Plugin;
import com.sshtools.bootlace.api.DefaultLayer;

public interface PluginDestroyer {
	void destroy(Plugin plugin, DefaultLayer player);
}
