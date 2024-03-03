package com.sshtools.bootlace.api;

import java.nio.file.Files;
import java.nio.file.Paths;

public interface BootContext {
	
	public static boolean isDeveloper() {
		return "true"
				.equals(System.getProperty("bootlace.dev", String.valueOf(Files.exists(Paths.get("pom.xml")))));
	}

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