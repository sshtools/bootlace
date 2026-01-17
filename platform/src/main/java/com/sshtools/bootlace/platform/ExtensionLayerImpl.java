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

import static com.sshtools.bootlace.api.FilesAndFolders.recursiveDelete;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.sshtools.bootlace.api.BootContext;
import com.sshtools.bootlace.api.ChildLayer;
import com.sshtools.bootlace.api.DependencyGraph;
import com.sshtools.bootlace.api.DependencyGraph.Dependency;
import com.sshtools.bootlace.api.Exceptions.NotALayer;
import com.sshtools.bootlace.api.ExtensionLayer;
import com.sshtools.bootlace.api.Layer;
import com.sshtools.bootlace.api.LayerType;
import com.sshtools.bootlace.api.Logs;
import com.sshtools.bootlace.api.Logs.BootLog;
import com.sshtools.bootlace.api.Logs.Log;
import com.sshtools.bootlace.api.NodeModel;
import com.sshtools.bootlace.api.Zip;
import com.sshtools.bootlace.platform.jini.INI;

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
	private PluginLayerImpl groupLayer;

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

	private void refresh() {
		try {
			checkForArchives();
			checkForLoadableLayers();
			checkForDeletedLayers();
		} catch (RuntimeException e) {
			LOG.error("Failed to refresh dynamic layers.", e);
		} catch (IOException e) {
//			LOG.warning("Failed to refresh dynamic layers.", e);
			throw new UncheckedIOException(e);
		}
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

	private void closeLayer(ChildLayer layer) {
		if(layer.equals(this.groupLayer)) {
			LOG.info("App layer being removed");
			this.groupLayer = null;
		}
		
		for(var child : layer.childLayers()) {
			LOG.info("Child of {0} is {1}", layer.id(), child.id());
			if(child.type() == LayerType.GROUP) {
				// XXX TODO instead we must remove the parent module from its module config .. HOW?
			}
			else {
				closeLayer(child);
			}
		}
		
		var rootLayer = (RootLayerImpl) rootLayer()
				.orElseThrow(() -> new IllegalStateException("Dynamic layer not attached to root layer."));
		try {
			rootLayer.beforeClose(layer);
		}
		finally {
			try {
				rootLayer.close(layer);
			}
			finally {
				rootLayer.removeLayer(layer.id());
				extensions.remove(layer.id());
				((AbstractChildLayer)layer).rootLayer(null);
			}
		}
	}
	
	private final static class DescriptorDir implements NodeModel<DescriptorDir>{
		
		private final Path dir;
		private final Descriptor descriptor;
		private final List<DescriptorDir> all;

		public DescriptorDir(Path dir, Descriptor descriptor, List<DescriptorDir> all) {
			super();
			this.dir = dir;
			this.descriptor = descriptor;
			this.all = all;
		}

		@Override
		public int hashCode() {
			return Objects.hash(descriptor.id());
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			DescriptorDir other = (DescriptorDir) obj;
			return Objects.equals(descriptor.id(), other.descriptor.id());
		}

		@Override
		public String name() {
			return descriptor.id();
		}

		@Override
		public void dependencies(Consumer<Dependency<DescriptorDir>> model) {
			var type  = descriptor.type();
			if(type == LayerType.GROUP) {
				all.forEach(dd -> {
					if(!dd.descriptor.id().equals(descriptor.id())) {
						model.accept(new Dependency<DescriptorDir>(dd, this));	
					}
				});
			}
			Arrays.asList(descriptor.component().getAllElse("parent", new String[0])).forEach(par -> {
				find(par).ifPresent(dd  -> {
					model.accept(new Dependency<DescriptorDir>(dd, this));
				});
			});;
		}
		
		Optional<DescriptorDir> find(String id) {
			return  all.stream().filter(d -> d.descriptor.id().equals(id)).findFirst();
		}
		
	} 

	private void checkForLoadableLayers() throws IOException {
		
		var l = new ArrayList<DescriptorDir>();
		try (var stream = Files.newDirectoryStream(directory,
				(f) -> Files.isDirectory(f) && 
				!f.getFileName().toString().endsWith(".backup") && 
				!f.getFileName().toString().endsWith(".tmp"))) {
			
			for (var dir : stream) {
				Descriptor descriptor = null;
				try (var dirStream = Files.newDirectoryStream(dir)) {
					for (var art : dirStream) {
						
						if(LOG.debug())
							LOG.debug("Checking dir {0} for descriptor for {1}", art, dir.getFileName().toString());
						
						try {
							var nextDescriptor = new Descriptor.Builder().
									fromArtifact(art).
									build();
							if(descriptor == null && nextDescriptor.id().equals(dir.getFileName().toString())) {
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
					l.add(new DescriptorDir(dir, descriptor, l));
				}
			}
			
		}

		List<DescriptorDir> topologicallySorted = new ArrayList<>(new DependencyGraph<>(l).getTopologicallySorted());
		l.forEach(a -> { 
			if(!topologicallySorted.contains(a)) {
				topologicallySorted.add(a);
			}
		});

		topologicallySorted.forEach(lyr -> maybeLoadLayer(lyr.dir, lyr.descriptor));
	}

	private void maybeLoadLayer(Path dir, Descriptor descriptor) {
		var layerSection = descriptor.component();
		var id = descriptor.id();

		var wantedParents = new HashSet<>(Arrays.asList(layerSection.getAllOr("parent").orElse(new String[0])));
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
		
		var type = descriptor.type();

		if (!extensions.containsKey(id)) {
			
			Collection<String> parents;
			
			if(type == LayerType.GROUP) {
				if(groupLayer != null) {
					throw new IllegalStateException(MessageFormat.format(
							"There may only be one layer of type `GROUP` in a parent  layer. {0} cannot be added.",
							id));
				}
				parents = extensions.keySet();
			}
			else if(type == LayerType.STATIC || type == LayerType.BOOT) {
				parents = Stream.concat(Stream.of(id()), wantedParents.stream()).distinct().toList();
			}
			else {
				throw new IllegalStateException(MessageFormat.format(
						"Layer {0} type {1} not supported in extension layer",
						id, type));
			}

			var layer = new PluginLayerImpl.Builder(id).
					fromDescriptor(descriptor).
					withParents(parents).
					withArtifactsDirectory(dir).
					build();

			var rootLayer = (RootLayerImpl) rootLayer()
					.orElseThrow(() -> new IllegalStateException("Dynamic layer not attached to app layer."));
			
				
			try {

				if(type.equals(LayerType.GROUP)) {
					groupLayer = layer;
				}
				else if(groupLayer != null) {
					/* TODO this layer as parent to groupLayer layer */
					groupLayer.addParent(layer);
				}
				
				extensions.put(id, layer);
				layer.rootLayer(rootLayer);
				rootLayer.addLayer(id, layer);
				rootLayer.open(layer);
				rootLayer.afterOpen(layer);
			}
			catch(Exception e) {
				if(groupLayer != null && type.equals(LayerType.GROUP)) {
					groupLayer = null;
				}
				
				try {
					rootLayer.close(layer);
				}
				catch(Exception ex) {
				}
				finally {
					try {
						layer.rootLayer(null);
					}
					finally {
						rootLayer.removeLayer(id);
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
