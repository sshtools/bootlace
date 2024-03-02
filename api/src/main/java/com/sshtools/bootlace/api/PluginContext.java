package com.sshtools.bootlace.api;

import java.io.Closeable;
import java.util.Optional;
import java.util.ServiceLoader;

public interface PluginContext extends Closeable {
	

	public interface Provider {
		PluginContext get();
	}

	public static PluginContext $() {
		return ServiceLoader.load(Provider.class).findFirst().orElseThrow(() -> new IllegalStateException("No plugin context")).get();
	}
	
	void autoClose(AutoCloseable... closeables);
	
	RootContext root();
	
	 default <P extends Plugin> P plugin(Class<P> plugin) {
		 return pluginOr(plugin).orElseThrow(() -> new Exceptions.MissingPluginClassException(plugin));
	 }
	
	 <P extends Plugin> Optional<P> pluginOr(Class<P> plugin);
	 
	 @Override
	 void close();
}