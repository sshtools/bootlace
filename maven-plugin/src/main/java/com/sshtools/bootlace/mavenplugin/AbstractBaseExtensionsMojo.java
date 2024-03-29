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

import java.io.IOException;
import java.util.jar.JarFile;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

public abstract class AbstractBaseExtensionsMojo extends AbstractMojo {

	/**
	 * The maven project.
	 */
	@Parameter(required = true, readonly = true, property = "project")
	protected MavenProject project;

	@Parameter(defaultValue = "true")
	protected boolean skipPoms;

	@Parameter(defaultValue = "false")
	protected boolean skip;
	
	protected final boolean isSkipPoms() {
		return skipPoms;
	}

	@Override
	public final void execute() throws MojoExecutionException, MojoFailureException {
		if(!skip && ( !isSkipPoms() || ( isSkipPoms() && ( project == null || !project.getPackaging().equals("pom"))))) {
			onExecute();
		}
		else
			getLog().info(String.format("Skipping %s, it is a POM and we are configured to skip these.", project.getArtifact().getArtifactId()));
	}

	protected void onExecute() throws MojoExecutionException, MojoFailureException {
		
	}

	protected String getArtifactVersion(Artifact artifact) {
		return getArtifactVersion(artifact, true);
	}
	
	protected String getArtifactVersion(Artifact artifact, boolean processSnapshotVersions) {
		String v = artifact.getVersion();
		if (artifact.isSnapshot()) {
			return getVersion(processSnapshotVersions, v);
		} else
			return v;
	}

	protected boolean isJarExtension(Artifact artifact) {
		if ("jar".equals(artifact.getType())) {
			try (JarFile jarFile = new JarFile(artifact.getFile())) {
				if (jarFile.getEntry("extension.def") != null) {
					return true;
				}
			} catch (IOException ioe) {
				throw new IllegalStateException("Failed to test for extension jar.", ioe);
			}
		}
		return false;
	}

	protected String getVersion(boolean processTimestampedSnapshotVersions, String v) {
		if (v.contains("-SNAPSHOT"))
			return v.substring(0, v.indexOf("-SNAPSHOT")) + "-" + getSnapshotVersionSuffix();
		else if(processTimestampedSnapshotVersions) {
			int idx = v.lastIndexOf("-");
			if (idx == -1) {
				return v;
			} else {
				idx = v.lastIndexOf(".", idx - 1);
				if (idx == -1)
					return v;
				else {
					idx = v.lastIndexOf("-", idx - 1);
					if (idx == -1)
						return v;
					else {
						return v.substring(0, idx) + "-" + getSnapshotVersionSuffix();
					}
				}
			}
		}
		else {
			return v;
		}
	}

	protected final String getSnapshotVersionSuffix() {
		if(isSnapshotVersionAsBuildNumber()) {
			String buildNumber = System.getenv("BUILD_NUMBER");
			if(buildNumber == null || buildNumber.equals(""))
				return "0";
			else
				return buildNumber;
		}
		else
			return "SNAPSHOT";
	}

	protected boolean isSnapshotVersionAsBuildNumber() {
		return false;
	}
}
