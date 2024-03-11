/**
 * Copyright © 2023 JAdaptive Limited (support@jadaptive.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the “Software”), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies
 * or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
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
	public Path resolveDir(String appId, Scope scope) {
		try {
			switch(scope) {
			case VENDOR:
				return checkDir(resolveConfDir(appId).resolve("vendor"));
			default:
				return checkDir(resolveConfDir(appId).resolve("extensions"));			
			}
		} catch (AccessDeniedException e) {
			throw new UncheckedIOException(e);
		}
	}

	private Path resolvePluginFile(String appId, Class<? extends Plugin> plugin, String ext) {
		return resolveDir(appId, Scope.PLUGINS).resolve(resolveFile(plugin, ext));
	}

	private Path resolveVendorPluginFile(String appId, Class<? extends Plugin> plugin, String ext) {
		return resolveDir(appId, Scope.VENDOR).resolve(resolveFile(plugin, ext));
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
