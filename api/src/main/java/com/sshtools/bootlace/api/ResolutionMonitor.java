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

import java.net.URI;
import java.util.Optional;

public interface ResolutionMonitor {
	default void have(GAV gav, URI uri, Repository remoteRepository) {
	}

	default void need(GAV gav, URI uri, Repository remoteRepository) {
	}

	default void found(GAV gav, URI uri, Repository remoteRepository, Optional<Long> size) {
	}

	default void downloading(GAV gav, URI uri, Repository remoteRepository, Optional<Long> bytes) {
	}

	default void downloaded(GAV gav, URI uri, Repository remoteRepository) {
	}

	default void failed(GAV gav, String location, Repository remoteRepository, Exception exception) {
	}
	
	default void loadingLayer(ChildLayer layerDef) {
	}
	
	default void loadedLayer(ChildLayer layerDef) {
	}

	static ResolutionMonitor monitor() {
		return DefaultResolutionMonitor.DEFAULT;
	}

	static ResolutionMonitor empty() {
		return DefaultResolutionMonitor.EMPTY;
	}

	public final static class DefaultResolutionMonitor {
		private final static ResolutionMonitor EMPTY = new ResolutionMonitor() {
		};
		private final static ResolutionMonitor DEFAULT = new ResolutionMonitor() {

			@Override
			public void loadingLayer(ChildLayer layerDef) {
				System.out.format("[Loading] %s%n", layerDef.id());
			}

			@Override
			public void loadedLayer(ChildLayer layerDef) {
				System.out.format("[Loaded] %s%n", layerDef.id());
			}

			@Override
			public void have(GAV gav, URI uri, Repository remoteRepository) {
				if(remoteRepository == null)
					System.out.format("    [Have] %s @ %s%n", gav, uri);
				else
					System.out.format("    [Have] %s @ %s in %s%n", gav, uri, remoteRepository.name());
			}

			@Override
			public void need(GAV gav, URI uri, Repository remoteRepository) {
				System.out.format("    [Need] %s @ %s in %s%n", gav, uri, remoteRepository.name());
			}

			@Override
			public void found(GAV gav, URI uri, Repository remoteRepository, Optional<Long> size) {
				System.out.format("    [Downloading] %s @ %s in %s%n", gav, uri, remoteRepository.name());
			}

			@Override
			public void downloading(GAV gav, URI uri, Repository remoteRepository, Optional<Long> bytes) {
			}

			@Override
			public void downloaded(GAV gav, URI uri, Repository remoteRepository) {
				System.out.format("    [Downloaded] %s%n", gav);
			}

			@Override
			public void failed(GAV gav, String location, Repository remoteRepository, Exception exception) {
				System.out.format("    [Failed!] %s%n", exception.getMessage());
			}

		};
	}
}
