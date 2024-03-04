package com.sshtools.bootlace.platform;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Function;

import com.sshtools.bootlace.api.AppRepository;
import com.sshtools.bootlace.api.AppRepository.AppRepositoryBuilder;
import com.sshtools.bootlace.api.ChildLayer;
import com.sshtools.bootlace.api.GAV;
import com.sshtools.bootlace.api.LayerContext;
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

	Set<LocalRepository> resolveLocalRepositories() {
		return resolveRepositoriesForLayer( 
				(a) -> a.localRepositories(), LocalRepository.class, LocalRepositoryBuilder.class);
	}

	Set<RemoteRepository> resolveRemoteRepositories() {
		return resolveRepositoriesForLayer( 
				(a) -> a.remoteRepositories(), RemoteRepository.class, RemoteRepositoryBuilder.class);
	}

	Set<AppRepository> resolveAppRepositories() {
		return resolveRepositoriesForLayer( 
				(a) -> a.appRepositories(), AppRepository.class, AppRepositoryBuilder.class);
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

	private <REPO extends Repository, BLDR extends Repository.RepositoryBuilder<BLDR, REPO>> Set<REPO> resolveRepositoriesForLayer(
			Function<AbstractChildLayer, Set<String>> supplier, Class<REPO> repoClass, Class<BLDR> bldrClass) {

		if (LOG.debug()) {
			LOG.debug("Resolving repositories of type {0} for layer {1}", repoClass.getName(), id());
		}
		
		var l = new LinkedHashSet<REPO>();
		
		for (var repoId : supplier.apply(this)) {
			resolveRepository(repoId, repoClass, bldrClass)
					.ifPresent(repo -> l.add(repo));
		}
			
		appLayer.ifPresent(app -> {
			var rootLayer = (RootLayerImpl) app;
			for (var parent : parents) {
				var layer = rootLayer.layers.get(parent);
				if (layer == null)
					throw new IllegalStateException(
							MessageFormat.format("Parent layer ''{0}'' does not exist.", parent));

				if (LOG.debug()) {
					LOG.debug("Now resolving repository for {0} using parent {1} module loader", id(),
							parent);
				}
				l.addAll(((AbstractChildLayer)layer).resolveRepositoriesForLayer(supplier, repoClass, bldrClass));
			}
		});
		
		if(l.isEmpty()) {
			if(LOG.debug()) {
				LOG.debug("Found no repositories of type {0} for layer {1} on the classpath", repoClass.getName(), id());
			}
		}
		return l;
	}

 	@SuppressWarnings("unchecked")
	private <BLDR extends RepositoryBuilder<BLDR, REPO>, REPO extends Repository> Optional<REPO> resolveRepository( 
			String id, Class<? extends REPO> repoClass, Class<? extends BLDR> builderClass) {
 		
 		if (LOG.debug()) {
			LOG.debug("Looking for repository definition ''{0}'', type ''{1}'' for layer ''{2}''", id, repoClass.getName(), id());
		}
 		
 		var def = findRepositoryDef(id);
 		if(def == null) {
 			if (LOG.debug()) {
				LOG.debug("No custom repository configuration, using defaults");
			}
 			
 			if(id.equals(BootstrapRepository.ID)) {
 				return (Optional<REPO>) ((RootLayerImpl)appLayer.get()).bootstrapRepository();
 			}
 			else if(id.equals(LocalRepository.ID)) {
 				return (Optional<REPO>) Optional.of(LocalRepositoryImpl.localRepository());
 			}
 			else if(id.equals(AppRepository.ID)) {
 				return (Optional<REPO>) Optional.of(AppRepositoryImpl.appRepository());
 			}
 			else {
 				throw new IllegalArgumentException(MessageFormat.format("No repository def for ''{0}''.", id));
 			}
 		}
 		if(!def.type().equals(repoClass)) {
 			throw new IllegalArgumentException(MessageFormat.format("Unexpected repository type for ''{0}''. Expected ''{1}'', got ''{2}''.", id, repoClass.getName(), def.type().getName()));
 		}
 		
		var thisModuleLayer = getClass().getModule().getLayer();
		var bldrOr = ServiceLoader.load(thisModuleLayer, builderClass).findFirst();
		if(bldrOr.isEmpty()) {
			LOG.debug("NO repository builder of type ''{0}'' of class ''{1}'' in child layers of ''{2}'', trying service layers", id, builderClass.getName(), thisModuleLayer);
		}
		else {
			
			var bldr = bldrOr.get();
			
 			if (LOG.debug()) {
				LOG.debug("Found builder {0}", bldr.getClass().getName());
			}
 			
			return Optional.of(configureBuilder(def, bldr));
		}
		
		var layerCtx = LayerContext.get(builderClass);
		for(var lyr : layerCtx.globalLayers()) {
			
			LOG.debug("Trying layer ''{0}''", lyr); 
			bldrOr = ServiceLoader.load(lyr, builderClass).findFirst();
			if(bldrOr.isEmpty()) {
				LOG.debug("Still NO repository builder of type ''{0}'' of class ''{1}'' in service layers, giving up", id, builderClass.getName());
			}
			else {
				return Optional.of(configureBuilder(def, bldrOr.get()));
			}
		}
		
		return Optional.empty();
	}

	private <BLDR extends RepositoryBuilder<BLDR, REPO>, REPO extends Repository> REPO configureBuilder(RepositoryDef def, BLDR bldr) {
		bldr.withName(def.name());
		bldr.withRoot(def.root().toString());
		
		/* Sanity check */
		if(bldr instanceof RemoteRepositoryBuilder rbldr) {
			rbldr.withReleases(def.snapshots().orElseGet(() -> def.releases().isEmpty()));
			rbldr.withSnapshots(def.releases().orElseGet(() -> def.snapshots().isEmpty()));
			rbldr.withId(def.id());
		}
		else if(bldr instanceof AppRepositoryBuilder abldr) {
			if(!def.id().equals(AppRepository.ID)) {
				throw new IllegalStateException(MessageFormat.format("App repository ''{0}'' must have id of ''{1}''", def.id(), AppRepository.ID));
			}
			def.pattern().ifPresent(abldr::withPattern);
		}
		else if(bldr instanceof LocalRepositoryBuilder lbldr) {
			if(!def.id().equals(LocalRepository.ID)) {
				throw new IllegalStateException(MessageFormat.format("Local repository ''{0}'' must have id of ''{1}''", def.id(), LocalRepository.ID));
			}
			def.pattern().ifPresent(lbldr::withPattern);
		}
		else
			throw new UnsupportedOperationException();
		
		return bldr.build();
	}
}