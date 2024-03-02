package com.sshtools.bootlace.platform;

import java.io.IOException;
import java.io.UncheckedIOException;
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

import com.sshtools.bootlace.api.Logs;
import com.sshtools.bootlace.api.Logs.BootLog;
import com.sshtools.bootlace.api.Logs.Log;
import com.sshtools.bootlace.api.Zip;
import com.sshtools.jini.INI;

/**
 * A Container layer that watches a particular directory to load further layers from. 
 * <p>
 * When a  
 */
public final class DynamicLayer extends AbstractChildLayer {
	private final static Log LOG = Logs.of(BootLog.LAYERS);

	private final static class DefaultQueue {
		private final static ScheduledExecutorService DEFAULT = Executors.newScheduledThreadPool(1);
	}

	public final static class Builder extends AbstractChildLayerBuilder<Builder> {
		private Optional<Path> directory = Optional.empty();
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
			return withDirectory(directory);
		}

		public DynamicLayer build() {
			return new DynamicLayer(this);
		}
	}

	private final Path directory;
	private final Thread watchThread;
	private final Map<String, AbstractChildLayer> loadedLayers = new ConcurrentHashMap<>();
	private final Set<String> allowParents;
	private final Set<String> denyParents;

	private ScheduledFuture<?> changedTask;

	private DynamicLayer(Builder builder) {
		super(builder);
		allowParents = Collections.unmodifiableSet(new HashSet<>(builder.allowParents));
		denyParents = Collections.unmodifiableSet(new HashSet<>(builder.denyParents));
		directory = builder.directory.orElseGet(() -> defaultPluginsRoot());

		var queue = builder.queue.orElseGet(() -> DefaultQueue.DEFAULT);

		try {
			if (!Files.exists(directory)) {
				Files.createDirectories(directory);
			}

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
	public void onOpened() {
		refresh();
	}

	@Override
	public String toString() {
		return "DynamicLayer [id()=" + id() + ", name()=" + name() + ", parents()=" + parents() + ", global()="
				+ global() + ", appRepositories()=" + appRepositories() + ", localRepositories()=" + localRepositories()
				+ ", remoteRepositories()=" + remoteRepositories() + ", monitor()=" + monitor() + ", directory="
				+ directory + ", allowParents=" + allowParents + ", denyParents=" + denyParents + "]";
	}

	private void checkForArchives() throws IOException {
		try (var stream = Files.newDirectoryStream(directory,
				p -> p.getFileName().toString().toLowerCase().endsWith(".zip"))) {
			for (var zip : stream) {
				try {
					var descriptor = new Descriptor.Builder().fromArtifact(zip).build();
					var layer = descriptor.componentSection();
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
				}
				catch(NoSuchFileException nsfe) {
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
			LOG.debug("Failed to refresh dynamic layers.", e);
		} catch (IOException e) {
			LOG.debug("Failed to refresh dynamic layers.", e);
			throw new UncheckedIOException(e);
		}
	}

	private void checkForDeletedLayers() throws IOException {
		var removed = new HashSet<String>();
		loadedLayers.forEach((id, layer) -> {

			var dir = directory.resolve(id);
			if (!Files.exists(dir)) {
				removed.add(id);
				var appLayer = (RootLayerImpl)appLayer()
						.orElseThrow(() -> new IllegalStateException("Dynamic layer not attached to app layer."));
				appLayer.beforeClose(layer);
				appLayer.close(layer);
			}
		});
		for (var layer : removed) {
			loadedLayers.remove(layer);
		}
	}

	private void checkForLoadableLayers() throws IOException {
		try (var stream = Files.newDirectoryStream(directory,
				(f) -> Files.isDirectory(f) && !f.getFileName().toString().endsWith(".backup"))) {
			for (var dir : stream) {
				try (var dirStream = Files.newDirectoryStream(dir)) {
					for (var jar : dirStream) {
						try {
							maybeLoadLayer(dir, new Descriptor.Builder().fromArtifact(jar).build());
						}
						catch(NoSuchFileException nsfe) {
							// No descriptor
						}
					}
				}
			}
		} 
	}

	private void maybeLoadLayer(Path dir, Descriptor descriptor) {
		var layerSection = descriptor.componentSection();
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

		if (!loadedLayers.containsKey(id)) {
			
			var layer = new PluginLayerImpl.Builder(id).
					fromDescriptor(descriptor).
					withParents(id()).
					withJarArtifactsDirectory(dir).
					build();

			loadedLayers.put(id, layer);

			var appLayer = (RootLayerImpl)appLayer().orElseThrow(
					() -> new IllegalStateException("Dynamic layer not attached to app layer."));
			appLayer.open(layer);
			appLayer.afterOpen(layer);
		}
	}

	private static Path defaultPluginsRoot() {
		var prop = System.getProperty("bootlace.plugins");
		if (prop != null) {
			Paths.get(prop);
		}
		/*
		 * Is there a pom.xml in the current directory. If so, we are in a development
		 * environment, so use a 'tmp' directory
		 */
		var pom = Paths.get("pom.xml");
		if (Files.exists(pom)) {
			return Paths.get(System.getProperty("bootlace.dev.plugins", "plugins"));
		}
		/*
		 * Is this a system installed app? If there is 'plugins' directory, and we can
		 * write to it, that's our local plugins. If not, then use a hidden directory in
		 * the users home
		 */
		var plugins = Paths.get("plugins");
		if (Files.exists(plugins) && Files.isWritable(plugins)) {
			return plugins;
		} else {
			return Paths.get(System.getProperty("user.home")).resolve(".bootlace").resolve("plugins");
		}
	}

	public Path plugins() {
		return directory;
	}

}
