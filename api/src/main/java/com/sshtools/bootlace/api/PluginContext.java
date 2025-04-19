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

import java.io.Closeable;
import java.util.Optional;
import java.util.ServiceLoader;

public interface PluginContext extends Closeable {
	
	public record PluginHostInfo(String app, String version, String displayName) {
		public PluginHostInfo(String app) {
			this(app, "0.0.0", app);
		}
	}

	public interface Provider {
		PluginContext get();
	}

	public static PluginContext $() {
		return ServiceLoader.load(Provider.class).findFirst()
				.orElseThrow(() -> new IllegalStateException("No plugin context")).get();
	}
	
	Layer layer();

	Optional<Layer> layer(String id);

	void autoClose(AutoCloseable... closeables);

	RootContext root();

	default <P extends Plugin> P plugin(Class<P> plugin) {
		return pluginOr(plugin).orElseThrow(() -> new Exceptions.MissingPluginClassException(plugin));
	}

	<P extends Plugin> Optional<P> pluginOr(Class<P> plugin);

	@Override
	void close();

	boolean hasPlugin(String className);
}