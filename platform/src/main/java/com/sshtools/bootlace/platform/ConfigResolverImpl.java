package com.sshtools.bootlace.platform;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;

import com.sshtools.bootlace.api.ConfigResolver;
import com.sshtools.bootlace.api.Plugin;

public class ConfigResolverImpl implements ConfigResolver {
	
	@Override
	public Path resolve(String appId, Scope scope, Class<? extends Plugin> plugin, String ext) {
		switch(scope) {
		case VENDOR:
			return resolveVendorPluginFile(appId, plugin, ext);
		default:
			return resolvePluginFile(appId, plugin, ext);			
		}
	}

	@Override
	public Path resolveVendorDir(String appId) {
		try {
			return checkDir(resolveConfDir(appId).resolve("vendor"));
		} catch (AccessDeniedException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public Path resolvePluginDir(String appId) {
		try {
			return checkDir(resolveConfDir(appId).resolve("plugins"));
		} catch (AccessDeniedException e) {
			throw new UncheckedIOException(e);
		}
	}

	private Path resolvePluginFile(String appId, Class<? extends Plugin> plugin, String ext) {
		return resolvePluginDir(appId).resolve(resolveFile(plugin, ext));
	}

	private Path resolveVendorPluginFile(String appId, Class<? extends Plugin> plugin, String ext) {
		return resolveVendorDir(appId).resolve(resolveFile(plugin, ext));
	}

	private Path resolveFile(Class<? extends Plugin> plugin, String ext) {
		return Paths.get(plugin.getName() + "." + ext);
	}

	private Path resolveConfDir(String appId) {
		try {
			return checkDir(Paths.get(System.getProperty(appId + ".config", "config")));
		}
		catch(AccessDeniedException ade) {
			try {
				return checkDir(Paths.get(System.getProperty("user.home")).resolve("." + appId).resolve("config"));
			} catch (AccessDeniedException e) {
				throw new UncheckedIOException(e);
			}
		}
	}

	private Path checkDir(Path path) throws AccessDeniedException {
		try {
			if (Files.exists(path)) {
				if(Files.isRegularFile(path)) {
					throw new IOException(MessageFormat.format("Expected path ''{0}'' to be a directory, but it was a file.", path));
				}
				if(!Files.isWritable(path)) {
					throw new AccessDeniedException(path.toString());
				}
			}
			else {
				Files.createDirectories(path);
			}
			return path;
		} catch(AccessDeniedException ade) {
			throw ade;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
