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

			if (skipPoms && project != null && "pom".equals(project.getPackaging())) {
				getLog().info("Skipping POM project " + project.getName());
				return;
			}
			
			onExecute();
		}
		else
			getLog().info(String.format("Skipping %s per configuration.", project.getArtifact().getArtifactId()));
	}

	protected void onExecute() throws MojoExecutionException, MojoFailureException {
		
	}

}
