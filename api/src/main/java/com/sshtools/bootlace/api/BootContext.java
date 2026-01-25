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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.sshtools.bootlace.api.PluginContext.PluginHostInfo;

/**
 * Public API that allows access to the containing application bootstrap.
 */
public interface BootContext {
	
	public static boolean isDeveloper() {
		return "true"
				.equals(System.getProperty("bootlace.dev", String.valueOf(Files.exists(Paths.get("pom.xml")))));
	}

	public static BootContext named(String name) {
		return host(new PluginHostInfo(name));
	}
	
	public static BootContext host(PluginHostInfo host) {
		return new BootContext() {
			public PluginHostInfo info() {
				return host;
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
	
	/**
	 * The information about the plugin host. This is typically the name of the application making
	 * use of <strong>Bootlace</strong>.
	 * 
	 * @return info
	 */
	PluginHostInfo info();

	/**
	 * Get whether all layers, i.e. the entire application, may be shutdown.
	 *  
	 * @return can shutdown
	 */
	boolean canShutdown();

	/**
	 * Shutdown all layers, i.e. the entire application.
	 */
	void shutdown();

	/**
	 * Get whether all layers, i.e. the entire application, may be restarted.
	 *  
	 * @return can restart
	 */
	boolean canRestart();

	/**
	 * Restart all layers, i.e. the entire application.
	 */
	void restart();
	
	/**
	 * Root path
	 */
	default Path basePath() {
		return Paths.get(System.getProperty("user.dir"));
	}
}