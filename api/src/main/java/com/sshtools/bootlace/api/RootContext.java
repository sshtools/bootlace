package com.sshtools.bootlace.api;

public interface RootContext {

	public interface Listener {
		void layerOpened(ChildLayer layer);

		void layerClosed(ChildLayer layer);
	}

//	
	BootContext app();

	boolean canShutdown(); 

	void shutdown();

	boolean canRestart();

	void restart();

	void addListener(Listener listener);

	void removeListener(Listener listener);

}