/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.maven.plugin.jdee;

import java.io.File;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

/**
 * Goal which clean all crated files and directories
 *
 * @goal clean
 * @phase process-sources
 */
public class CleanMojo extends AbstractMojo {

  /**
   * The Maven Project.
   *
   * @parameter expression="${project}"
   * @required
   * @readonly
   */
  protected MavenProject executedProject;

  /**
   * Location of the source directory.
   *
   * @parameter expression="${project.build.sourceDirectory}"
   * @required
   */
  private File sourceDirectory;

  /**
   * Test source directory
   *
   * @parameter expression="${project.build.testSourceDirectory}"
   * @required
   */
  private File testSourceDirectory;

  /** Methode which execute goal.
   * <p>Delete files src/main/java/prj.el, src/test/java/prj.el and xref.data
   * .maven-emacs-plugin-cache directory</p>
   * @todo delete xref.data
   * @throws MojoExecutionException some exception raise
   */
  public void execute() throws MojoExecutionException {
    File f = new File(sourceDirectory, "prj.el");
    f.delete();
    f = new File(testSourceDirectory, "prj.el");
    f.delete();
    f = new File(executedProject.getBasedir(), "xref.data");
    f.delete();
    f = new File(executedProject.getBasedir() + File.separator
                 + ".maven-emacs-plugin-cache" + File.separator);
    if (f.exists()) {
      FileUtil.deleteDirectory(f);
    }
  }
}
