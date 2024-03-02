package com.sshtools.bootlace.platform;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import com.sshtools.bootlace.api.ArtifactRef;
import com.sshtools.bootlace.api.GAV;
import com.sshtools.bootlace.api.LayerArtifacts;
import com.sshtools.bootlace.api.PluginLayer;
import com.sshtools.bootlace.api.PluginRef;
import com.sshtools.bootlace.api.Zip;
import com.sshtools.jini.INI.Section;

public final class PluginLayerImpl extends AbstractChildLayer implements PluginLayer {
	public final static class Builder extends AbstractChildLayerBuilder<PluginLayerImpl.Builder> {
		private final Set<ArtifactRef> artifacts = new LinkedHashSet<>();

		public Builder(String id) {
			super(id);
		}

		public PluginLayerImpl build() {
			return new PluginLayerImpl(this);
		}

		public PluginLayerImpl.Builder withJarArtifactsDirectory(Path dir) {
			try (var str = Files.newDirectoryStream(dir,
						f -> f.getFileName().toString().toLowerCase().endsWith(".jar"))) {
				for (var jar : str) {
					withJarArtifacts(jar);
				}
			} catch (IOException ioe) {
				throw new UncheckedIOException(ioe);
			}
			return this;
		}
		
		public PluginLayerImpl.Builder withJarArtifacts(Path... paths) {
			Arrays.asList(paths).forEach((path) -> {
				try(var in = Files.newInputStream(path)) {
					withArtifactJar(in);
				} catch (IOException ioe) {
					throw new UncheckedIOException(ioe);
				}	
			});
			return this;
		}
		
		public PluginLayerImpl.Builder withArtifactJar(InputStream in) {
			try {
				var props = getMavenPropertiesForArtifact(in);
				withArtifactRefs(refFromProperties(props));
				return this;
			}
			catch(IOException ioe) {
				throw new UncheckedIOException(ioe);
			}
		}

		private ArtifactRef refFromProperties(Properties props) {
			return ArtifactRef.of(GAV.ofParts(
				props.getProperty("groupId"),
				props.getProperty("artifactId"),
				props.getProperty("version")
			));
		}

		private Properties getMavenPropertiesForArtifact(InputStream in) throws IOException {
			return Zip.unzip(in, (ze, is) -> {
				var p = new Properties();
				try {
					p.load(is);
					return Optional.of(p);
				}
				catch(IOException ioe) {
					throw new UncheckedIOException(ioe);
				}
			}, ze -> ze.getName().matches(".*META-IF/maven/[^/]+/[^/]+/pom.properties")).orElseThrow(() -> new IllegalArgumentException("Artifact does not appear to be a Maven artifact, there is no Maven meta-data."));
		}

		public PluginLayerImpl.Builder withArtifactRefs(Collection<ArtifactRef> ref) {
			this.artifacts.addAll(ref);
			return this;
		}
		public PluginLayerImpl.Builder withArtifactRefs(ArtifactRef... refs) {
			return withArtifactRefs(Arrays.asList(refs));
		}

		public PluginLayerImpl.Builder withArtifacts(Collection<GAV> ref) {
			return withArtifactRefs(ref.stream().map(ArtifactRef::of).toList());
		}

		public PluginLayerImpl.Builder withArtifacts(GAV... gav) {
			return withArtifacts(Arrays.asList(gav));
		}

		public PluginLayerImpl.Builder withArtifacts(String... gav) {
			return withArtifacts(Arrays.asList(gav).stream().map((s) -> new GAV.Builder().ofSpec(s).build()).toList());
		}

		protected PluginLayerImpl.Builder fromArtifactsSection(Optional<Section> section) {
			section.ifPresent(this::fromArtifactsSection);
			return this;
			
		}
		
		protected PluginLayerImpl.Builder fromArtifactsSection(Section section) {
			section.values().forEach((k, v) -> {
				if(v.length == 1) {
					withArtifactRefs(ArtifactRef.of(GAV.ofSpec(k), Paths.get(v[0])));
				}
				else if(v.length == 0) {
					withArtifactRefs(ArtifactRef.of(GAV.ofSpec(k)));
				}
			});
			return this;
		}
	}

	protected final Set<ArtifactRef> artifacts;
	
	final List<PluginRef> pluginRefs = new ArrayList<>();
	Optional<LayerArtifacts> layerArtifacts = Optional.empty();

	PluginLayerImpl(PluginLayerImpl.Builder layerBuilder) {
		super(layerBuilder);
		artifacts = new LinkedHashSet<>(layerBuilder.artifacts);
	}

	@Override
	public List<PluginRef> pluginRefs() {
		return Collections.unmodifiableList(pluginRefs);
	}

	@Override
	public Optional<LayerArtifacts> finalArtifacts() {
		return layerArtifacts;
	}

	@Override
	public  Set<ArtifactRef> artifacts() {
		return artifacts;
	}

	@Override
	public String toString() {
		return "PluginLayer [id()=" + id() + ", name()=" + name() + ", global()=" + global() + ", parents()="
				+ parents() + ", appRepositories()=" + appRepositories() + ", localRepositories()="
				+ localRepositories() + ", remoteRepositories()=" + remoteRepositories() + ", artifacts=" + artifacts + "]";
	}

}