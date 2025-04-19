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

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolverException;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResult;
import org.apache.maven.shared.transfer.dependencies.DefaultDependableCoordinate;
import org.apache.maven.shared.transfer.dependencies.resolve.DependencyResolverException;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;
import org.sonatype.inject.Description;

import com.sshtools.jini.INI;

/**
 * Generates a bootlace plugin from the current project
 */
@Mojo(threadSafe = true, name = "generate-plugin", requiresProject = true, defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyResolution = ResolutionScope.RUNTIME, requiresDependencyCollection = ResolutionScope.RUNTIME)
@Description("Generates a bootlace plugin from the current project")
public class BootlaceGeneratePluginMojo extends AbstractExtensionsMojo {

	protected static final String SEPARATOR = "/";
	
	/**
	 * The maven project.
	 * 
	 * @parameter expression="${project}"
	 * @required
	 * @readonly
	 */
	@Parameter(required = true, readonly = true, property = "project")
	protected MavenProject project;

	@Parameter(defaultValue = "true", property = "attach")
	private boolean attach = true;

	/**
	 * Additional artifacts to add to the plugin. A string of the form
	 * groupId:artifactId:version[:packaging[:classifier]].
	 */
	@Parameter(property = "bootlace.artifacts")
	private List<String> artifacts;

	@Component
	private MavenProjectHelper projectHelper;

	protected void onExecute() throws MojoExecutionException, MojoFailureException {
		var log = getLog();
		if (skipPoms && "pom".equals(project.getPackaging())) {
			log.info("Skipping POM project " + project.getName());
			return;
		}

		log.info(project.getBasedir().getAbsolutePath());
		log.info(project.getExecutionProject().getBasedir().getAbsolutePath());

		try {

			/*
			 * Download extra artifacts (e.g. platform specific jars for SWT, JavaFX etc
			 * without adding them to the primary POM
			 */
			for (var artifact : artifacts) {
				log.info("Getting " + artifact);

				var tokens = StringUtils.split(artifact, ":");
				if (tokens.length < 3 || tokens.length > 5) {
					throw new MojoFailureException("Invalid artifact, you must specify "
							+ "groupId:artifactId:version[:packaging[:classifier]] " + artifact);
				}

				coordinate.setGroupId(tokens[0]);
				coordinate.setArtifactId(tokens[1]);
				coordinate.setVersion(tokens[2]);

				if (tokens.length >= 4) {
					coordinate.setType(tokens[3]);
				}
				if (tokens.length == 5) {
					coordinate.setClassifier(tokens[4]);
				}

				try {
					doCoordinate();
				} catch (MojoFailureException | DependencyResolverException | ArtifactResolverException e) {
					throw new MojoExecutionException("Failed to process an artifact.", e);
				}

				coordinate = new DefaultDependableCoordinate();
			}

			var storeTarget = new File(output, File.separator + project.getArtifactId() + "-" 
					+ project.getVersion() + "-bootlace.zip");

			storeTarget.getParentFile().mkdirs();

			Properties sourceProperties = new Properties();

			var filteredList = project.getArtifacts().stream().filter(a -> !"provided".equals(a.getScope()) || provided).toList();
			log.info("Adding " + filteredList.size() + " primary artifacts ");

			generateZip(sourceProperties, storeTarget, filteredList);

			if (attach) {
				log.info("Attaching artifact as bootlace zip");
				projectHelper.attachArtifact(project, "zip", "bootlace", storeTarget);
			}

		} catch (Exception e) {
			log.error(e);
			throw new MojoExecutionException("Unable to create dependencies file: " + e, e);
		}
	}

	protected void generateZip(Properties sourceProperties, File zipfile, List<Artifact> artifacts)
			throws IOException, FileNotFoundException {

		
		if(!isExtension(project.getArtifact().getFile())) {
			throw new IllegalArgumentException("Project is not an extension, a layers.ini resource must exist.");
		}
		
		
		/* We need to work out which artifacts are provided by other extensions, and
		 * to do so without using mavens "provided" scope. This is because provided
		 * prevents transitive dependencies being pulled in, meaning dependencies of
		 * other extensions have to explicitly to be added. This gets very boring
		 * very quickly. Being as layers.ini is required anyway, we use that instead.
		 * 
		 * We do this by going through all the dependent artifacts, and
		 * inspect its layers.ini for any dependencies and add them to a set. 
		 * 
		 * Then we build the zip, and include this projects artifact and any
		 * dependent artifacts that are not in the set. Of course, we do not include
		 * any artifacts the ARE extensions themselves, or any artifact that has a
		 * META-INF/BOOTLACE.provided resource.
		 */
		var inDependency = new HashSet<String>();
		for(var a : artifacts) {
			if (!isExclude(a) && isExtension(a.getFile())) {
				var layers = getLayers(a.getFile());
				layers.sectionOr("artifacts").ifPresent(lyr -> {
					for(var key : lyr.keys()) {
						inDependency.add(String.join(":", Arrays.asList(key.split(":")).subList(0,  2)));
					}
				});
			}
		}
		try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(zipfile))) {

			var art = project.getArtifact();
			Log log = getLog();
			log.info("Adding project artifact " + art.getFile().getName());

			var e = new ZipEntry(getOutName(art));
			zip.putNextEntry(e);

			try (FileInputStream fin = new FileInputStream(art.getFile())) {
				IOUtil.copy(fin, zip);
			}

			zip.closeEntry();
			var addedPaths = new HashSet<String>();

			for (var a : artifacts) {

				var artifactKey = makeKey(a);
				File resolvedFile = null;
				String outName = null;

				if(artifactKey.equals("com.sshtools:jini-lib")) {
					/* TODO this is unfortunate. We need to find a way to hide this from child layers,
					 *  or not use it at all so bootlacec-platform has zero dependencies
					 */
					log.info("Artifact " + artifactKey + " is a bootlace-platform dependency, skipping");
					continue;
				}
				else if(inDependency.contains(artifactKey)) {
					log.info("Artifact " + artifactKey + " is provided as a dependency of an a extension");
					continue;
				}
				else if (isExclude(a)) {
					log.info("Artifact " + artifactKey + " is excluded");
					continue;
				} else {
					log.info("Artifact " + artifactKey + " is an extra");
					resolvedFile = a.getFile();
				}

				outName = makeFilename(a);

				if (!resolvedFile.exists()) {
					log.warn(resolvedFile.getAbsolutePath() + " does not exist!");
					continue;
				}
				if (resolvedFile.isDirectory()) {
					log.warn(resolvedFile.getAbsolutePath() + " is a directory");
					resolvedFile = a.getFile();
				}
				
				if(isExtensionOrBootlaceProvided(resolvedFile)) {
					log.info("Artifact " + artifactKey + " is an extension, not adding");
					continue;
				}

				log.info("Adding " + outName + " to plugin zip");

				var path = outName;

				if (addedPaths.contains(path)) {
					log.info("Already added " + path);
					continue;
				}

				addedPaths.add(path);

				e = new ZipEntry(path);

				zip.putNextEntry(e);

				try (FileInputStream fin = new FileInputStream(resolvedFile)) {
					IOUtil.copy(fin, zip);
				}

				zip.closeEntry();

			}

		}
	}

	private INI getLayers(File file) {
		if(file.isDirectory()) {
			return INI.fromFile(new File(file, "layers.ini").toPath());
		}
		else {
			try(var jf = new JarFile(file)) {
				var je = jf.getEntry("layers.ini");
				if(je == null)
					throw new IOException("No layers.ini in " + file);
				try(var ji = jf.getInputStream(je)) {
					return INI.fromInput(ji);
				}
			}
			catch(IOException ioe) {
				throw new UncheckedIOException(ioe);
			}
		}
	}

	private boolean isExtension(File resolvedFile) {
		return new File(resolvedFile, "layers.ini").exists() || isArtifactContains(resolvedFile, "layers.ini");
	}

	private boolean isExtensionOrBootlaceProvided(File resolvedFile) {
		return isArtifactContains(resolvedFile, "layers.ini", "META-INF/BOOTLACE.provided");
	}

	private boolean isArtifactContains(File resolvedFile, String... entryNames) {
		try(var jf = new JarFile(resolvedFile)) {
			for(var n : entryNames) {
				if(jf.getEntry(n) != null) {
					return true;
				}
			}
			return false;
		}
		catch(IOException ioe) {
			throw new UncheckedIOException(ioe);
		}
	}

	private String getOutName(Artifact art) {
		if (art.getClassifier() == null || art.getClassifier().length() == 0)
			return art.getGroupId() + "-" + art.getArtifactId() + "-" + art.getVersion() + "." + art.getType();
		else
			return art.getGroupId() + "-" + art.getArtifactId() + "-" + art.getVersion() + "-" + art.getClassifier() + "." + art.getType();
	}

	@Override
	protected void doHandleResult(ArtifactResult result)
			throws MojoExecutionException, DependencyResolverException, ArtifactResolverException, IOException {
		/* Only used for extra artifacts */
	}

	@Override
	protected boolean isSnapshotVersionAsBuildNumber() {
		return false;
	}

	public static String makeFilename(Artifact a) {
		if(a.getClassifier() == null || a.getClassifier().equals(""))
			return a.getGroupId() + ":" + a.getArtifactId() + ":" + a.getVersion() + "." + a.getType();
		else
			return a.getGroupId() + ":" + a.getArtifactId() + ":" + a.getVersion() + ":" + a.getClassifier() + "." + a.getType();
	}

	public static String makeKey(Artifact a) {
		if (a.getClassifier() == null || a.getClassifier().equals(""))
			return a.getGroupId() + ":" + a.getArtifactId();
		else
			return a.getGroupId() + ":" + a.getArtifactId() + ":" + a.getClassifier();
	}
}