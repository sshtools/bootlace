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
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
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
import java.util.function.Consumer;
import java.util.stream.Stream;

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
 * A Container layer that obtains its artifacts from a directory of jars
 * or classes.
 */
public abstract class AbstractStaticLayer extends AbstractChildLayer implements ExtensionLayer {
	private final static Log LOG = Logs.of(BootLog.LAYERS);

	public abstract static class AbstractStaticLayerBuilder<BLDR extends AbstractStaticLayerBuilder<BLDR>> extends AbstractChildLayerBuilder<BLDR> {
		private Optional<Path> directory = Optional.empty();
		private Optional<Path> userDirectory = Optional.empty();
		private Set<String> allowParents = new HashSet<>();
		private Set<String> denyParents = new HashSet<>();

		public AbstractStaticLayerBuilder(String id) {
			super(id);
		}

		@SuppressWarnings("unchecked")
		@Override
		protected BLDR fromComponentSection(INI.Section section) {
			super.fromComponentSection(section);
			withDirectory(section.getOr("directory").map(Paths::get));
			withUserDirectory(section.getOr("user-directory").map(Paths::get));
			withAllowParents(section.getAllOr("allowParent").orElse(new String[0]));
			withAllowParents(section.getAllOr("allowParents").orElse(new String[0]));
			withAllowParents(section.getAllOr("denyParent").orElse(new String[0]));
			withAllowParents(section.getAllOr("denyParents").orElse(new String[0]));
			return (BLDR)this;
		}

		public BLDR withAllowParents(String... allowParents) {
			return withAllowParents(Arrays.asList(allowParents));
		}

		@SuppressWarnings("unchecked")
		public BLDR withAllowParents(Collection<String> allowParents) {
			this.allowParents.addAll(allowParents);
			return (BLDR)this;
		}

		public BLDR withDenyParents(String... denyParents) {
			return withDenyParents(Arrays.asList(denyParents));
		}

		@SuppressWarnings("unchecked")
		public BLDR withDenyParents(Collection<String> denyParents) {
			this.denyParents.addAll(denyParents);
			return (BLDR)this;
		}

		public BLDR withDirectory(String directory) {
			return withDirectory(Paths.get(directory));
		}

		@SuppressWarnings("unchecked")
		public BLDR withDirectory(Optional<Path> directory) {
			this.directory = directory;
			return (BLDR)this;
		}

		public BLDR withDirectory(Path directory) {
			return withDirectory(Optional.of(directory));
		}

		public BLDR withUserDirectory(Path fallbackDirectory) {
			return withUserDirectory(Optional.of(fallbackDirectory));
		}

		@SuppressWarnings("unchecked")
		public BLDR withUserDirectory(Optional<Path> fallbackDirectory) {
			this.userDirectory = fallbackDirectory;
			return (BLDR) this;
		}
	}

	private final Set<String> allowParents;
	private final Set<String> denyParents;
	
	protected final Path writeDirectory;
	protected final Path readDirectory;

	protected final Map<String, AbstractChildLayer> extensions = new ConcurrentHashMap<>();

	protected AbstractStaticLayer(AbstractStaticLayerBuilder<?> builder) {
		super(builder);
		allowParents = Collections.unmodifiableSet(new HashSet<>(builder.allowParents));
		denyParents = Collections.unmodifiableSet(new HashSet<>(builder.denyParents));

		var homeBase = Paths.get(System.getProperty("user.home")).resolve(".bootlace");
		var actualDir = builder.directory.orElseGet(() -> {
			return homeBase.resolve(id());
		});
		
		if(isWritable(actualDir)) {
			writeDirectory = readDirectory = actualDir;
		}
		else {
			writeDirectory = builder.userDirectory.orElseGet(() -> homeBase.resolve(id()));
			readDirectory = actualDir;
		}
		
		try {
			Files.createDirectories(writeDirectory);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		
		LOG.info("Extension archives for {0} read from {1}", id(), readDirectory);
		LOG.info("Expanded Extension for {0} directories read from {1}", id(), writeDirectory);
		
//		try {
//			writeDirectory = resolveWriteDirectory(builder);
//		} catch (IOException e) {
//			throw new UncheckedIOException(e);
//		}
//		if(writeDirectory.equals(builder.userDirectory.orElse(null))) {
//			readDirectory = Optional.empty();
//		}
//		else {
//			readDirectory = builder.userDirectory;
//		}
//		if (builder.userDirectory.isPresent()) {
//			var fallback = builder.userDirectory.get();
//			if (!fallback.isAbsolute()) {
//				fallback = Paths.get(System.getProperty("user.home")).resolve(".bootlace").resolve(directory.getFileName());
//			}
//			LOG.info("No access to {0}, falling back to {1} for dynamic layers", directory, fallback);
//			Files.createDirectories(fallback);
//			return fallback;
//		} else
//			throw ade;
	}
	
	@Override
	public final Collection<? extends Layer> extensions() { 
		return extensions.values();
	}

	protected final boolean isWritable(Path directory) {
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
			return true;
		} catch (AccessDeniedException ade) {
			return false;
		} catch(IOException ioe) {
			throw new UncheckedIOException(ioe);
		}
	}

	@Override
	public final void onOpened() {
		try {
			refresh();
			onStaticLayerOpened();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	protected void onStaticLayerOpened() {
		
	}

	@Override
	public String toString() {
		return "AbstractStaticLayer [id()=" + id() + ", name()=" + name() + ", parents()=" + parents() + ", appRepositories()=" + appRepositories() + ", localRepositories()=" + localRepositories()
				+ ", remoteRepositories()=" + remoteRepositories() + ", monitor()=" + monitor() + ", " +
				"allowParents=" + allowParents + ", denyParents=" + denyParents + "]";
	}

	protected final void checkForArchives(Path readDirectory, Path writeDirectory) throws IOException {
		if(!Files.exists(readDirectory)) {
			LOG.info("{0} does not exist, skipping checking for extension archives (it may be populated later)", readDirectory);
			return;
		}
		
		var readIsWritable = isWritable(readDirectory);
		var writeIsWritable = isSingleDir() ? readIsWritable : isWritable(writeDirectory);
		
		if(!writeIsWritable) {
			LOG.info("{0} is not writable, skipping checking for extension archives.", writeDirectory);
			return;
		}
		
		try (var stream = Files.newDirectoryStream(readDirectory,
				p -> p.getFileName().toString().toLowerCase().endsWith(".zip"))) {
			for (var zip : stream) {
				try {
					var descriptor = new Descriptor.Builder().fromArtifact(zip).build();
					var layer = descriptor.component();
					var id = layer.get("id");
					var dir = writeDirectory.resolve(id);

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
						
						if(readIsWritable) {
							Files.delete(zip);
						}

					} catch (IOException ioe) {
						try {

							try {
								if(readIsWritable) {
									var failedFile = zip.getParent().resolve(zip.getFileName() + ".failed");
									if (Files.exists(failedFile)) {
										Files.delete(failedFile);
									}
									Files.move(zip, failedFile);
								}

								recursiveDelete(dir);
								if (Files.exists(backupDir)) {
									Files.move(backupDir, dir);
								}
							}
							catch(AccessDeniedException ade) {
								LOG.debug("Failed to move .zip file to .zip.failed, or delete existing expanded extension.", ade);
							}
							throw ioe;
						} catch (IOException ioe2) {
							throw new UncheckedIOException(ioe2);
						}
					} finally {
						if (Files.exists(backupDir)) {
							recursiveDelete(backupDir);
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

	protected final void refresh() throws IOException {
		if(!isSingleDir()) {
			checkForArchives(writeDirectory, writeDirectory);
		}
		checkForArchives(readDirectory, writeDirectory);
		checkForLoadableLayers(readDirectory);
		onRefresh();
	}

	protected boolean isSingleDir() {
		return readDirectory.toString().equals(writeDirectory.toString());
	}

	protected void onRefresh() throws IOException {
	}

	protected final void closeLayer(ChildLayer layer) {
		
		for(var child : ((AbstractLayer)layer).childLayers()) {
			LOG.info("Child of {0} is {1}", layer.id(), child.id());
			closeLayer(child);
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
	
	protected final static class DescriptorDir implements NodeModel<DescriptorDir>{
		
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

	protected final void checkForLoadableLayers(Path directory) throws IOException {
		
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
		
		LOG.debug("Final order ..");
		topologicallySorted.forEach(lyr -> LOG.debug("   {0}", lyr.name()));

		topologicallySorted.forEach(lyr -> maybeLoadLayer(lyr.dir, lyr.descriptor));
	}

	protected final void maybeLoadLayer(Path dir, Descriptor descriptor) {
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
			
			if(type == LayerType.DEFAULT || type == LayerType.STATIC || type == LayerType.BOOT || type == LayerType.GROUP) {
				parents = Stream.concat(Stream.of(id()), wantedParents.stream()).distinct().toList();
			}
			else {
				throw new IllegalStateException(MessageFormat.format(
						"Layer {0} type {1} not supported in extension layer",
						id, type));
			}

			var layer = new DefaultLayerImpl.Builder(id).
					fromDescriptor(descriptor).
					withParents(parents).
					withArtifactsDirectory(dir).
					build();

			var rootLayer = (RootLayerImpl) rootLayer()
					.orElseThrow(() -> new IllegalStateException("Dynamic layer not attached to app layer."));
			
				
			try {

				extensions.put(id, layer);
				layer.rootLayer(rootLayer);
				rootLayer.addLayer(id, layer);
				rootLayer.open(layer, writeDirectory);
				rootLayer.afterOpen(layer);
			}
			catch(Exception e) {
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

	protected abstract Path defaultDirectory();
}