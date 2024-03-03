package com.sshtools.bootlace.platform;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import com.sshtools.bootlace.api.AppRepository;
import com.sshtools.bootlace.api.AppRepository.AppRepositoryBuilder;
import com.sshtools.bootlace.api.ChildLayer;
import com.sshtools.bootlace.api.GAV;
import com.sshtools.bootlace.api.Layer;
import com.sshtools.bootlace.api.LocalRepository;
import com.sshtools.bootlace.api.LocalRepository.LocalRepositoryBuilder;
import com.sshtools.bootlace.api.Logs;
import com.sshtools.bootlace.api.Logs.BootLog;
import com.sshtools.bootlace.api.Logs.Log;
import com.sshtools.bootlace.api.RemoteRepository;
import com.sshtools.bootlace.api.RemoteRepository.RemoteRepositoryBuilder;
import com.sshtools.bootlace.api.Repository;
import com.sshtools.bootlace.api.Repository.RepositoryBuilder;
import com.sshtools.bootlace.api.ResolutionMonitor;
import com.sshtools.bootlace.api.RootLayer;
import com.sshtools.jini.INI.Section;

public abstract class AbstractChildLayer extends AbstractLayer implements ChildLayer {
	private final static Log LOG = Logs.of(BootLog.LAYERS);

	static abstract class AbstractChildLayerBuilder<B extends AbstractChildLayer.AbstractChildLayerBuilder<B>>
			extends AbstractLayerBuilder<B> {
		protected final Set<String> parents = new LinkedHashSet<>();

		public AbstractChildLayerBuilder(String id) {
			super(id);
		}

		@Override
		protected B fromComponentSection(Section section) {
			withParents(section.getAllOr("parent").orElse(new String[0]));
			withParents(section.getAllOr("parents").orElse(new String[0]));
			return super.fromComponentSection(section);
		}

		public B withParents(String... parents) {
			return withParents(Arrays.asList(parents));
		}

		@SuppressWarnings("unchecked")
		public B withParents(Collection<String> parents) {
			this.parents.addAll(parents);
			return (B) this;
		}

	}

	final Set<String> parents;

	private Optional<RootLayer> appLayer = Optional.empty();

	AbstractChildLayer(AbstractChildLayer.AbstractChildLayerBuilder<?> layerBuilder) {
		super(layerBuilder);
		parents = new LinkedHashSet<>(layerBuilder.parents);
	}

	@Override
	public final Optional<RootLayer> appLayer() {
		return appLayer;
	}

	final void appLayer(RootLayerImpl appLayer) {
		this.appLayer = Optional.of(appLayer);
	}

	@Override
	public final Set<String> parents() {
		return parents;
	}

	@Override
	public void onOpened() {
	}

	@Override
	public void onAfterOpen() {
	}

	@Override
	public String toString() {
		return "AbstractChildLayer [id()=" + id() + ", name()=" + name() + ", appRepositories()=" + appRepositories()
				+ ", localRepositories()=" + localRepositories() + ", monitor()=" + monitor() + ", global()=" + global()
				+ ", remoteRepositories()=" + remoteRepositories() + ", parents=" + parents + ", appLayer=" + appLayer
				+ "]";
	}

	@SuppressWarnings("unchecked")
	<REPO extends Repository, BLDR extends Repository.RepositoryBuilder<BLDR, REPO>> Set<REPO> resolveRepositoriesForLayer(
			Function<AbstractChildLayer, Set<String>> supplier, Class<REPO> repoClass, Class<BLDR> bldrClass) {

		if (LOG.debug()) {
			LOG.debug("Resolving repositories of type {0} for layer {1}", repoClass.getName(), id());
		}
		
		var l = new LinkedHashSet<REPO>();
		var thisModuleLayer = getClass().getModule().getLayer();
		
		if (parents.isEmpty()) {
			for (var repoId : supplier.apply(this)) {
				Repositories.forIdOr(thisModuleLayer, repoId, repoClass, bldrClass)
						.ifPresent(repo -> l.add(repo));
			}
		
			addRemotesForLayer(supplier, repoClass, l, thisModuleLayer, this);
				
		} else {
			appLayer.ifPresent(app -> {
					var rootLayer = (RootLayerImpl) app;
				for (var parent : parents) {
					var moduleLayer = rootLayer.moduleLayers.get(parent);
					if (moduleLayer == null)
						throw new IllegalStateException(
								MessageFormat.format("Parent layer ''{0}'' does not exist.", parent));

					if (LOG.debug()) {
						LOG.debug("Now resolving repository for {0} using parent {1} module loader", id(),
								parent);
					}

					for (var repoId : supplier.apply(this)) {
						Repositories.forIdOr(moduleLayer, repoId, repoClass, bldrClass).ifPresent(repo -> l.add(repo));
					}
					
					var layer = rootLayer.layers.get(parent);
					if(layer != null) 
						addRemotesForLayer(supplier, repoClass, l, moduleLayer, ((AbstractChildLayer)layer));
				}

				addRemotesForLayer(supplier, repoClass, l, rootLayer.getClass().getModule().getLayer(), rootLayer);
			});
		}
		
		if(l.isEmpty()) {
			if(LOG.debug()) {
				LOG.debug("Found no repositories of type {0} for layer {1} on the classpath", repoClass.getName(), id());
			}
		}
		return l;
	}

	protected <REPO extends Repository> void addRemotesForLayer(Function<AbstractChildLayer, Set<String>> supplier,
			Class<REPO> repoClass, LinkedHashSet<REPO> l, ModuleLayer thisLayer, AbstractLayer layer) {
		if(repoClass.equals(RemoteRepository.class)) {
			for (var repoId : supplier.apply(this)) {
				var def = layer.remoteRepositoryDefs.get(repoId);
				if(def != null) {
					Repositories.builderForId(thisLayer, "central", RemoteRepositoryBuilder.class).ifPresent(bldr -> {
						bldr.withId(def.id());
						bldr.withName(def.name());
						bldr.withRoot(def.root());
						bldr.withReleases(def.releases().orElse(def.snapshots().isEmpty()));
						bldr.withSnapshots(def.releases().orElse(def.releases().isEmpty()));
						
						l.add((REPO) bldr.build());
					});
				}
			}
		}
	}

	<REPO extends Repository, BLDR extends Repository.RepositoryBuilder<BLDR, REPO>> Set<REPO> resolveRepositories(
			Set<REPO> l, Function<AbstractChildLayer, Set<String>> supplier, Class<REPO> repoClass,
			Class<BLDR> bldrClass) {
		l.addAll(resolveRepositoriesForLayer(supplier, repoClass, bldrClass));
		appLayer.ifPresent(app -> {
			for (var parent : parents) {
				var moduleLayer = (AbstractChildLayer) ((RootLayerImpl) app).layers.get(parent);
				if (moduleLayer == null)
					throw new IllegalStateException(
							MessageFormat.format("Parent layer ''{0}'' does not exist.", parent));
				moduleLayer.resolveRepositories(l, supplier, repoClass, bldrClass);
			}
		});
		return l;
	}
	
	<BLDR extends RepositoryBuilder<BLDR, REPO>, REPO extends Repository> REPO forId(ModuleLayer layer, String id,
			Class<? extends REPO> repoClass, Class<? extends BLDR> builderClass) {
		return forIdOr(layer, id, repoClass, builderClass)
				.orElseThrow(() -> new IllegalStateException("No repository implementation with id of " + id));
	}

	@SuppressWarnings("unchecked")
	<BLDR extends RepositoryBuilder<BLDR, REPO>, REPO extends Repository> Optional<REPO> forIdOr(ModuleLayer layer, 
			String id, Class<? extends REPO> repoClass, Class<? extends BLDR> builderClass) {
		if(appLayer.isPresent() && id.equals("bootstrap") && repoClass.equals(LocalRepository.class)) {
			return Optional.of((REPO)(((RootLayerImpl)appLayer.get()).bootstrapRepository().orElse(Repositories.bootstrapRepository())));
		}
		else {
			return Repositories.forIdOr(layer, id, repoClass, builderClass);
		}
	}
	

	Set<LocalRepository> resolveLocalRepositories() {
		var l = new LinkedHashSet<LocalRepository>();
		resolveRepositories(l, (a) -> a.localRepositories(), LocalRepository.class, LocalRepositoryBuilder.class);
		appLayer.ifPresent(app -> {
			l.addAll(app.localRepositories().stream().map(r -> forId(getClass().getModule().getLayer(), r,
					LocalRepository.class, LocalRepositoryBuilder.class)).toList());
		});
		return l;
	}

	Set<RemoteRepository> resolveRemoteRepositories() {
		var l = new LinkedHashSet<RemoteRepository>();
		resolveRepositories(l, (a) -> a.remoteRepositories(), RemoteRepository.class, RemoteRepositoryBuilder.class);
		appLayer.ifPresent(app -> {
			l.addAll(app.remoteRepositories().stream().map(r -> forId(getClass().getModule().getLayer(), r,
					RemoteRepository.class, RemoteRepositoryBuilder.class)).toList());
		});
		return l;
	}

	Set<AppRepository> resolveAppRepositories() {
		var l = new LinkedHashSet<AppRepository>();
		resolveRepositories(l, (a) -> a.appRepositories(), AppRepository.class, AppRepositoryBuilder.class);
		appLayer.ifPresent(app -> {
			l.addAll(app.appRepositories().stream().map(r -> forId(getClass().getModule().getLayer(), r,
					AppRepository.class, AppRepositoryBuilder.class)).toList());
		});
		return l;
	}
	
	Optional<ResolutionMonitor> resolveMonitor() {
		if(monitor().isPresent()) {
			return monitor();
		}
		else {
			if(appLayer.isPresent()) {
				var app = appLayer.get();
				for (var parent : parents) {
					var moduleLayer = (AbstractChildLayer) ((RootLayerImpl) app).layers.get(parent);
					if (moduleLayer == null)
						throw new IllegalStateException(
								MessageFormat.format("Parent layer ''{0}'' does not exist.", parent));
					var mon = moduleLayer.resolveMonitor();
					if(mon.isPresent())
						return mon;
				}
				if(app.monitor().isPresent())
					return app.monitor();
			}
			return Optional.empty();
		}
	}

	RemoteRepository resolveRemoteRepository(GAV gav) {
		var repos = resolveRemoteRepositories();
		for (var r : repos) {
			if (r.supported(gav)) {
				return r;
			}
		}
		throw new IllegalStateException("GAV " + gav + " is not supported by any repository.");
	}

}