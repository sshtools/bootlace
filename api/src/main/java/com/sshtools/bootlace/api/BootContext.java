package com.sshtools.bootlace.api;

public interface BootContext {

	public static BootContext named(String name) {
		return new BootContext() {
			public String name() {
				return name;
			}

			@Override
			public boolean canShutdown() {
				return false;
			}

			@Override
			public void shutdown() {
				throw new UnsupportedOperationException();
			}

			@Override
			public boolean canRestart() {
				return false;
			}

			@Override
			public void restart() {
				throw new UnsupportedOperationException();
			}
		};
	}
	
	String name();

	boolean canShutdown();

	void shutdown();

	boolean canRestart();

	void restart();
}