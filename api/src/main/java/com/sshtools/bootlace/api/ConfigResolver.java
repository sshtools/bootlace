package com.sshtools.bootlace.api;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.ServiceLoader;

public interface ConfigResolver {
	
	public static ConfigResolver get(ModuleLayer layer) {
		return ServiceLoader.load(layer, ConfigResolver.class).findFirst()
				.orElseThrow(() -> new IllegalStateException("No configuration resolver available."));
	}
	
	public enum Scope {
		USER, VENDOR
	}

	Path resolve(String appId, Scope scope, Class<? extends Plugin> plugin, String ext);

	Path resolvePluginDir(String appId);

	Path resolveVendorDir(String appId);
	
	public static Properties properties(Path path) {
		if (Files.exists(path)) {
			try(var in = Files.newInputStream(path)) {
				var p = new Properties();
				p.load(in);
				return p;
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			} 
		} else {
			return new Properties();
		}
	}
}
