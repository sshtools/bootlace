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
package com.sshtools.bootlace.api;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.ServiceLoader;

/**
 * The framework is expected to provide a location the store configuration for a
 * given plugin.
 * <p>
 * Implementations of this interface are responsible for provide these
 * locations.
 */
public interface ConfigResolver {

	/**
	 * Discover a {@link ConfigResolver} to use.
	 * 
	 * @param layer layer to load from
	 * @return configuration resolver
	 */
	public static ConfigResolver get(ModuleLayer layer) {
		return ServiceLoader.load(layer, ConfigResolver.class).findFirst()
				.orElseThrow(() -> new IllegalStateException("No configuration resolver available."));
	}

	/**
	 * The scope of the configuration
	 */
	public enum Scope {
		/**
		 * Resolves to a location suitable for both reading and writing configuration 
		 * for this applications plugins.   
		 */
		PLUGINS, 
		/**
		 * Resolves to a location suitable for read only access to default configuration 
		 * provided by the vendor of the application. 
		 */
		VENDOR
	}

	/**
	 * Resolve a file path suitable for written configuration for the given applications plugin and scope.
	 * 
	 * @param appId application ID
	 * @param scope scope
	 * @param plugin plugin
	 * @param ext file extension
	 * @return path
	 */
	Path resolve(String appId, Scope scope, Class<? extends Plugin> plugin, String ext);

	/**
	 * Resolve a directory path suitable for written configuration for the given application and scope.
	 * 
	 * @param appId application ID
	 * @param scope scope
	 * @return path
	 */
	Path resolveDir(String appId, Scope scope);

	/**
	 * Utility to load {@link Properties} from a {@link Path}.
	 * 
	 * @param path path
	 * @return properties
	 */
	public static Properties properties(Path path) {
		if (Files.exists(path)) {
			try (var in = Files.newInputStream(path)) {
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
