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
package com.sshtools.bootlace.mavenplugin;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.shared.transfer.artifact.ArtifactCoordinate;
import org.apache.maven.shared.transfer.artifact.DefaultArtifactCoordinate;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolver;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolverException;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResult;
import org.apache.maven.shared.transfer.dependencies.DefaultDependableCoordinate;
import org.apache.maven.shared.transfer.dependencies.DependableCoordinate;
import org.apache.maven.shared.transfer.dependencies.resolve.DependencyResolver;
import org.apache.maven.shared.transfer.dependencies.resolve.DependencyResolverException;
import org.codehaus.plexus.util.StringUtils;

import com.sshtools.jini.INI;
import com.sshtools.jini.INIParseException;
import com.sshtools.jini.INIReader;
import com.sshtools.jini.INIReader.DuplicateAction;
import com.sshtools.jini.INIReader.MultiValueMode;
import com.sshtools.jini.INIWriter;

/**
 * Abstract
 */
public abstract class AbstractExtensionsMojo extends AbstractBaseExtensionsMojo {

	public interface IORunnable {
		void run() throws IOException;
	}

	protected static final String EXTENSION_ARCHIVE = "bootlace";

	private static final Pattern ALT_REPO_SYNTAX_PATTERN = Pattern.compile("(.+)::(.*)::(.+)");

	@Parameter(defaultValue = "${session}", required = true, readonly = true)
	protected MavenSession session;

	@Parameter(defaultValue = "true", property = "provided")
	protected boolean provided = false;

	static Map<Artifact, Path> lastVersionProcessed = new HashMap<>();

	/**
	 *
	 */
	@Component
	protected ArtifactResolver artifactResolver;

	/**
	 *
	 */
	@Component
	protected DependencyResolver dependencyResolver;

	@Component
	protected ArtifactHandlerManager artifactHandlerManager;

	/**
	 * Map that contains the layouts.
	 */
	@Component(role = ArtifactRepositoryLayout.class)
	protected Map<String, ArtifactRepositoryLayout> repositoryLayouts;

	/**
	 * The repository system.
	 */
	@Component
	protected RepositorySystem repositorySystem;

	protected DefaultDependableCoordinate coordinate = new DefaultDependableCoordinate();

	/**
	 * Repositories in the format id::[layout]::url or just url, separated by comma.
	 * ie.
	 * central::default::https://repo.maven.apache.org/maven2,myrepo::::https://repo.acme.com,https://repo.acme2.com
	 */
	@Parameter(property = "bootlace.remoteRepositories")
	protected String remoteRepositories;

	/**
	 *
	 */
	@Parameter(defaultValue = "${project.remoteArtifactRepositories}", readonly = true, required = true)
	protected List<ArtifactRepository> pomRemoteRepositories;
	/**
	 *
	 */
	@Parameter(property = "bootlace.excludeClassifiers")
	protected List<String> excludeClassifiers;
	@Parameter(property = "bootlace.copyOncePerRuntime", defaultValue = "true")
	protected boolean copyOncePerRuntime = true;

	/**
	 * Which groups can contain extensions. This can massively speed up dependency
	 * by not needlessly contacting a Maven repository to determine if an artifact
	 * has a extension archive artifact as well (which it tries to do for ALL
	 * dependencies including 3rd party ones that will never has an extension
	 * archive). This provides a way to optimise this, as we only have a few group
	 * names that have extensions.
	 */
	@Parameter(property = "bootlace.groups")
	protected List<String> groups;

	/**
	 * Location of the file.
	 */
	@Parameter(defaultValue = "${project.build.directory}", property = "bootlace.output", required = true)
	protected File output;

	/**
	 * Skip plugin execution completely.
	 *
	 * @since 2.7
	 */
	@Parameter(property = "extensions.skip", defaultValue = "false")
	protected boolean skip;

	@Parameter(defaultValue = "true")
	protected boolean includeVersion;

	@Parameter(defaultValue = "true")
	protected boolean processSnapshotVersions;

	/**
	 * Download transitively, retrieving the specified artifact and all of its
	 * dependencies.
	 */
	@Parameter(property = "extensions.transitive", defaultValue = "true")
	protected boolean transitive = true;

	/**
	 * Download transitively, retrieving the specified artifact and all of its
	 * dependencies.
	 */
	@Parameter(property = "extensions.useRemoteRepositories", defaultValue = "true")
	protected boolean useRemoteRepositories = true;

	/**
	 * Update policy.
	 */
	@Parameter(property = "extensions.updatePolicy")
	private String updatePolicy;

	/**
	 * Update policy.
	 */
	@Parameter(property = "extensions.checksumPolicy")
	private String checksumPolicy;

	@Parameter(defaultValue = "true")
	protected boolean processExtensionVersions;

	protected Set<String> artifactsDone = new HashSet<>();

	public static String makeArtifactName(Artifact a) {
		return a.getGroupId() + ":" + a.getArtifactId() + ":" + a.getVersion();
	}

	public static String makeKey(Artifact a) {
		if (a.getClassifier() == null || a.getClassifier().equals(""))
			return a.getGroupId() + ":" + a.getArtifactId();
		else
			return a.getGroupId() + ":" + a.getArtifactId() + ":" + a.getClassifier();
	}

	public static String makeOutName(Artifact art) {
		if (art.getClassifier() == null || art.getClassifier().length() == 0)
			return art.getGroupId() + "-" + art.getArtifactId() + "-" + art.getVersion() + "." + art.getType();
		else
			return art.getGroupId() + "-" + art.getArtifactId() + "-" + art.getVersion() + "-" + art.getClassifier()
					+ "." + art.getType();
	}

	public static String makeFilename(Artifact a) {
		if (a.getClassifier() == null || a.getClassifier().equals(""))
			return a.getGroupId() + ":" + a.getArtifactId() + ":" + a.getVersion() + "." + a.getType();
		else
			return a.getGroupId() + ":" + a.getArtifactId() + ":" + a.getVersion() + ":" + a.getClassifier() + "."
					+ a.getType();
	}

	private void handleResult(ArtifactResult result)
			throws MojoExecutionException, DependencyResolverException, ArtifactResolverException {

		Artifact artifact = result.getArtifact();
		String id = toCoords(artifact);

		if (isExclude(artifact)) {
			getLog().info(String.format("Skipping %s because it's classifier is excluded.", id));
			return;
		}

		if (artifactsDone.contains(id))
			return;
		else
			artifactsDone.add(id);
		try {
			doHandleResult(result);
		} catch (IOException e) {
			throw new MojoExecutionException("Failed to handle.", e);
		}
	}

	protected ArrayList<Artifact> getFilteredDependencies() {
		var filteredList = new ArrayList<>(
				project.getArtifacts().stream().filter(a -> !"provided".equals(a.getScope()) || provided).toList());
		getLog().info("Adding " + filteredList.size() + " primary artifacts ");
		return filteredList;
	}

	protected boolean isExclude(Artifact artifact) {
		return artifact != null && artifact.getClassifier() != null && artifact.getClassifier().length() > 0
				&& excludeClassifiers != null && excludeClassifiers.contains(artifact.getClassifier());
	}

	protected boolean isProcessedGroup(Artifact artifact) {
		if (groups == null || groups.isEmpty()) {
			return true;
		} else
			return groups.contains(artifact.getGroupId());
	}

	protected abstract void doHandleResult(ArtifactResult result)
			throws MojoExecutionException, DependencyResolverException, ArtifactResolverException, IOException;

	protected void doCoordinate() throws MojoFailureException, MojoExecutionException, IllegalArgumentException,
			DependencyResolverException, ArtifactResolverException {

		var always = new ArtifactRepositoryPolicy(true,
				updatePolicy == null ? ArtifactRepositoryPolicy.UPDATE_POLICY_INTERVAL + ":60" : updatePolicy,
				checksumPolicy == null ? ArtifactRepositoryPolicy.CHECKSUM_POLICY_IGNORE : checksumPolicy);
//		ArtifactRepositoryPolicy always = new ArtifactRepositoryPolicy(true,
//				updatePolicy == null ? ArtifactRepositoryPolicy.UPDATE_POLICY_NEVER : updatePolicy,
//				checksumPolicy == null ? ArtifactRepositoryPolicy.CHECKSUM_POLICY_IGNORE : checksumPolicy);
		var repoList = new ArrayList<ArtifactRepository>();

		if (pomRemoteRepositories != null && useRemoteRepositories) {
			repoList.addAll(pomRemoteRepositories);
		}

		if (remoteRepositories != null) {
			// Use the same format as in the deploy plugin id::layout::url
			String[] repos = StringUtils.split(remoteRepositories, ",");
			for (String repo : repos) {
				repoList.add(parseRepository(repo, always));
			}
		}

		var buildingRequest = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());

		var settings = session.getSettings();
		repositorySystem.injectMirror(repoList, settings.getMirrors());
		repositorySystem.injectProxy(repoList, settings.getProxies());
		repositorySystem.injectAuthentication(repoList, settings.getServers());

		buildingRequest.setRemoteRepositories(repoList);

		var log = getLog();
		if (transitive) {
			log.debug("Resolving " + coordinate + " with transitive dependencies");
			for (ArtifactResult result : dependencyResolver.resolveDependencies(buildingRequest, coordinate, null)) {

				if ("provided".equals(result.getArtifact().getScope()) && !provided) {
					log.debug("Skipping provided dependency " + coordinate);
					continue;
				}

				/*
				 * If the coordinate is for an extension zip, then we only we transitive
				 * dependencies that also have an extension zip
				 */
				if (EXTENSION_ARCHIVE.equals(coordinate.getClassifier())) {
					if (isProcessedGroup(result.getArtifact())) {
						log.debug("Resolving " + toCoords(result.getArtifact()) + " with transitive dependencies");
						try {
							handleResult(artifactResolver.resolveArtifact(buildingRequest,
									toExtensionCoordinate(result.getArtifact())));
						} catch (ArtifactResolverException arfe) {
							log.debug("Failed to resolve " + result.getArtifact().getArtifactId()
									+ " as an extension, assuming it isn't one");
						}
					}
				} else {
					handleResult(result);
				}
			}
		} else {
			log.debug("Resolving " + coordinate);
			handleResult(artifactResolver.resolveArtifact(buildingRequest, toArtifactCoordinate(coordinate)));
		}

	}

	protected boolean isArtifactContains(File resolvedFile, String... entryNames) {
		if (resolvedFile.exists()) {
			if(resolvedFile.isDirectory()) {
				for (var n : entryNames) {
					if (new File(resolvedFile, n).exists()) {
						return true;
					}
				}
			}
			else {
				try (var jf = new JarFile(resolvedFile)) {
					for (var n : entryNames) {
						if (jf.getEntry(n) != null) {
							return true;
						}
					}
				} catch (IOException ioe) {
					throw new UncheckedIOException(ioe);
				}
			}
		}
		return false;
	}

	protected INI getLayers(File file) {
		try {
			if (file.isDirectory()) {
				return createINIReader().build().read(new File(file, "layers.ini").toPath());
			} else {
				try (var jf = new JarFile(file)) {
					var je = jf.getEntry("layers.ini");
					if (je == null)
						throw new IOException("No layers.ini in " + file);
					try (var ji = jf.getInputStream(je)) {
						return createINIReader().build().read(ji);
					}
				}
			}
		} catch (IOException ioe) {
			throw new UncheckedIOException(ioe);
		} catch (INIParseException e) {
			throw new IllegalArgumentException("Failed to parse layers.ini.", e);
		}
	}

	protected boolean isExtension(File resolvedFile) {
		return new File(resolvedFile, "layers.ini").exists() || isArtifactContains(resolvedFile, "layers.ini");
	}

	protected List<Artifact> getExtensions(List<Artifact> artifacts) {
		var l = new ArrayList<Artifact>();
		for (var a : artifacts) {
			if (!isExclude(a) && isExtension(a.getFile())) {
				l.add(a);
			}
		}
		return l;
	}

	protected void calcDependencyTypes(List<Artifact> artifacts, Set<String> inDependency,
			ArrayList<Artifact> extensions) {
		var log = getLog();

		/*
		 * We need to work out which artifacts are provided by other extensions, and to
		 * do so without using mavens "provided" scope. This is because provided
		 * prevents transitive dependencies being pulled in, meaning dependencies of
		 * other extensions have to explicitly to be added. This gets very boring very
		 * quickly.
		 * 
		 * There are two ways this is done.
		 * 
		 * Being as layers.ini is required anyway, we use that instead. We do this by
		 * going through all the dependent artifacts, and inspect its layers.ini for any
		 * dependencies and add them to a set.
		 * 
		 * We can also then check the Maven provider dependency trail. If the trail
		 * contains any artifact we know is an extension, we can exclude that as well.
		 * 
		 * Then we build the zip, and include this projects artifact and any dependent
		 * artifacts that are not in the set. Of course, we do not include any artifacts
		 * the ARE extensions themselves, or any artifact that has a
		 * META-INF/BOOTLACE.provided resource.
		 */
		extensions.addAll(getExtensions(artifacts));
		for (var a : extensions) {
			log.info("Adding  " + a + " as extension");
			var layers = getLayers(a.getFile());
			layers.sectionOr("artifacts").ifPresent(lyr -> {
				for (var key : lyr.keys()) {
					inDependency.add(String.join(":", Arrays.asList(key.split(":")).subList(0, 2)));
				}
			});
		}

		log.info("Dep trails ...");
		for (var art : artifacts) {
			if (isTransientDependencyOfExtension(art, extensions)) {
				inDependency.add(makeKey(art));
			}
		}
	}

	protected boolean isTransientDependencyOfExtension(Artifact artifact, List<Artifact> extensions) {

		if (artifact.equals(project.getArtifact())) {
			return false;
		}

		var extensionsInTrail = new LinkedHashSet<Artifact>();

		for (var dep : artifact.getDependencyTrail()) {
			var gav = dep.split(":");
			var frst = extensions.stream()
					.filter(f -> f.getGroupId().equals(gav[0]) && f.getArtifactId().equals(gav[1])).findFirst();
			if (frst.isPresent()) {
				extensionsInTrail.add(artifact);
			}
		}

		/*
		 * If any artifact in the dependency trail is an extension, then its a match
		 * except if the artifact is ONLY a transitive dependency of this project
		 * extension, not of any others
		 */
		if (extensionsInTrail.isEmpty()) {
			getLog().info("   " + artifact + " dependency trail  contains no extensions at all");
			return false;
		} else if (extensionsInTrail.size() == 1 && extensionsInTrail.iterator().next().equals(project.getArtifact())) {
			getLog().info("   " + artifact + " should be included by its only a dependency of the project artifiact");
			return false;
		} else {
			getLog().info("   " + artifact + " should be removed, its a transitive dependency");
			return true;
		}
	}

	private String toCoords(Artifact artifact) {
		return artifact.getArtifactId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion()
				+ (artifact.getClassifier() == null ? "" : ":" + artifact.getClassifier());
	}

	protected Path checkDir(Path resolve) {
		if (!Files.exists(resolve)) {
			try {
				Files.createDirectories(resolve);
			} catch (IOException e) {
				throw new IllegalStateException(String.format("Failed to create %s.", resolve));
			}
		}
		return resolve;
	}

	protected ArtifactRepository parseRepository(String repo, ArtifactRepositoryPolicy policy)
			throws MojoFailureException {
		// if it's a simple url
		String id = "temp";
		ArtifactRepositoryLayout layout = getLayout("default");
		String url = repo;

		// if it's an extended repo URL of the form id::layout::url
		if (repo.contains("::")) {
			Matcher matcher = ALT_REPO_SYNTAX_PATTERN.matcher(repo);
			if (!matcher.matches()) {
				throw new MojoFailureException(repo, "Invalid syntax for repository: " + repo,
						"Invalid syntax for repository. Use \"id::layout::url\" or \"URL\".");
			}

			id = matcher.group(1).trim();
			if (!StringUtils.isEmpty(matcher.group(2))) {
				layout = getLayout(matcher.group(2).trim());
			}
			url = matcher.group(3).trim();
		}
		return new MavenArtifactRepository(id, url, layout, policy, policy);
	}

	protected ArtifactCoordinate toExtensionCoordinate(Artifact art) {
		ArtifactHandler artifactHandler = artifactHandlerManager.getArtifactHandler("zip");
		DefaultArtifactCoordinate artifactCoordinate = new DefaultArtifactCoordinate();
		artifactCoordinate.setGroupId(art.getGroupId());
		artifactCoordinate.setArtifactId(art.getArtifactId());
		artifactCoordinate.setVersion(art.getVersion());
		artifactCoordinate.setClassifier(EXTENSION_ARCHIVE);
		artifactCoordinate.setExtension(artifactHandler.getExtension());
		return artifactCoordinate;
	}

	protected ArtifactCoordinate toArtifactCoordinate(DependableCoordinate dependableCoordinate) {
		var artifactHandler = artifactHandlerManager.getArtifactHandler(dependableCoordinate.getType());
		var artifactCoordinate = new DefaultArtifactCoordinate();
		artifactCoordinate.setGroupId(dependableCoordinate.getGroupId());
		artifactCoordinate.setArtifactId(dependableCoordinate.getArtifactId());
		artifactCoordinate.setVersion(dependableCoordinate.getVersion());
		artifactCoordinate.setClassifier(dependableCoordinate.getClassifier());
		artifactCoordinate.setExtension(artifactHandler.getExtension());
		return artifactCoordinate;
	}

	protected boolean matches(Artifact a, String key) {
		var args = key.split(":");
		if (args.length == 2) {
			return (args[0].equals("") || a.getGroupId().equals(args[0]))
					&& (args[1].equals("") || a.getArtifactId().equals(args[1]));
		} else if (args.length == 3) {
			return (args[0].equals("") || a.getGroupId().equals(args[0]))
					&& (args[1].equals("") || a.getArtifactId().equals(args[1]))
					&& (args[2].equals("") || args[2].equals(a.getClassifier()));
		}
		return a.getArtifactId().equals(key);
	}

	protected ArtifactRepositoryLayout getLayout(String id) throws MojoFailureException {
		var layout = repositoryLayouts.get(id);

		if (layout == null) {
			throw new MojoFailureException(id, "Invalid repository layout", "Invalid repository layout: " + id);
		}

		return layout;
	}

	/**
	 * @return {@link #skip}
	 */
	protected boolean isSkip() {
		return skip;
	}

	public static Path newFile(Path destinationDir, ZipEntry zipEntry) throws IOException {
		var destFile = destinationDir.resolve(zipEntry.getName());

		var destDirPath = destinationDir.toFile().getCanonicalPath();
		var destFilePath = destFile.toFile().getCanonicalPath();

		if (!destFilePath.startsWith(destDirPath + File.separator)) {
			throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
		}

		return destFile;
	}

	static INIReader.Builder createINIReader() {
		return new INIReader.Builder().withMultiValueMode(MultiValueMode.SEPARATED)
				.withDuplicateKeysAction(DuplicateAction.APPEND);
	}

	static INIWriter.Builder createINIWriter() {
		return new INIWriter.Builder().withMultiValueMode(MultiValueMode.SEPARATED);
	}
}
