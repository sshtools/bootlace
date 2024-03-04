package com.sshtools.bootlace.platform;

import static java.lang.String.format;

import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.sshtools.bootlace.api.BootContext;
import com.sshtools.bootlace.api.ChildLayer;
import com.sshtools.bootlace.api.Collect;
import com.sshtools.bootlace.api.Exceptions;
import com.sshtools.bootlace.api.Http;
import com.sshtools.bootlace.api.Http.HttpClientFactory;
import com.sshtools.bootlace.api.LayerContext;
import com.sshtools.bootlace.api.Logs;
import com.sshtools.bootlace.api.Logs.BootLog;
import com.sshtools.bootlace.api.Logs.Log;
import com.sshtools.bootlace.api.Plugin;
import com.sshtools.bootlace.api.PluginContext;
import com.sshtools.bootlace.api.PluginLayer;
import com.sshtools.bootlace.api.PluginRef;
import com.sshtools.bootlace.api.RootContext;
import com.sshtools.bootlace.api.RootLayer;

/**
 * This is where the module graph is actually constructed an each {@link Module}
 * loaded into the JVM.
 * <p>
 * It takes the flat list of layers provided by the builders, and arranges the
 * in a tree (wth possible multiple roots).
 * <p>
 * Each layer may also expand into further layers as the directories and jars
 * are examined and any <code>layers.ini</code> found within them are used to
 * construct further layers or suppliment archives and paths provided by the
 * root (or parent) layer definition.
 * 
 */
public final class RootLayerImpl extends AbstractLayer implements RootLayer {
	
	class RootContextImpl implements RootContext {

		private List<Listener> listeners = new ArrayList<>();

		@Override
		public void addListener(Listener listener) {
			listeners.add(listener);
		}

		@Override
		public BootContext app() {
			return app.orElseThrow(() -> new IllegalStateException("No app context set."));
		}

		@Override
		public boolean canShutdown() {
			return true;
		}

		@Override
		public void removeListener(Listener listener) {
			listeners.remove(listener);
		}

		@Override
		public void shutdown() {
			sem.release();
		}

		@Override
		public boolean canRestart() {
			return false;
		}

		@Override
		public void restart() {
		}

	}

	private final static Log LOG = Logs.of(BootLog.LAYERS);

	private final Optional<BootContext> app;
	private final RootContextImpl root;
	private final Semaphore sem = new Semaphore(1);
	private final HttpClientFactory httpClientFactory;
	private final String userAgent;
	private final Optional<BootstrapRepository> bootstrapRepository;
	protected final Map<String, ChildLayer> layers;

	protected final Map<String, ModuleLayer> moduleLayers = new ConcurrentHashMap<>();
	
	RootLayerImpl(RootLayerBuilder builder) {
		super(builder);

		this.bootstrapRepository = builder.bootstrapRepository.or(() -> BootContext.isDeveloper() 
				? Optional.of(BootstrapRepository.bootstrapRepository()) 
				: Optional.empty());
		
		this.userAgent = builder.userAgent.orElse("Bootlace");
		this.httpClientFactory = builder.httpClientFactory.orElseGet(Http::defaultClientFactory);

		layers = builder.layers.stream().collect(Collect.toLinkedMap(ChildLayer::id, Function.identity()));

		layers.forEach((k, v) -> ((AbstractChildLayer) v).appLayer(this));
		LOG.debug("Attached root layer to child layers");

		sem.tryAcquire();
		app = builder.appContext;
		root = new RootContextImpl();

		layers.values().forEach(l -> {
			open(l);
			afterOpen(l);
		});

	}
	
	public Optional<BootstrapRepository> bootstrapRepository() {
		return bootstrapRepository;
	}

	@Override
	public ChildLayer getLayer(String id) {
		var layer = layers.get(id);
		if (layer == null)
			throw new IllegalArgumentException(format("No layer with ID ''{0}''", id));
		return layer;
	}

	@Override
	public String toString() {
		return "AppLayer [id()=" + id() + ", name()=" + name() + ", global()=" + global() + ", appRepositories()="
				+ appRepositories() + ", localRepositories()=" + localRepositories() + ", remoteRepositories()="
				+ remoteRepositories() + ", monitor()=" + monitor() + ", app=" + app + ", userAgent=" + userAgent + "]";
	}

	@Override
	public void waitFor() {
		try {
			sem.acquire(1);
		} catch (InterruptedException e) {
			throw new IllegalStateException("Interrupted.", e);
		} finally {
			sem.release();
		}
	}

	void afterOpen(ChildLayer child) {
		try {
			if(child instanceof PluginLayer) {
				var pchild = (PluginLayerImpl)child;
				LOG.info("Post-initialising plugins in layer ''{0}''", child.id());
				pchild.pluginRefs.forEach(ref -> { 
					LOG.info("    {0}", ref.plugin().getClass().getName());
					PluginContextProviderImpl.current.set(ref.context());
					try {
						ref.plugin().afterOpen(ref.context());
					}
					catch(RuntimeException re) {
						throw re;
					}
					catch(Exception e) {
						throw new Exceptions.FailedToOpenPlugin(ref, e);
					}
					finally {
						PluginContextProviderImpl.current.set(null);
					} 
				});
			}
		} finally {
			try {
				child.onAfterOpen();
			} finally {
				root.listeners.forEach(l -> l.layerOpened(child));
			}
		}
	}

	void beforeClose(ChildLayer layer) {

		try {
			if(layer instanceof PluginLayer) {
				var pchild = (PluginLayerImpl)layer;
				try {
					pchild.pluginRefs.forEach(ref -> {
						PluginContextProviderImpl.current.set(ref.context());
						try {
							ref.plugin().beforeClose(ref.context());
						}
						catch(RuntimeException re) {
							throw re;
						}
						catch(Exception e) {
							throw new Exceptions.FailedToClosePlugin(ref, e);
						}
						finally {
							PluginContextProviderImpl.current.set(null);
						}
					});
				}
				finally {
					try {
						pchild.pluginRefs.forEach(ref -> ref.context().close());
					}
					finally {
						pchild.pluginRefs.forEach(ref -> {
							PluginContextProviderImpl.current.set(ref.context());
							try {
								ref.plugin().close();
							}
							catch(RuntimeException re) {
								throw re;
							}
							catch(Exception e) {
								throw new Exceptions.FailedToClosePlugin(ref, e);
							}
							finally {
								PluginContextProviderImpl.current.set(null);
							} 
						});
					}
				}
			}
		} finally {
			root.listeners.forEach(l -> l.layerClosed(layer));
		}
	}

	void close(ChildLayer layer) {
		LayerContextImpl.deregister(layer.id(), moduleLayers.remove(layer.id()));
	}

	void open(ChildLayer layerDef) {
		
		
//	; TODO 
//	;
//	; 1 Need way to express that an artifact comes from the framework, overriding
//	; what is in contributed descriptors
//	;
//	
//	

		LOG.info("Opening child layer {0}", layerDef.id());
		LOG.debug(layerDef);

		var id = layerDef.id();			
		var childLoader = new ChildLayerLoader(layerDef, ClassLoader.getSystemClassLoader());
			
		monitor().ifPresent(mon -> mon.loadingLayer(layerDef));
		if (layerDef instanceof PluginLayer) {

			if (LOG.debug()) {
				LOG.debug("{0} is a plugin layer", layerDef.id());
			}

			//
			var pluginLayerDef = (PluginLayerImpl) layerDef;
			var layerArtifacts = new LayerArtifactsImpl(pluginLayerDef, httpClientFactory, root);
			pluginLayerDef.layerArtifacts = Optional.of(layerArtifacts);
			var paths = layerArtifacts.paths(); 

			var parents = parents(id, layerDef.parents());
			
			var layer = create(id, parents, paths, childLoader);
			moduleLayers.put(id, layer);

			LayerContextImpl.register(layer, layerDef, childLoader);

			loadPlugins(pluginLayerDef, id, parents, layer);
		
			LOG.info("Initialising plugins in layer ''{0}''", id);
			pluginLayerDef.pluginRefs.forEach(ref -> { 
				LOG.info("    {0}", ref.plugin().getClass().getName()); 
				PluginContextProviderImpl.current.set(ref.context());
				try {
					ref.plugin().open(ref.context());
				}
				catch(RuntimeException re) {
					throw re;
				}
				catch(Exception e) {
					throw new Exceptions.FailedToClosePlugin(ref, e);
				}
				finally { 
					PluginContextProviderImpl.current.set(null);
				}
			});
		} else {
			/* Dynamic layer */
			if (LOG.debug()) {
				LOG.debug("{0} is a dynamic layer", layerDef.id());
			}
			
			var parents = parents(id, layerDef.parents());
			var layer = create(id, parents, Collections.emptySet(), childLoader);
			moduleLayers.put(id, layer);
			
			LayerContextImpl.register(layer, layerDef, childLoader);
		}

			
		LOG.info("Opened child layer {0}", layerDef.id());
		layerDef.onOpened();
		monitor().ifPresent(mon -> mon.loadedLayer(layerDef));
	}

	private ModuleLayer create(String layerId, Set<ModuleLayer> parentLayers, Set<Path> modulePathEntries, ChildLayerLoader childLoader) {
		var finder = ModuleFinder.of(modulePathEntries.toArray(Path[]::new));

		var roots = finder.findAll().stream().map(m -> m.descriptor().name()).collect(Collectors.toSet());

		var appConfig = Configuration.resolveAndBind(finder,
				parentLayers.stream().map(ModuleLayer::configuration).collect(Collectors.toList()), ModuleFinder.of(),
				roots);

//		var module = ModuleLayer.defineModulesWithOneLoader(appConfig, parentLayers.stream().toList(),
//				/*childLoader*/ClassLoader.getSystemClassLoader()).layer();
		
		var module = ModuleLayer.defineModulesWithOneLoader(appConfig, parentLayers.stream().toList(),
				childLoader).layer();
		
		childLoader.module(module);
		return module;
	}

	private void loadPlugins(PluginLayerImpl pluginLayer, String id, Set<ModuleLayer> parents, ModuleLayer layer) {

		LOG.info("Loading plugins for layer ''{0}''", id);

		/*
		 * We only use the first implementation found. This will be the childs own
		 * {@link Plugin}
		 */
		var pluginObjects = new ConcurrentHashMap<Class<? extends Plugin>, Plugin>();
		
		var it = ServiceLoader.load(layer, Plugin.class).iterator();
		while(it.hasNext()) {
			
			/* Create context first and register it so it can be accessed by plugins
			 * while they are being instantiated (e.g. for accessing plugin instances
			 * statically when defining member variables)
			 */
			var context = new PluginContext() {

				private final List<AutoCloseable> closeable = new ArrayList<>();

				@Override
				public void autoClose(AutoCloseable... closeable) {
					this.closeable.addAll(Arrays.asList(closeable));
				}

				@Override
				public void close() {
					closeable.forEach(t -> {
						try {
							t.close();
						} catch (Exception e) {
						}
					});
				}

				@SuppressWarnings("unchecked")
				@Override
				public <P extends Plugin> Optional<P> pluginOr(Class<P> plugin) {
					var res = Optional.ofNullable((P) pluginObjects.get(plugin));
					if(res.isEmpty()) {
						res = searchParents(parents, plugin);
					}
					return res;
				}

				@Override
				public RootContext root() {
					return root;
				}

				private <P extends Plugin> Optional<P> searchParents(Collection<ModuleLayer> parents, Class<P> plugin) {
					for(var mod : parents) {
						var ctx  = LayerContext.get(mod);
						var lyr = ctx.layer();
						if(lyr instanceof PluginLayer) {
							var plyr = (PluginLayer)lyr;
							for(var ref : plyr.pluginRefs()) {
								var pRes = ref.context().pluginOr(plugin);
								if(pRes.isPresent())
									return pRes;
							}
						}
						var pRes = searchParents(mod.parents(), plugin);
						if(pRes.isPresent())
							return pRes;
					}
					return Optional.empty();
				}
			};
			PluginContextProviderImpl.current.set(context);
			try {
			
				var plugin = it.next();
				
				if(!plugin.getClass().getModule().getLayer().equals(layer)) {
					if(LOG.trace())
						LOG.trace("Skipping plugin ''{0}'' because it's in a parent layer.", plugin.getClass().getName());
					continue;
				}
				
	
				LOG.info("Loading plugin ''{0}''", plugin.getClass().getName());
	
				pluginLayer.pluginRefs.add(new PluginRef(plugin, context));
				pluginObjects.put(plugin.getClass(), plugin);
	
				LOG.info("Loaded plugin ''{0}''", plugin.getClass().getName());
			}
			finally {
				PluginContextProviderImpl.current.set(context);				
			}
		}
		
	}

	private Set<ModuleLayer> parents(String name, Collection<String> parents) {
		var parentLayers = new LinkedHashSet<ModuleLayer>();

		for (String parent : parents) {
			ModuleLayer parentLayer = moduleLayers.get(parent);
			if (parentLayer == null) {
				throw new IllegalArgumentException(
						"Layer '" + name + "': parent layer '" + parent + "' not configured yet");
			}

			parentLayers.add(parentLayer);
		}

		return parentLayers.isEmpty() ? Set.of(ModuleLayer.boot()) : parentLayers;
	}

	@Override
	public Optional<RootLayer> appLayer() {
		return Optional.of(this);
	}

	@Override
	protected Set<String> parents() {
		return Collections.emptySet();
	}
}