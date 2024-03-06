package com.sshtools.bootlace.api;

import java.net.URL;
import java.util.Optional;

public interface RootContext {

	public interface Listener {
		void layerOpened(ChildLayer layer);

		void layerClosed(ChildLayer layer);
	}

//	
	BootContext app();
	
	Optional<URL> globalResource(String path);

	boolean canShutdown(); 

	void shutdown();

	boolean canRestart();

	void restart();

	void addListener(Listener listener);

	void removeListener(Listener listener);

}