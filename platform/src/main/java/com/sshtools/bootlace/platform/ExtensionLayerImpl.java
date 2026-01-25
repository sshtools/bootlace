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
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.sshtools.bootlace.api.ExtensionLayer;
import com.sshtools.bootlace.api.Logs;
import com.sshtools.bootlace.api.Logs.BootLog;
import com.sshtools.bootlace.api.Logs.Log;
import com.sshtools.bootlace.platform.jini.INI;

/**
 * A Container layer that watches a particular directory to load further layers
 * from.
 */
public final class ExtensionLayerImpl extends AbstractStaticLayer implements ExtensionLayer {
	private final static Log LOG = Logs.of(BootLog.LAYERS);

	private final static class DefaultQueue {
		private final static ScheduledExecutorService DEFAULT = Executors.newScheduledThreadPool(1);
	}

	public final static class Builder extends AbstractStaticLayerBuilder<Builder> {
		private Optional<ScheduledExecutorService> queue = Optional.empty();
		private boolean directoryMonitor = true;
		private Duration changeDelay = Duration.ofSeconds(3);

		public Builder(String id) {
			super(id);
		}

		@Override
		protected Builder fromComponentSection(INI.Section section) {
			super.fromComponentSection(section);
			withDirectoryMonitor(section.getBooleanOr("monitor").orElse(true));
			section.getOr("delay").ifPresent(d -> {
				if (d.toLowerCase().endsWith("s")) {
					withChangeDelay(Duration.ofSeconds(Integer.parseInt(d.substring(0, d.length() - 1))));
				} else if (d.toLowerCase().endsWith("m")) {
					withChangeDelay(Duration.ofMinutes(Integer.parseInt(d.substring(0, d.length() - 1))));
				} else if (d.toLowerCase().endsWith("h")) {
					withChangeDelay(Duration.ofHours(Integer.parseInt(d.substring(0, d.length() - 1))));
				} else {
					withChangeDelay(Duration.ofSeconds(Integer.parseInt(d)));
				}
			});
			return this;
		}

		public Builder withChangeDelay(Duration changeDelay) {
			this.changeDelay = changeDelay;
			return this;
		}

		public Builder withDirectoryMonitor(boolean directoryMonitor) {
			this.directoryMonitor = directoryMonitor;
			return this;
		}

		public Builder withoutDirectoryMonitor() {
			return withDirectoryMonitor(false);
		}

		public ExtensionLayerImpl build() {
			return new ExtensionLayerImpl(this);
		}
	}

	private final Thread watchThread;

	private ScheduledFuture<?> changedTask;

	@SuppressWarnings("unused")
	private ExtensionLayerImpl(Builder builder) {
		super(builder);

		try {
			var queue = builder.queue.orElseGet(() -> DefaultQueue.DEFAULT);

			if (builder.directoryMonitor) {
				
				var watchService = FileSystems.getDefault().newWatchService();
				
				directory.register(
					watchService, 
					StandardWatchEventKinds.ENTRY_CREATE,
					StandardWatchEventKinds.ENTRY_DELETE, 
					StandardWatchEventKinds.ENTRY_MODIFY
				);

				watchThread = new Thread(() -> {
					try {
						WatchKey key;
						while ((key = watchService.take()) != null) {
							key.pollEvents().forEach(e -> {
								if (changedTask != null) {
									changedTask.cancel(false);
								}
								changedTask = queue.schedule(this::doRefresh, builder.changeDelay.toMillis(),
										TimeUnit.MILLISECONDS);
							});
							key.reset();
						}
					} catch (InterruptedException ie) {
					}
				}, "DynamicLayerMonitor" + hashCode());
				watchThread.start();
			}

			else {
				watchThread = null;
			}

		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

	}
	
	@Override
	protected void onRefresh() throws IOException {
			checkForDeletedLayers();
	}

	private void checkForDeletedLayers() throws IOException {
		extensions.values().stream().forEach(lyr -> {
			var id = lyr.id();
			var dir = directory.resolve(id);
			if (!Files.exists(dir)) {
				LOG.info("Removing layer {0}", id);
				closeLayer(lyr);
			}
		});
	}

	@Override
	protected Path defaultDirectory() {
		throw new IllegalStateException("DYNAMIC layers require a `directory`.");
	}

	private void doRefresh() {
		try {
			refresh();
		} catch (RuntimeException e) {
			LOG.error("Failed to refresh dynamic layers.", e);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}


}
