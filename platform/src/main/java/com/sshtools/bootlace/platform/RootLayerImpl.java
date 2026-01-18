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

import static com.sshtools.bootlace.api.Lang.runWithLoader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import com.sshtools.bootlace.api.Access;
import com.sshtools.bootlace.api.ArtifactRef;
import com.sshtools.bootlace.api.BootContext;
import com.sshtools.bootlace.api.ChildLayer;
import com.sshtools.bootlace.api.Collect;
import com.sshtools.bootlace.api.DependencyGraph;
import com.sshtools.bootlace.api.DependencyGraph.Dependency;
import com.sshtools.bootlace.api.Exceptions;
import com.sshtools.bootlace.api.FilteredClassLoader;
import com.sshtools.bootlace.api.GAV;
import com.sshtools.bootlace.api.Http;
import com.sshtools.bootlace.api.Http.HttpClientFactory;
import com.sshtools.bootlace.api.Layer;
import com.sshtools.bootlace.api.LayerContext;
import com.sshtools.bootlace.api.Logs;
import com.sshtools.bootlace.api.Logs.BootLog;
import com.sshtools.bootlace.api.Logs.Log;
import com.sshtools.bootlace.api.NodeModel;
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
 * in a tree (with possible multiple roots).
 * <p>
 * Each layer may also expand into further layers as the directories and jars
 * are examined and any <code>layers.ini</code> found within them are used to
 * construct further layers or supplement archives and paths provided by the
 * root (or parent) layer definition.
 * 
 */
public final class RootLayerImpl extends AbstractLayer implements RootLayer {
	
	private final class ChildPluginContext implements PluginContext {
		private final ConcurrentHashMap<Class<? extends Plugin>, Plugin> pluginObjects;
		private final Set<ModuleLayer> parents;
		private final List<AutoCloseable> closeable = new ArrayList<>();
		private final PluginLayerImpl layer;

		private ChildPluginContext(PluginLayerImpl layer, ConcurrentHashMap<Class<? extends Plugin>, Plugin> pluginObjects,
				Set<ModuleLayer> parents) {
			this.layer = layer;
			this.pluginObjects = pluginObjects;
			this.parents = parents;
			
		}

		@Override
		public Layer layer() {
			return layer;
		}

		@Override
		public boolean hasPlugin(String className) {
			return pluginObjects.keySet().stream().map(Class::getName).toList().contains(className);
		}

		@Override
		public Optional<Layer> layer(String id) {
			var global = ((RootLayerImpl)layer.rootLayer().get()).publicLayers.get(id);
			if(global != null) 
				return Optional.of(global);
			return Optional.ofNullable(searchLayersForId(layer.rootLayer().orElse(null), layer, id));
		}

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

		private Layer searchLayersForId(RootLayer rootLayer, Layer layer, String id) {
			if(layer.id().equals(id)) {
				return layer;
			}
			if(layer instanceof ChildLayer cl) {
				for(var parent : cl.parents()) {
					var parentLayer = ((RootLayerImpl)rootLayer).layers.get(parent);
					if(parentLayer != null) {
						if(parentLayer.access() == Access.PRIVATE) {
							/* Skip private parent.*/
							continue;
						}
						var l = searchLayersForId(rootLayer, parentLayer, id);
						if(l != null)
							return l;
					}
				}
			}
			return null;
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
	}

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

		@Override
		public Optional<URL> globalResource(String path) {
			return RootLayerImpl.this.globalResource(path);
		}

		@Override
		public boolean hasPlugin(String className) {
			return pluginObjects.keySet().stream().map(Class::getName).toList().contains(className);
		}
		

		final Set<GAV> artifacts = new LinkedHashSet<GAV>();

		void addArtifact(GAV artifact) {
			artifacts.add(artifact);
		}
		
		boolean hasArtifact(GAV artifact) {
			return artifacts.contains(artifact.toWithoutVersion());
		}

	}

	private final static Log LOG = Logs.of(BootLog.LAYERS);

	private final Optional<BootContext> app;
	private final RootContextImpl root;
	private final Semaphore sem = new Semaphore(1);
	private final HttpClientFactory httpClientFactory;
	private final String userAgent;
	private final Optional<BootstrapRepository> bootstrapRepository;
	protected final Map<String, ChildLayer> publicLayers = new ConcurrentHashMap<>();
	private Map<Class<? extends Plugin>, Plugin> pluginObjects = new ConcurrentHashMap<>();
	private Map<String, ClassLoader> globalResourceLoaders = new ConcurrentHashMap<>();
	private final Map<String, ChildLayer> tempLayers = new LinkedHashMap<>();
	private final Optional<PluginInitializer> pluginInitializer;
	private final Optional<PluginDestroyer> pluginDestroyer;
	protected final Map<String, ModuleLayer> moduleLayers = new ConcurrentHashMap<>();
	protected final Map<String, ClassLoader> moduleLoaders = new ConcurrentHashMap<>();

	private boolean initialising;
	private ClassLoader rootLoader;
	
	final Map<String, ChildLayer> layers;
	
	@SuppressWarnings("unused")
	RootLayerImpl(RootLayerBuilder builder) {
		super(builder);
		this.pluginInitializer = builder.pluginInitializer;
		this.pluginDestroyer = builder.pluginDestroyer;
		this.bootstrapRepository = builder.bootstrapRepository.or(() -> BootContext.isDeveloper() 
				? Optional.of(BootstrapRepository.bootstrapRepository()) 
				: Optional.empty());
		this.userAgent = builder.userAgent.orElse("Bootlace");
		this.httpClientFactory = builder.httpClientFactory.orElseGet(Http::defaultClientFactory);

		layers =  builder.layers.stream().collect(Collect.toLinkedMap(ChildLayer::id, Function.identity()));

		layers.forEach((k, v) -> ((AbstractChildLayer) v).rootLayer(this));
		LOG.debug("Attached root layer to child layers");

		rootLoader = new FilteredClassLoader.Builder(ClassLoader.getSystemClassLoader()).
				build();
		
		sem.tryAcquire();
		app = builder.appContext;
		root = new RootContextImpl();
		
		initialising = true;
		try {
			layers.values().forEach(l -> {
				open(l);
				afterOpen(l);
			});
		}
		finally {
			initialising = true;
			layers.putAll(tempLayers);
			tempLayers.clear();;
		}
		
		pluginObjects.forEach((k,v) -> {
			/* TODO move this to plugin loading .. so they are unregistered on unload */
			try {
				var en = v.getClass().getClassLoader().getResources("META-INF/MANIFEST.MF");
				while(en.hasMoreElements()) {
					var url = en.nextElement();
					try(var in = url.openStream()) {
						var mf = new Manifest(in);
						var resources = mf.getMainAttributes().getValue("X-NPM-Resources");
						if(resources == null) {							
							resources = mf.getMainAttributes().getValue("X-Bootlace-Resources");
						}
						if(resources != null) {
							globalResourceLoaders.put(resources, v.getClass().getClassLoader());
						}
					}
				}
			}
			catch(IOException ioe) {
				throw new UncheckedIOException(ioe);
			}
		});

	}
	
	@Override
	List<ChildLayer> childLayers() {
		return  layers.values().stream().
				filter(l -> l.parents().isEmpty()).
				toList();
	}

	@Override
	public ClassLoader loader() {
		return rootLoader;
	}

	@Override
	public ModuleLayer moduleLayer() {
		return ModuleLayer.boot();
	}

	@Override
	public Access access() {
		return Access.PUBLIC;
	}

	@Override
	public Optional<URL> globalResource(String path) {
		var fullPath ="npm2mvn/" + path;
		for(var en : globalResourceLoaders.entrySet()) {
			if(fullPath.startsWith(en.getKey() + "/")) {
				return Optional.ofNullable(en.getValue().getResource(fullPath));
			}
		}
		return Optional.empty();
	}

	public Optional<BootstrapRepository> bootstrapRepository() {
		return bootstrapRepository;
	}

	@Override
	public ChildLayer getLayer(String id) {
		return getLayerOr(id).orElseThrow(() -> new IllegalArgumentException(MessageFormat.format("No layer with ID `{0}`", id)));
	}

	@Override
	public String toString() {
		return "AppLayer [id()=" + id() + ", name()=" + name() + ", appRepositories()="
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
	
	void removeLayer(String id) {
		var map = initialising ? tempLayers : layers;
		map.remove(id);
	}
	
	void addLayer(String id, ChildLayer layer) {
		var map = initialising ? tempLayers : layers;
		if(map.put(id, layer) != null) {
			throw new IllegalArgumentException(MessageFormat.format("Layer `{0}` is already known.", id));
		}
	}
	
	Optional<ChildLayer> getLayerOr(String id) {
		var l = layers.get(id);
		if(l == null) {
			l = tempLayers.get(id);
		}
		return Optional.ofNullable(l);
	}

	void afterOpen(ChildLayer child) {
		try {
			if(child instanceof PluginLayer) {
				var pchild = (PluginLayerImpl)child;
				LOG.info("Post-initialising plugins in layer `{0}`", child.id());
				pchild.pluginRefs.forEach(ref -> {
					var plugin = ref.plugin(); 
					LOG.info("    {0}", plugin.getClass().getName());
					PluginContextProviderImpl.current.set(ref.context());
					runWithLoader(child.loader(), () -> {
						try {
							plugin.afterOpen(ref.context());
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
					 
				});
			}
		} finally {
			try {
				((AbstractLayer)child).onAfterOpen();
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
						runWithLoader(pchild.loader(), () -> {
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
					});
				}
				finally {
					try {
						pchild.pluginRefs.forEach(ref -> ref.context().close());
					}
					finally {
						pchild.pluginRefs.forEach(ref -> {
							runWithLoader(pchild.loader(), () -> {
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
									try {
										pluginDestroyer.ifPresent(d -> {
											d.destroy(ref.plugin(), pchild);
										});
									} finally {
										PluginContextProviderImpl.current.set(null);
									}
								}	
							});
							 
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
		moduleLoaders.remove(layer.id());
	}

	void open(ChildLayer layerDef) {
		
		
//	; TODO 
//	;
//	; 1 Need way to express that an artifact comes from the framework, overriding
//	; what is in contributed descriptors
//	;
//	
//	

		var id = layerDef.id();			

		LOG.info("Opening child layer `{0}`. {1}", id, layerDef.name().orElse("Unnammed"));
		LOG.debug(layerDef);
			
		monitor().ifPresent(mon -> mon.loadingLayer(layerDef));
		if (layerDef instanceof PluginLayer) {
			
			if (LOG.debug()) {
				LOG.debug("`{0}` is a plugin layer", id);
			}

			//
			var pluginLayerDef = (PluginLayerImpl) layerDef;
			
			LOG.info("Artifacts: {0}" , System.lineSeparator() + "    " +String.join("," + System.lineSeparator() + "    ", pluginLayerDef.artifacts().stream().map(ArtifactRef::toString).toList()));
			
			var layerArtifacts = new LayerArtifactsImpl(pluginLayerDef, httpClientFactory, root);
			pluginLayerDef.layerArtifacts = Optional.of(layerArtifacts);
			var paths = layerArtifacts.paths(); 

			var parents = parents(layerDef);
			ModuleLayer layer = createAndRegisterLoader(layerDef, paths, parents);

			loadPlugins(pluginLayerDef, id, parents, layer);
		
			LOG.info("Initialising plugins in layer `{0}`", id);
			pluginLayerDef.pluginRefs.forEach(ref -> { 
				LOG.info("    {0}", ref.plugin().getClass().getName());
				runWithLoader(layerDef.loader(), () -> { 
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
				
			});
		} else {
			/* Dynamic layer */
			if (LOG.debug()) {
				LOG.debug("{0} is a dynamic layer", layerDef.id());
			}
			
			createAndRegisterLoader(layerDef, Collections.emptySet(), parents(layerDef));
		}

			
		LOG.info("Opened child layer {0}", layerDef.id());
		((AbstractLayer)layerDef).onOpened();
		monitor().ifPresent(mon -> mon.loadedLayer(layerDef));
	}
	
	private ModuleLayer createAndRegisterLoader(ChildLayer layerDef, Set<Path> paths, Set<ModuleLayer> parents) {

		var childLayerLoader = new FilteredClassLoader.Builder(rootLoader).
				build();
		
		var layer = createModuleLayer(layerDef, parents, paths, childLayerLoader);
		var modules = layer.modules();

		ClassLoader childLoader;
		if (modules.isEmpty()) {
			childLoader = childLayerLoader;
		} else {
			/* We use defineModulesWithOneLoader so there will only be one  anyway */
			childLoader = layer.findLoader(modules.iterator().next().getName());
		}
		
		moduleLayers.put(layerDef.id(), layer);
		moduleLoaders.put(layerDef.id(), childLoader);
		
		LayerContextImpl.register(layer, layerDef, childLoader);

		return layer;
	}

	@SuppressWarnings("preview")
	private ModuleLayer createModuleLayer(ChildLayer layerDef, Set<ModuleLayer> parentLayers, Set<Path> modulePathEntries, ClassLoader loader) {
	
		/* Sort the module paths so that directories come last, and also
		 * canonicalize the paths. JPMS doesn't seem to liked directories that
		 * are symlinks, so we need to get the actual paths.
		 */
		var finder = ModuleFinder.of(modulePathEntries.stream().sorted((p1, p2) -> {
			var d1 = Files.isDirectory(p1) ? 1 : -1;
			var d2 = Files.isDirectory(p2) ? 1 : -1;
			var o = Integer.valueOf(d1).compareTo(d2);
			if(o == 0) {
				return p1.compareTo(p2);
			}
			else {
				return o;
			}
		}).map(Path::toAbsolutePath).map(p -> {
			try {
				return p.toRealPath();
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}).toList().toArray(Path[]::new));
		
		/* Find all the modules we want to load */
		var roots = finder.findAll().stream().map(m -> m.descriptor().name()).
				collect(Collectors.toSet());

		
		/* Create a module layer with  a single class loader that includes
		 * all the modules in this layer
		 */
		var appConfig = Configuration.resolveAndBind(finder,
				parentLayers.stream().map(ModuleLayer::configuration).
					collect(Collectors.toList()), ModuleFinder.of(),
				roots);
		
		
		var ctrlr = ModuleLayer.defineModulesWithOneLoader(appConfig, parentLayers.stream().toList(),
				loader);
		
		layerDef.moduleParameters().ifPresent(modprms -> {

			// TODO custom exports, reads and opens	
			
			modprms.nativeModules().forEach(mod -> {
				ctrlr.layer().findModule(mod).ifPresentOrElse(rmod -> {
					LOG.info("Enabling native access for module {0}", rmod.getName());
					ctrlr.enableNativeAccess(rmod);
				}, () -> {
					throw new IllegalArgumentException(MessageFormat.format("Module {0} configured for native access does not exist.", mod));
				});
			});	
		});
		
		var mlayer = ctrlr.layer();
		LOG.info("Created layer: {0} - {1}", layerDef.id(), mlayer);
		return mlayer;
	}
	

	private final static class JPMSPlugins  {
		private final List<JPMSNode> list;
		
		private JPMSPlugins(ModuleLayer layer) {
			 list = ServiceLoader.load(layer, Plugin.class).
					 stream().
					 map(p -> new JPMSNode(p, this)).
					 toList();
			
		}
		
		private List<JPMSNode> sorted() {
			var topologicallySorted = new ArrayList<>(new DependencyGraph<>(list).getTopologicallySorted());
			list.forEach(l -> { 
				if(!topologicallySorted.contains(l)) {
					topologicallySorted.add(l);
				}
			});
			return  topologicallySorted;
		}
		
		private Optional<JPMSNode> forModule(String req) {
			return list.stream().filter(p -> p.getProvider().type().getModule().getName().equals(req)).findFirst();
		}
	}
	
	private final static class JPMSNode implements NodeModel<JPMSNode> {
		
		private final ServiceLoader.Provider<Plugin> provider;
		private final JPMSPlugins plugins;
		
		private JPMSNode(Provider<Plugin> provider, JPMSPlugins plugins) {
			super();
			this.provider = provider;
			this.plugins = plugins;
		}

		private ServiceLoader.Provider<Plugin> getProvider() {
			return provider;
		}

		@Override
		public String name() {
			return provider.type().getName();
		}

		@Override
		public String toString() {
			return name() + " [" + provider.type().getModule().getName() + "]";
		}

		@Override
		public void dependencies(Consumer<Dependency<JPMSNode>> model) {
			var desc = provider.type().getModule().getDescriptor();
			desc.requires().stream().forEach(req -> {
				plugins.forModule(req.name()).ifPresent(node -> {
					model.accept(new Dependency<RootLayerImpl.JPMSNode>(node, this));
					node.dependencies(model);
				});	
			});
		}
		
	}

	private void loadPlugins(PluginLayerImpl pluginLayer, String id, Set<ModuleLayer> parents, ModuleLayer layer) {

		LOG.info("Loading plugins for layer `{0}`", id);

		var pluginObjects = new ConcurrentHashMap<Class<? extends Plugin>, Plugin>();
		var pluginProcessor = new JPMSPlugins(layer);
		var sorted = pluginProcessor.sorted();
		var it = sorted.iterator();
		
		while(it.hasNext()) {
			
			/* Create context first and register it so it can be accessed by plugins
			 * while they are being instantiated (e.g. for accessing plugin instances
			 * statically when defining member variables)
			 */
			var context = new ChildPluginContext(pluginLayer, pluginObjects, parents);
			PluginContextProviderImpl.current.set(context);
			try {
			
				var pluginProvider = it.next().getProvider();
				var type = pluginProvider.type();
				var modLayer = type.getModule().getLayer();
				
				if(!modLayer.equals(layer)) {
					if(LOG.trace())
						LOG.trace("Skipping plugin `{0}` because it is in a different layer.", type.getName());
					continue;
				}

				/* The is where the plugin is actually instantiated */
				Plugin plugin;
				if(pluginInitializer.isPresent()) {
					LOG.debug("Loading plugin `{0}` using custom initializer", type.getName());
					plugin = pluginInitializer.get().initialize(pluginProvider, pluginLayer, parents);
					
				}
				else {
					LOG.debug("Loading plugin `{0}`", type.getName());
					plugin = pluginProvider.get();
				}
	
				pluginLayer.pluginRefs.add(new PluginRef(plugin, context));
				pluginObjects.put(plugin.getClass(), plugin);
				if(this.pluginObjects.put(plugin.getClass(), plugin) != null) {
					throw new IllegalStateException(MessageFormat.format("Plugin `{0}` found more than once.",  plugin.getClass().getName()));
				}
	
				LOG.info("Loaded plugin `{0}`", plugin.getClass().getName());
			}
			finally {
				PluginContextProviderImpl.current.set(context);				
			}
		}
		
	}

	private Set<ModuleLayer> parents(ChildLayer layer) {
		var parentLayers = new LinkedHashSet<ModuleLayer>();

		for (String parent : layer.parents()) {
			ModuleLayer parentLayer = moduleLayers.get(parent);
			if (parentLayer == null) {
				throw new IllegalArgumentException(
						"Layer `" + layer.id() + "`: parent layer `" + parent + "` not configured yet");
			}

			parentLayers.add(parentLayer);
		}

		return parentLayers.isEmpty() ? Set.of(ModuleLayer.boot()) : parentLayers;
	}

	@Override
	public Optional<RootLayer> rootLayer() {
		return Optional.of(this);
	}

	@Override
	protected Set<String> parents() {
		return Collections.emptySet();
	}

	@Override
	protected void onAfterOpen() {
	}

	@Override
	protected void onOpened() {
	}
}