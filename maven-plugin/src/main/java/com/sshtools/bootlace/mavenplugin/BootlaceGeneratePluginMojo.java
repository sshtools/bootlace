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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
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

				if (isExclude(a)) {
					log.info("Artifact " + artifactKey + " is excluded");
					continue;
				} else {
					log.info("Artifact " + artifactKey + " is an extra");
					resolvedFile = a.getFile();
				}

				outName = resolvedFile.getName();

				if (!resolvedFile.exists()) {
					log.warn(resolvedFile.getAbsolutePath() + " does not exist!");
					continue;
				}
				if (resolvedFile.isDirectory()) {
					log.warn(resolvedFile.getAbsolutePath() + " is a directory");
					resolvedFile = a.getFile();
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

	public static String makeKey(Artifact a) {
		if (a.getClassifier() == null || a.getClassifier().equals(""))
			return a.getGroupId() + "/" + a.getArtifactId();
		else
			return a.getGroupId() + "/" + a.getArtifactId() + ":" + a.getClassifier();
	}
}