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

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Function;

import com.sshtools.bootlace.api.Access;
import com.sshtools.bootlace.api.AppRepository;
import com.sshtools.bootlace.api.AppRepository.AppRepositoryBuilder;
import com.sshtools.bootlace.api.ChildLayer;
import com.sshtools.bootlace.api.GAV;
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
		protected Optional<Access> access = Optional.empty();

		public AbstractChildLayerBuilder(String id) {
			super(id);
		}

		@SuppressWarnings("unchecked")
		public final B withAccess(Access access) {
			this.access = Optional.of(access);
			return (B) this;
		}

		@Override
		protected B fromComponentSection(Section section) {
			withParents(section.getAllOr("parent").orElse(new String[0]));
			withParents(section.getAllOr("parents").orElse(new String[0]));
			section.getOr("access").map(Access::valueOf).ifPresent(this::withAccess);
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
	final Access access;

	private Optional<RootLayer> rootLayer = Optional.empty();

	AbstractChildLayer(AbstractChildLayer.AbstractChildLayerBuilder<?> layerBuilder) {
		super(layerBuilder);
		this.access = layerBuilder.access.orElse(Access.PROTECTED);
		parents = new LinkedHashSet<>(layerBuilder.parents);
	}

	@Override
	public Access access() {
		return access;
	}

	@Override
	public final Optional<RootLayer> rootLayer() {
		return rootLayer;
	}

	final void rootLayer(RootLayerImpl rootLayer) {
		if(rootLayer == null && this.rootLayer.isPresent()) {
			((RootLayerImpl)this.rootLayer.get()).publicLayers.remove(id());
			this.rootLayer = null;
		}
		else {
			this.rootLayer = Optional.of(rootLayer);
			if(access().equals(Access.PUBLIC) && rootLayer.publicLayers.put(id(), this) != null) {
				throw new IllegalStateException(MessageFormat.format("More than one PUBLIC layer with the ID {0}", id()));
			}
		}
	}

	@Override
	public final Set<String> parents() {
		return parents;
	}

	public void onOpened() {
	}

	public void onAfterOpen() {
	}

	@Override
	public String toString() {
		return "AbstractChildLayer [id()=" + id() + ", name()=" + name() + ", appRepositories()=" + appRepositories()
				+ ", localRepositories()=" + localRepositories() + ", monitor()=" + monitor() 
				+ ", remoteRepositories()=" + remoteRepositories() + ", parents=" + parents + ", appLayer=" + rootLayer
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
			if(rootLayer.isPresent()) {
				var app = rootLayer.get();
				for (var parent : parents) {
					var moduleLayer = (AbstractChildLayer) ((RootLayerImpl) app).getLayer(parent);
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
			
		rootLayer.ifPresent(app -> {
			var rootLayer = (RootLayerImpl) app;
			for (var parent : parents) {
				var layer = rootLayer.getLayer(parent);
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
			LOG.debug("Looking for repository definition `{0}`, type `{1}` for layer `{2}`", id, repoClass.getName(), id());
		}
 		
 		var def = findRepositoryDef(id);
 		if(def == null) {
 			if (LOG.debug()) {
				LOG.debug("No custom repository configuration, using defaults");
			}
 			
 			if(id.equals(BootstrapRepository.ID)) {
 				return (Optional<REPO>) ((RootLayerImpl)rootLayer.get()).bootstrapRepository();
 			}
 			else if(id.equals(LocalRepository.ID)) {
 				return (Optional<REPO>) Optional.of(LocalRepositoryImpl.localRepository());
 			}
 			else if(id.equals(AppRepository.ID)) {
 				return (Optional<REPO>) Optional.of(AppRepositoryImpl.appRepository());
 			}
 			else {
 				throw new IllegalArgumentException(MessageFormat.format("No repository def for `{0}`.", id));
 			}
 		}
 		if(!def.type().equals(repoClass)) {
 			throw new IllegalArgumentException(MessageFormat.format("Unexpected repository type for `{0}`. Expected `{1}`, got `{2}`.", id, repoClass.getName(), def.type().getName()));
 		}
 		
		var thisModuleLayer = getClass().getModule().getLayer();
		var bldrOr = ServiceLoader.load(thisModuleLayer, builderClass).findFirst();
		if(bldrOr.isEmpty()) {
			LOG.debug("NO repository builder of type `{0}` of class `{1}` in child layers of `{2}`, trying service layers", id, builderClass.getName(), thisModuleLayer);
		}
		else {
			
			var bldr = bldrOr.get();
			
 			if (LOG.debug()) {
				LOG.debug("Found builder {0}", bldr.getClass().getName());
			}
 			
			return Optional.of(configureBuilder(def, bldr));
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
				throw new IllegalStateException(MessageFormat.format("App repository `{0}` must have id of `{1}`", def.id(), AppRepository.ID));
			}
			def.pattern().ifPresent(abldr::withPattern);
		}
		else if(bldr instanceof LocalRepositoryBuilder lbldr) {
			def.pattern().ifPresent(lbldr::withPattern);
		}
		else
			throw new UnsupportedOperationException();
		
		return bldr.build();
	}
}