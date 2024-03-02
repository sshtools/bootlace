package com.sshtools.bootlace.platform;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.sshtools.bootlace.api.AppRepository;
import com.sshtools.bootlace.api.Artifact;
import com.sshtools.bootlace.api.ArtifactRef;
import com.sshtools.bootlace.api.Exceptions.NotALayer;
import com.sshtools.bootlace.api.GAV;
import com.sshtools.bootlace.api.Http.HttpClientFactory;
import com.sshtools.bootlace.api.LayerArtifacts;
import com.sshtools.bootlace.api.Logs;
import com.sshtools.bootlace.api.Logs.BootLog;
import com.sshtools.bootlace.api.Logs.Log;
import com.sshtools.bootlace.api.PluginLayer;
import com.sshtools.bootlace.api.ResolutionMonitor;

public class LayerArtifactsImpl implements LayerArtifacts {

	private final static Log LOG = Logs.of(BootLog.LAYERS);

	private Set<ArtifactRef> artifactsToDo = new LinkedHashSet<>();
	private Set<ArtifactRef> artifactsDone = new LinkedHashSet<>();
	private Set<ArtifactRef> finalArtifactsDone = new LinkedHashSet<>();

	private PluginLayerImpl pluginLayerDef;
	private HttpClientFactory httpClientFactory;
	
	LayerArtifactsImpl(PluginLayerImpl pluginLayerDef, HttpClientFactory httpClientFactory) {
		this.pluginLayerDef = pluginLayerDef;
		this.httpClientFactory = httpClientFactory;
		
		artifactsToDo.addAll(pluginLayerDef.artifacts());
		
		try {
			expand();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
	
	@Override
	public PluginLayer layer() {
		return pluginLayerDef;
	}
	
	@Override
	public Set<ArtifactRef> artifacts() {
		return Collections.unmodifiableSet(finalArtifactsDone);
	}
	
	@Override
	public Set<Path> paths() {
		return Collections.unmodifiableSet(artifacts().stream().
				map(ArtifactRef::path).
				filter(Optional::isPresent).
				map(Optional::get).
				sorted((p1, p2) -> {
					var d1 = Files.isDirectory(p1);
					var d2 = Files.isDirectory(p2);
					if(d1 && !d2)
						return 1;
					else if(d2 && !d1) 
						return -1;
					else
						return p1.compareTo(p2);
				}).
				collect(Collectors.toSet()));
	}
	
	@Override
	public String toString() {
		return "LayerArtifactsImpl [artifactsToDo=" + artifactsToDo + ", artifactsDone=" + artifactsDone + "]";
	}

	private void expand() throws IOException {
		while(!artifactsToDo.isEmpty()) {
			var first = artifactsToDo.iterator().next();
			artifactsToDo.remove(first);
			artifactsDone.add(first);
			
			/* Is this a reference for a versioned artifact, where we already have a
			 * one with a path? Is so, skip
			 */
			if(first.gav().hasVersion()) {
				var skip = false;
				for(var have : finalArtifactsDone) {
					if(have.hasPath() && have.gav().toWithoutVersion().equals(first.gav().toWithoutVersion())) {
						skip = true;
						break;
					}
				}
				if(skip) {
					continue;
				}
			}
			
			
			var artifactFile = loadArtifact(first);
			finalArtifactsDone.add(first.withPath(artifactFile));
			
			try {
				var descriptor = new Descriptor.Builder().
					fromArtifact(artifactFile).
					build();
				
	
				LOG.info("Found contributed descriptor in {0}", artifactFile);
				
				processDescriptor(pluginLayerDef, descriptor);
			}
			catch(NotALayer nle) {
				if(LOG.debug())
					LOG.debug("Not a layer.", nle);
				
				/// TODO more??
				artifactsDone.add(first);
			}

		}
		
		optimizeArtifacts();
		
		
		if(LOG.debug()) {
			LOG.debug("Final list of artifacts for ''{0}'' now ''{1}''", pluginLayerDef.id(), String.join(", ", finalArtifactsDone.stream().map(ArtifactRef::toString).toList()));
		}
	}
	
	private void optimizeArtifacts() {
		finalArtifactsDone = finalArtifactsDone.stream().filter(art -> {
			return art.path().isPresent() || !isArtifactWithPathPresent(art.gav());
		}).collect(Collectors.toSet());
	}
	
	private boolean isArtifactWithPathPresent(GAV gav) {
		for(var art : finalArtifactsDone) {
			if(art.gav().equals(gav) && art.path().isPresent())
				return true;
		}
		return false;
	}
	
	private void addArtifactsIfNotDone(ArtifactRef ref) {
		if(!artifactsDone.contains(ref))
			artifactsToDo.add(ref);
	}
	
	private void processDescriptor(PluginLayerImpl pluginLayerDef, Descriptor descriptor) {
		/* TODO: This is all very similar to what happens in PluginLayerImpl. It 
		 * should be re-used
		 */
				
		var section = descriptor.componentSection();
		
		/* Override some other basic stuff if possible */
		if(section.contains("global"))
			pluginLayerDef.global = section.getBoolean("global");

		/* Add more repositories and child artifacts */
		var repos = Stream.concat(
				Arrays.asList(section.getAllOr("localRepository").orElse(new String[0])).stream(),
				Arrays.asList(section.getAllOr("localRepositories").orElse(new String[0])).stream()).toList();
		pluginLayerDef.localRepositories.addAll(repos);
		
		if(LOG.debug()) {
			LOG.debug("Local repositories for ''{0}'' now ''{1}''", descriptor.id(), String.join(", ", pluginLayerDef.localRepositories));
		}
		
		repos = Stream.concat(
				Arrays.asList(section.getAllOr("remoteRepository").orElse(new String[0])).stream(),
				Arrays.asList(section.getAllOr("remoteRepositories").orElse(new String[0])).stream()).toList();
		pluginLayerDef.remoteRepositories.addAll(repos);
		
		if(LOG.debug()) {
			LOG.debug("Remote repositories for ''{0}'' now ''{1}''", descriptor.id(), String.join(", ", pluginLayerDef.remoteRepositories));
		}
		
		
		repos = Stream.concat(
				Arrays.asList(section.getAllOr("appRepository").orElse(new String[0])).stream(),
				Arrays.asList(section.getAllOr("appRepositories").orElse(new String[0])).stream()).toList();
		pluginLayerDef.appRepositories.addAll(repos);
		
		if(LOG.debug()) {
			LOG.debug("App repositories for ''{0}'' now ''{1}''", descriptor.id(), String.join(", ", pluginLayerDef.appRepositories));
		}
		
		pluginLayerDef.parents.addAll(Stream.concat(
				Arrays.asList(section.getAllOr("parent").orElse(new String[0])).stream(),
				Arrays.asList(section.getAllOr("parents").orElse(new String[0])).stream()).toList());
		
		if(LOG.debug()) {
			LOG.debug("Parents for ''{0}'' now ''{1}''", descriptor.id(), String.join(", ", pluginLayerDef.parents));
		}
		
		var arts = descriptor.artifactsSection();
		arts.ifPresent(art -> { 
			art.values().forEach((k, v) -> {
				if(k.equals("*")) {
					pluginLayerDef.artifacts().forEach(defArt -> {
						Artifact.find(defArt).pom().dependencies().forEach(gav -> 
							addArtifactsIfNotDone(ArtifactRef.of(gav))
						);
					});
				}
				else if(v.length == 1) {
					addArtifactsIfNotDone(ArtifactRef.of(GAV.ofSpec(k), Paths.get(v[0])));
				}
				else if(v.length == 0) {
					addArtifactsIfNotDone(ArtifactRef.of(GAV.ofSpec(k)));
				}
			});
		});
		
		if(LOG.debug()) {
			LOG.debug("Artifacts for ''{0}'' now ''{1}''", descriptor.id(), String.join(", ", artifactsToDo.stream().map(ArtifactRef::toString).toList()));
		}
	}

	private Path loadArtifact(ArtifactRef ref) throws IOException {
		var gav = ref.gav();
		var monitor = pluginLayerDef.resolveMonitor();
		
		if(ref.path().isPresent()) {
			
			var path = ref.path().get();
			LOG.info("Loading {0} @ {1}", gav, path);
			if(!Files.exists(path)) {
				throw new IOException(MessageFormat.format("Path to artifact ''{0}'' of ''{1}'' does not exist. Local artifact paths are only intended for developer purposes, and would usually point to the class output directory from your compiler, e.g. target/classes or bin. These must exist.", gav, path));
			}
			monitor.ifPresent(m -> m.have(gav, path.toUri(), null));
			return path;
		}
		else {
			LOG.info("Loading {0}", gav);

			var found = false;
			var appRepositories = pluginLayerDef.resolveAppRepositories();
			var locals = pluginLayerDef.resolveLocalRepositories();
	
			if (appRepositories.isEmpty()) {
	
				LOG.debug("No app repository, just checking locals", gav);
	
				/* Check locals */
				if(locals.isEmpty()) {
					throw new IOException(MessageFormat.format("""
							Artifact ''{0}'' was not found, as there was neither an 'appRepository', 
							nor a 'localRepository' configured to be able to retrieve it. 
							Check your layers.ini for this layer. 
							""", gav));
				}
				
				for (var local : locals) {
					var localResult = local.resolve(httpClientFactory, gav);
					if (localResult.isPresent()) {
	
						var uri = localResult.get().uri();
						LOG.debug("Local repository resolved {0} to {1}", gav, uri);
	
						var path = Paths.get(uri);
						if (Files.exists(path)) {
							found = true;
							LOG.info("Found {0} @ {1}", gav, uri);
							monitor.ifPresent(m -> m.have(gav, path.toUri(), local));
							return path;
						}
					}
				}
				
				
				throw new IOException(MessageFormat.format("""
						Artifact ''{0}'' was not found, and could not be found by searching {1}
						static local repositories. An 'appRepository' was not configured
						either, so the artifact could not be downloaded from any remote
						repositories if there are any. Check your layers.ini for this layer. 
						""", gav, locals.size()));
			} else {
				for(var appRepository : appRepositories) {
	
					LOG.debug("App repository {0}", appRepository.id());
	
					var result = appRepository.resolve(httpClientFactory, gav);
					if (result.isPresent()) {
						var resolved = result.get().uri();
						var path = Paths.get(resolved);
						if (Files.exists(path)) {
							/* Have in app repository */
							var uri = path.toUri();
							LOG.info("Found {0} @ {1}", gav, uri);
							monitor.ifPresent(m -> m.have(gav, uri, appRepository));
							return path;
						} else {
							/* Check locals */
							for (var local : locals) {
								var localResult = local.resolve(httpClientFactory, gav);
								if (localResult.isPresent()) {
									var uri = localResult.get().uri();
	
									LOG.debug("Local repository resolved {0} to {1}", gav, uri);
	
									path = Paths.get(uri);
									if (Files.exists(path)) {
										LOG.info("Found {0} @ {1}", gav, uri);
										found = true;
										var uri2 = path.toUri();
										monitor.ifPresent(m -> m.have(gav, uri2, local));
										return path;
									}
								}
							}
	
						}
	
						if (!found) {
							LOG.info("{0}, will try remote repositories", gav);
							return downloadArtifact(gav, appRepository, monitor);
						}
					}
				}
				
				
				throw new IOException(MessageFormat.format("""
						Artifact ''{0}'' was not found, and could not be found by searching {1}
						application repositories   
						""", gav, appRepositories.size()));
			}
		}
	}

	private Path downloadArtifact(GAV gav, AppRepository appRepository,
			Optional<ResolutionMonitor> monitor) throws IOException {
		var remoteRepository = pluginLayerDef.resolveRemoteRepository(gav);
		var result = remoteRepository.resolve(httpClientFactory, gav);
		try {
			if (result.isPresent()) {
				var uri = result.get().uri();
				monitor.ifPresent(m -> m.need(gav, uri, remoteRepository));
				var downIn = remoteRepository.download(httpClientFactory, gav, uri, result.get(), monitor);
				if(monitor.isPresent()) {
					downIn = new FilterInputStream(downIn) {
						private long total = 0;
						
						@Override
						public int read(byte[] b, int off, int len) throws IOException {
							var r = in.read(b, off, len);
							if(r > -1) {
								total += r;
							}
							monitor.get().downloading(gav, uri, remoteRepository, Optional.of(total));
							return r;
						}
					};
				}
				try (var in = downIn) {
					var path = appRepository.store(gav, in);
					monitor.ifPresent(m -> m.downloaded(gav, uri, remoteRepository));
					return path;
				}
			} else {
				throw new NoSuchFileException(gav.toString());
			}
		} catch (RuntimeException | IOException e) {
			monitor.ifPresent(m -> m.failed(gav, gav.toString(), remoteRepository, e));
			throw e;
		}
	}
}
