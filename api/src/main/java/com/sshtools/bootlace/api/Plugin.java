package com.sshtools.bootlace.api;

public interface Plugin extends AutoCloseable {
	
	default void open(PluginContext context) throws Exception {
	}
	
	default void afterOpen(PluginContext context) throws Exception {
	}
	
	@Override
	default void close() throws Exception {
	}

	default void beforeClose(PluginContext context) throws Exception {
		
	}
}
