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
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.sshtools.bootlace.api.BootContext;
import com.sshtools.bootlace.api.Exceptions.NotALayer;
import com.sshtools.bootlace.api.ExtensionLayer;
import com.sshtools.bootlace.api.Layer;
import com.sshtools.bootlace.api.Logs;
import com.sshtools.bootlace.api.Logs.BootLog;
import com.sshtools.bootlace.api.Logs.Log;
import com.sshtools.bootlace.api.Zip;
import com.sshtools.jini.INI;

/**
 * A Container layer that watches a particular directory to load further layers
 * from.
 */
public final class ExtensionLayerImpl extends AbstractChildLayer implements ExtensionLayer {
	private final static Log LOG = Logs.of(BootLog.LAYERS);

	private final static class DefaultQueue {
		private final static ScheduledExecutorService DEFAULT = Executors.newScheduledThreadPool(1);
	}

	public final static class Builder extends AbstractChildLayerBuilder<Builder> {
		private Optional<Path> directory = Optional.empty();
		private Optional<Path> fallbackDirectory = Optional.of(Paths.get(".bootlace"));
		private Optional<ScheduledExecutorService> queue = Optional.empty();
		private boolean directoryMonitor = true;
		private Duration changeDelay = Duration.ofSeconds(3);
		private Set<String> allowParents = new HashSet<>();
		private Set<String> denyParents = new HashSet<>();

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
			withDirectory(section.getOr("directory").map(Paths::get));
			withAllowParents(section.getAllOr("allowParent").orElse(new String[0]));
			withAllowParents(section.getAllOr("allowParents").orElse(new String[0]));
			withAllowParents(section.getAllOr("denyParent").orElse(new String[0]));
			withAllowParents(section.getAllOr("denyParents").orElse(new String[0]));
			return this;
		}

		public Builder withAllowParents(String... allowParents) {
			return withAllowParents(Arrays.asList(allowParents));
		}

		public Builder withAllowParents(Collection<String> allowParents) {
			this.allowParents.addAll(allowParents);
			return this;
		}

		public Builder withDenyParents(String... denyParents) {
			return withDenyParents(Arrays.asList(denyParents));
		}

		public Builder withDenyParents(Collection<String> denyParents) {
			this.denyParents.addAll(denyParents);
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

		public Builder withDirectory(String directory) {
			return withDirectory(Paths.get(directory));
		}

		public Builder withDirectory(Optional<Path> directory) {
			this.directory = directory;
			return this;
		}

		public Builder withDirectory(Path directory) {
			return withDirectory(Optional.of(directory));
		}

		public Builder withFallbackDirectory(Path fallbackDirectory) {
			return withFallbackDirectory(Optional.of(fallbackDirectory));
		}

		public Builder withFallbackDirectory(Optional<Path> fallbackDirectory) {
			this.fallbackDirectory = fallbackDirectory;
			return this;
		}
		
		public ExtensionLayerImpl build() {
			return new ExtensionLayerImpl(this);
		}
	}

	private final Path directory;
	private final Thread watchThread;
	private final Map<String, AbstractChildLayer> extensions = new ConcurrentHashMap<>();
	private final Set<String> allowParents;
	private final Set<String> denyParents;
	private final Optional<Path> fallbackDirectory;

	private ScheduledFuture<?> changedTask;

	private ExtensionLayerImpl(Builder builder) {
		super(builder);
		fallbackDirectory = builder.fallbackDirectory;
		allowParents = Collections.unmodifiableSet(new HashSet<>(builder.allowParents));
		denyParents = Collections.unmodifiableSet(new HashSet<>(builder.denyParents));

		try {
			directory = resolveDirectory(builder);
			var queue = builder.queue.orElseGet(() -> DefaultQueue.DEFAULT);

			if (builder.directoryMonitor) {
				var watchService = FileSystems.getDefault().newWatchService();
				directory.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
						StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);

				watchThread = new Thread(() -> {
					try {
						WatchKey key;
						while ((key = watchService.take()) != null) {
							key.pollEvents().forEach(e -> {
								if (changedTask != null) {
									changedTask.cancel(false);
								}
								changedTask = queue.schedule(this::refresh, builder.changeDelay.toMillis(),
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
	public Collection<? extends Layer> extensions() { 
		return extensions.values();
	}

	@Override
	public Path path() {
		return directory;
	}

	private Path resolveDirectory(Builder builder) throws IOException {
		var directory = builder.directory.orElseGet(() -> defaultPluginsRoot());

		try {
			if (Files.exists(directory)) {
				var flag = directory.resolve(".bootlace.flag");
				try {
					Files.createFile(flag);
				} finally {
					Files.deleteIfExists(flag);
				}
			} else {
				Files.createDirectories(directory);
			}
		} catch (AccessDeniedException ade) {
			if (fallbackDirectory.isPresent()) {
				var fallback = fallbackDirectory.get();
				if (!fallback.isAbsolute()) {
					fallback = Paths.get(System.getProperty("user.home")).resolve(".bootlace").resolve(directory.getFileName());
				}
				LOG.info("No access to {0}, falling back to {1} for dynamic layers", directory, fallback);
				Files.createDirectories(fallback);
				return fallback;
			} else
				throw ade;
		}
		
		return directory;
	}

	@Override
	public void onOpened() {
		refresh();
	}

	@Override
	public String toString() {
		return "DynamicLayer [id()=" + id() + ", name()=" + name() + ", parents()=" + parents() + ", appRepositories()=" + appRepositories() + ", localRepositories()=" + localRepositories()
				+ ", remoteRepositories()=" + remoteRepositories() + ", monitor()=" + monitor() + ", directory="
				+ directory + ", allowParents=" + allowParents + ", denyParents=" + denyParents + "]";
	}

	private void checkForArchives() throws IOException {
		try (var stream = Files.newDirectoryStream(directory,
				p -> p.getFileName().toString().toLowerCase().endsWith(".zip"))) {
			for (var zip : stream) {
				try {
					var descriptor = new Descriptor.Builder().fromArtifact(zip).build();
					var layer = descriptor.component();
					var id = layer.get("id");
					var dir = zip.getParent().resolve(id);

					var backupDir = dir.getParent().resolve(dir.getFileName() + ".backup");

					try {
						if (Files.exists(dir)) {
							if (Files.exists(backupDir)) {
								recursiveDelete(backupDir);
							}
							Files.move(dir, backupDir);
						}

						Files.createDirectories(dir);
						Zip.unzip(zip, dir);
						Files.delete(zip);

					} catch (IOException ioe) {
						try {

							var failedFile = zip.getParent().resolve(zip.getFileName() + ".failed");
							if (Files.exists(failedFile)) {
								Files.delete(failedFile);
							}
							Files.move(zip, failedFile);

							recursiveDelete(dir);
							if (Files.exists(backupDir)) {
								Files.move(backupDir, dir);
							}
							throw ioe;
						} catch (IOException ioe2) {
							throw new UncheckedIOException(ioe2);
						}
					}
				} catch (NoSuchFileException nsfe) {
					// No descriptor
				}

			}
		} catch (UncheckedIOException ioe) {
			throw ioe.getCause();
		}
	}

	private static void recursiveDelete(Path fileOrDirectory, FileVisitOption... options) {
		try (var walk = Files.walk(fileOrDirectory, options)) {
			walk.sorted(Comparator.reverseOrder()).forEach(p -> {
				try {
					Files.delete(p);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		} catch (IOException ioe) {
			throw new UncheckedIOException(ioe);
		}
	}

	private void refresh() {
		try {
			checkForArchives();
			checkForLoadableLayers();
			checkForDeletedLayers();
		} catch (RuntimeException e) {
			e.printStackTrace();
			LOG.error("Failed to refresh dynamic layers.", e);
		} catch (IOException e) {
//			LOG.warning("Failed to refresh dynamic layers.", e);
			throw new UncheckedIOException(e);
		}
	}

	private void checkForDeletedLayers() throws IOException {
		var removed = new HashSet<String>();
		extensions.forEach((id, layer) -> {

			var dir = directory.resolve(id);
			if (!Files.exists(dir)) {
				removed.add(id);
				var appLayer = (RootLayerImpl) rootLayer()
						.orElseThrow(() -> new IllegalStateException("Dynamic layer not attached to app layer."));
				try {
					appLayer.beforeClose(layer);
				}
				finally {
					try {
						appLayer.close(layer);
					}
					finally {
						appLayer.removeLayer(id);
						extensions.remove(id);
						layer.rootLayer(null);
					}
				}
			}
		});
		for (var layer : removed) {
			extensions.remove(layer);
		}
	}

	private void checkForLoadableLayers() throws IOException {
		try (var stream = Files.newDirectoryStream(directory,
				(f) -> Files.isDirectory(f) && 
				!f.getFileName().toString().endsWith(".backup") && 
				!f.getFileName().toString().endsWith(".tmp"))) {
			for (var dir : stream) {
				Descriptor descriptor = null;
				try (var dirStream = Files.newDirectoryStream(dir)) {
					for (var jar : dirStream) {
						
						if(LOG.debug())
							LOG.debug("Checking Jar {0}", jar);
						
						try {
							var nextDescriptor = new Descriptor.Builder().fromArtifact(jar).build();
							if(descriptor == null) {
								descriptor = nextDescriptor;
							}
						} catch (NoSuchFileException | NotALayer nsfe) {
							// No descriptor
						}
					}
				}
				
				if(descriptor == null) {
					LOG.warning("No loadable layers in extenssion directory {0}", dir);
				}
				else {					
					maybeLoadLayer(dir, descriptor);
				}
			}
		}
	}

	private void maybeLoadLayer(Path dir, Descriptor descriptor) {
		var layerSection = descriptor.component();
		var id = layerSection.get("id");

		var wantedParents = new HashSet<>(Arrays.asList(layerSection.getAllOr("parents").orElse(new String[0])));
		wantedParents.add(id());

		if (!allowParents.isEmpty()) {
			wantedParents.forEach(p -> {
				if (!allowParents.contains(p))
					throw new IllegalStateException(MessageFormat.format(
							"Plugin {0} wanted the parent {1}, but the dynamic layer {2} does not allow use of this parent. It allows {3}",
							id, p, id(), String.join(", ", allowParents)));
			});
		}

		if (!denyParents.isEmpty()) {
			wantedParents.forEach(p -> {
				if (denyParents.contains(p))
					throw new IllegalStateException(MessageFormat.format(
							"Plugin {0} wanted the parent {1}, but the dynamic layer {2} does not allow use of this parent. It denies {3}",
							id, p, id(), String.join(", ", denyParents)));
			});
		}

		if (!extensions.containsKey(id)) {

			var layer = new PluginLayerImpl.Builder(id).
					fromDescriptor(descriptor).
					withParents(id()).
					withJarArtifactsDirectory(dir).
					build();

			var appLayer = (RootLayerImpl) rootLayer()
					.orElseThrow(() -> new IllegalStateException("Dynamic layer not attached to app layer."));
			
				
			try {
				extensions.put(id, layer);
				layer.rootLayer(appLayer);
				appLayer.addLayer(id, layer);
				appLayer.open(layer);
				appLayer.afterOpen(layer);
			}
			catch(Exception e) {
				try {
					appLayer.close(layer);
				}
				catch(Exception ex) {
				}
				finally {
					try {
						layer.rootLayer(null);
					}
					finally {
						appLayer.removeLayer(id);
						extensions.remove(id);
					}
				}
				throw e;
			}
		}
	}

	private static Path defaultPluginsRoot() {
		var prop = System.getProperty("bootlace.extensions");
		if (prop != null) {
			Paths.get(prop);
		}
		/*
		 * Is there a pom.xml in the current directory. If so, we are in a development
		 * environment, so use a 'tmp' directory
		 */
		if (BootContext.isDeveloper()) {
			return Paths.get(System.getProperty("bootlace.dev.extensions", "extensions"));
		}
		/*
		 * Is this a system installed app? If there is 'plugins' directory, and we can
		 * write to it, that's our local plugins. If not, then use a hidden directory in
		 * the users home
		 */
		var plugins = Paths.get("extensions");
		if (Files.exists(plugins) && Files.isWritable(plugins)) {
			return plugins;
		} else {
			return Paths.get(System.getProperty("user.home")).resolve(".bootlace").resolve("extensions");
		}
	}

}
