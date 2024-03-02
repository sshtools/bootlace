package com.sshtools.bootlace.platform;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import com.sshtools.bootlace.api.Layer;
import com.sshtools.bootlace.api.Logs;
import com.sshtools.bootlace.api.Logs.BootLog;
import com.sshtools.bootlace.api.Logs.Log;
import com.sshtools.bootlace.api.ResolutionMonitor;
import com.sshtools.jini.INI;

class AbstractLayer implements Layer {

	private final static Log LOG = Logs.of(BootLog.LAYERS);

	abstract static class AbstractLayerBuilder<L extends AbstractLayerBuilder<L>> {

		Set<String> appRepositories = new LinkedHashSet<>(); 
		Set<String> remoteRepositories = new LinkedHashSet<>();
		Optional<ResolutionMonitor> monitor = Optional.empty();
		Set<String> localRepositories = new LinkedHashSet<>();

		protected String id;
		protected Optional<String> name = Optional.empty();
		protected boolean global;

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

		public L fromDescriptor(Descriptor descriptor) {
			return (L) fromComponentSection(descriptor.componentSection());
		}

		public final L fromINI(Path path) {
			try {
				if(LOG.debug())
					LOG.debug("Constructing layer from file ''{0}''", path);
				
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
				LOG.debug("Constructing layer from resource ''{0}''", resource);
			
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

		@SuppressWarnings("unchecked")
		public final L withGlobal(boolean global) {
			this.global = global;
			return (L) this;
		}

		public final L withGlobal() {
			return withGlobal(true);
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

		@SuppressWarnings("unchecked")
		protected L fromComponentSection(INI.Section section) {
			addLocalRepositories(section.getAllOr("localRepository").orElse(new String[0]));
			addLocalRepositories(section.getAllOr("localRepositories").orElse(new String[0]));
			addAppRepositories(section.getAllOr("appRepository").orElse(new String[0]));
			addAppRepositories(section.getAllOr("appRepositories").orElse(new String[0]));
			addRemoteRepositories(section.getAllOr("remoteRepository").orElse(new String[0]));
			addRemoteRepositories(section.getAllOr("remoteRepositories").orElse(new String[0]));
			withName(section.getOr("name"));
			withGlobal(section.getBooleanOr("global",false));
			return (L) this;
		}

		protected final L fromINI(INI ini) {
			return fromDescriptor(new Descriptor.Builder().fromINI(ini).build());
		}

		@Override
		public String toString() {
			return "AbstractLayerBuilder [id=" + id + ", name=" + name + ", global=" + global + ", appRepositories="
					+ appRepositories + ", remoteRepositories=" + remoteRepositories + ", monitor=" + monitor
					+ ", localRepositories=" + localRepositories + "]";
		}
	}

	protected final Set<String> appRepositories;
	protected final Set<String> remoteRepositories;
	protected final Set<String> localRepositories;
	
	private final Optional<ResolutionMonitor> monitor;
	private final String id;
	private final Optional<String> name;
	
	boolean global;

	protected AbstractLayer(AbstractLayerBuilder<?> builder) {
		this.id = builder.id;
		this.name = builder.name;
		this.appRepositories = new LinkedHashSet<>(builder.appRepositories); 
		this.remoteRepositories = new LinkedHashSet<>(builder.remoteRepositories);
		this.localRepositories = new LinkedHashSet<>(builder.localRepositories);
		this.monitor = builder.monitor;
		this.global = builder.global;
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
	public final boolean global() {
		return global;
	}

	@Override
	public final Set<String> remoteRepositories() {
		return remoteRepositories;
	}

	@Override
	public String toString() {
		return "AbstractLayer [id=" + id + ", name=" + name + ", global=" + global + ", appRepositories="
				+ appRepositories + ", localRepositories=" + localRepositories + ", monitor=" + monitor
				+ ", remoteRepositories=" + remoteRepositories + "]";
	}
}