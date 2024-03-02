package com.sshtools.bootlace.platform;

import com.sshtools.bootlace.api.PluginContext;

public final class PluginContextProviderImpl implements PluginContext.Provider {
	
	final static ThreadLocal<PluginContext> current = new ThreadLocal<>(); 

	@Override
	public PluginContext get() {
		var ctx = current.get();
		if(ctx == null)
			throw new IllegalStateException("No current plugin context.");
		return ctx;
	}
	
}