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
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.sshtools.bootlace.api.AppRepository;
import com.sshtools.bootlace.api.Layer;
import com.sshtools.bootlace.api.LocalRepository;
import com.sshtools.bootlace.api.Logs;
import com.sshtools.bootlace.api.Logs.BootLog;
import com.sshtools.bootlace.api.Logs.Log;
import com.sshtools.bootlace.platform.jini.INI;
import com.sshtools.bootlace.platform.jini.INI.Section;
import com.sshtools.bootlace.api.RemoteRepository;
import com.sshtools.bootlace.api.Repository;
import com.sshtools.bootlace.api.ResolutionMonitor;
import com.sshtools.bootlace.api.RootLayer;

abstract class AbstractLayer implements Layer {

	private final static Log LOG = Logs.of(BootLog.LAYERS);

	abstract static class AbstractLayerBuilder<L extends AbstractLayerBuilder<L>> {

		Set<String> appRepositories = new LinkedHashSet<>(); 
		Set<String> remoteRepositories = new LinkedHashSet<>();
		Set<RepositoryDef> repositoryDefs = new LinkedHashSet<>();
		Optional<ResolutionMonitor> monitor = Optional.empty();
		Set<String> localRepositories = new LinkedHashSet<>();

		protected String id;
		protected Optional<String> name = Optional.empty();

		protected AbstractLayerBuilder(String id) {
			if(id == null || id.length() == 0)
				throw new IllegalArgumentException("Empty layer ID");
			this.id = id;
		}

		public final L addAppRepositories(String... repositories) {
			return addAppRepositories(Arrays.asList(repositories));
		}

		@SuppressWarnings("unchecked")
		public final L addAppRepositories(Collection<String> repositories) {
			this.appRepositories.addAll(repositories);
			return (L) this;
		}

		@SuppressWarnings("unchecked")
		public final L addLocalRepositories(Collection<String> repositories) {
			this.localRepositories.addAll(repositories);
			return (L) this;
		}

		public final L addLocalRepositories(String... repositories) {
			return addLocalRepositories(Arrays.asList(repositories));
		}

		@SuppressWarnings("unchecked")
		public final L addRemoteRepositories(Collection<String> repositories) {
			this.remoteRepositories.addAll(repositories);
			return (L) this;
		}

		public final L addRemoteRepositories(String... repositories) {
			return addRemoteRepositories(Arrays.asList(repositories));
		}

		@SuppressWarnings("unchecked")
		public final L addRepositoryDefs(Collection<RepositoryDef> repositoryDefs) {
			this.repositoryDefs.addAll(repositoryDefs);
			return (L) this;
		}

		public final L addRepositoryDefs(RepositoryDef... repositoryDefs) {
			return addRepositoryDefs(Arrays.asList(repositoryDefs));
		}

		@SuppressWarnings("unchecked")
		public L fromDescriptor(Descriptor descriptor) {
			return (L) fromComponentSection(descriptor.component()).
					   fromRepositoryDefsSection(RemoteRepository.class, descriptor.repositories().map(r -> r.obtainSection("remote"))).
					   fromRepositoryDefsSection(AppRepository.class, descriptor.repositories().map(r -> r.obtainSection("local"))).
					   fromRepositoryDefsSection(LocalRepository.class, descriptor.repositories().map(r -> r.obtainSection("app")));
		}

		public final L fromINI(Path path) {
			try {
				if(LOG.debug())
					LOG.debug("Constructing layer from file `{0}`", path);
				
				return fromINI(Bootlace.createINIReader().build().read(path));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			} catch (ParseException e) {
				throw new IllegalArgumentException("Failed to parse file.", e);
			}
		}
		
		public final L fromINI(String filePath) {
			return fromINI(Paths.get(filePath));
		}

		public final L fromINIResource() {
			return fromINIResource(Descriptor.DESCRIPTOR_RESOURCE_NAME);
		}

		public final L fromINIResource(ClassLoader loader) {
			return fromINIResource(Descriptor.DESCRIPTOR_RESOURCE_NAME, loader);
		}

		public final L fromINIResource(String resource) {
			return fromINIResource(resource, getClass().getClassLoader());
		}

		public final L fromINIResource(String resource, ClassLoader loader) {
			if(LOG.debug())
				LOG.debug("Constructing layer from resource `{0}`", resource);
			
			var in = loader.getResourceAsStream(resource);
			if (in == null)
				throw new UncheckedIOException(new NoSuchFileException(resource));
			try (var rdr = new InputStreamReader(in)) {
				try {
					return fromINI(Bootlace.createINIReader().build().read(rdr));
				} catch (ParseException e) {
					throw new IllegalArgumentException("Failed to parse resource.", e);
				}
			} catch (IOException ioe) {
				throw new UncheckedIOException(ioe);
			}
		}

		public final L withAppRepositories(Collection<String> repositories) {
			this.appRepositories.clear();
			return addAppRepositories(repositories);
		}

		public final L withAppRepositories(String... repositories) {
			return withAppRepositories(Arrays.asList(repositories));
		}

		public final L withLocalRepositories(Collection<String> repositories) {
			return addLocalRepositories(repositories);
		}

		public final L withLocalRepositories(String... repositories) {
			return withLocalRepositories(Arrays.asList(repositories));
		}

		public final L withRepositoryDefs(Collection<RepositoryDef> remoteRepositoryDefs) {
			return addRepositoryDefs(remoteRepositoryDefs);
		}

		public final L withRepositoryDefs(RepositoryDef... repositories) {
			return withRepositoryDefs(Arrays.asList(repositories));
		}
		
		@SuppressWarnings("unchecked")
		public final L withMonitor(ResolutionMonitor monitor) {
			this.monitor = Optional.of(monitor);
			return (L) this;
		}

		@SuppressWarnings("unchecked")
		public final L withName(Optional<String> name) {
			this.name = name;
			return (L) this;
		}

		public final L withName(String name) {
			return withName(Optional.of(name));
		}

		public final L withoutMonitor() {
			return withMonitor(ResolutionMonitor.empty());
		}
		
		public final L withRemoteRepositories(Collection<String> repositories) {
			return addRemoteRepositories(repositories);
		}

		public final L withRemoteRepositories(String... repositories) {
			return withRemoteRepositories(Arrays.asList(repositories));
		}

		@SuppressWarnings({ "unchecked", "hiding" })
		protected <L extends AbstractLayerBuilder<L>> L fromRepositoryDefsSection(Class<? extends Repository> type, Optional<Section> repos) {
			repos.ifPresent(r -> {
				for(var sec : r.allSections()) {
					addRepositoryDefs(toRemoteRepositoryDef(type, sec));
				}
			});
			return (L)this;
		}

		@SuppressWarnings("unchecked")
		protected L fromComponentSection(INI.Section section) {
			addLocalRepositories(section.getAllOr("local-repository").orElse(new String[0]));
			addLocalRepositories(section.getAllOr("local-repositories").orElse(new String[0]));
			addAppRepositories(section.getAllOr("app-repository").orElse(new String[0]));
			addAppRepositories(section.getAllOr("app-repositories").orElse(new String[0]));
			addRemoteRepositories(section.getAllOr("remote-repository").orElse(new String[0]));
			addRemoteRepositories(section.getAllOr("remote-repositories").orElse(new String[0]));
			withName(section.getOr("name"));
			return (L) this;
		}

		protected RepositoryDef toRemoteRepositoryDef(Class<? extends Repository> type, Section sec) {
			return new RepositoryDef(
					type,
					sec.key(), 
					sec.getOr("name").orElseGet(() -> sec.key()), 
					sec.getOr("root").map(URI::create).orElseThrow(()-> new IllegalArgumentException("No 'root' in repository def section.")),
					sec.getBooleanOr("releases"), 
					sec.getBooleanOr("snapshots"),
					sec.getOr("pattern")
			);
		}

		protected final L fromINI(INI ini) {
			return fromDescriptor(new Descriptor.Builder().fromINI(ini).build());
		}

		@Override
		public String toString() {
			return "AbstractLayerBuilder [id=" + id + ", name=" + name + ", appRepositories="
					+ appRepositories + ", remoteRepositories=" + remoteRepositories + ", monitor=" + monitor
					+ ", localRepositories=" + localRepositories + "]";
		}
	}

	protected final Set<String> appRepositories;
	protected final Set<String> remoteRepositories;
	protected final Set<String> localRepositories;
	protected final Map<String, RepositoryDef> repositoryDefs;
	
	private final Optional<ResolutionMonitor> monitor;
	private final String id;
	private final Optional<String> name;
	

	protected AbstractLayer(AbstractLayerBuilder<?> builder) {
		this.id = builder.id;
		this.name = builder.name;
		this.appRepositories = new LinkedHashSet<>(builder.appRepositories); 
		this.remoteRepositories = new LinkedHashSet<>(builder.remoteRepositories);
		this.localRepositories = new LinkedHashSet<>(builder.localRepositories);
		this.repositoryDefs = new HashMap<>();
		builder.repositoryDefs.stream().forEach(v -> repositoryDefs.put(v.id(), v));
		this.monitor = builder.monitor;
		
		if(LOG.debug()) {
			if(!repositoryDefs.isEmpty()) {
				LOG.info("Layer `{0}` has {1} repository defs", id, repositoryDefs.size());
				repositoryDefs.forEach((k,v) ->
					LOG.info("   {0} = {1}", k, v.root()) 
				);
			}
		}
	}

	@Override
	public final Set<String> appRepositories() {
		return appRepositories;
	}

	@Override
	public final String id() {
		return id;
	}

	@Override
	public final Set<String> localRepositories() {
		return localRepositories;
	}

	@Override
	public final Optional<ResolutionMonitor> monitor() {
		return monitor;
	}

	@Override
	public final Optional<String> name() {
		return name;
	}

	@Override
	public final Set<String> remoteRepositories() {
		return remoteRepositories;
	}

	@Override
	public String toString() {
			return "AbstractLayer [id=" + id + ", name=" + name + ", appRepositories="
				+ appRepositories + ", localRepositories=" + localRepositories + ", monitor=" + monitor
				+ ", remoteRepositories=" + remoteRepositories + "]";
	}

	protected abstract void onAfterOpen();

	protected abstract void onOpened();
	
	protected abstract Set<String> parents();
	
	public abstract Optional<RootLayer> rootLayer();
	
	protected RepositoryDef findRepositoryDef(String id) {
		var def = repositoryDefs.get(id);
		if(LOG.trace()) {
			LOG.debug("Looking for repository `{0}` in layer `{1}`", id,  id());
		}
		if(def == null) {
			if(rootLayer().isPresent()) {
				var app = ((RootLayerImpl)rootLayer().get());
				if(!app.equals(this)) {
					var parents = parents();
					
					if(LOG.trace()) {
						LOG.debug("`{0}` does not exist in layer `{1}`, trying {2} parents", id, id(), parents.size());
					}
					
					for(var parent : parents) {
						var layer = app.getLayerOr(parent).orElse(null);
						if(layer != null) {
							if(layer instanceof AbstractChildLayer acl) {
								def = acl.findRepositoryDef(id);
								break;
							}
						}
					}
					
					if(def == null) {
						if(LOG.trace()) {
							LOG.debug("`{0}` does not exist in layer `{1}` or any parents, trying app layer itself", id, id());
						}	 
						
						def = app.findRepositoryDef(id);
					}
				}
			}
			else {
				if(LOG.trace()) {
					LOG.debug("`{0}` does not exist in layer `{1}`, no app layer to try", id, id());
				}
			}
		}
		return def;
	}
}