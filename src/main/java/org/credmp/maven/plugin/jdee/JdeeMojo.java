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

import java.io.DataInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactCollector;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.resolver.ResolutionNode;
import org.apache.maven.artifact.resolver.WarningResolutionListener;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ExcludesArtifactFilter;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.ReportPlugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.jdee.support.AsynchronousProcess;
import org.apache.maven.plugin.jdee.support.ClassBrowser;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.artifact.InvalidDependencyVersionException;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * Goal which make prj.el files in src/main/java and src/main/test,
 * uncompress javadoc and source in cache directory and make xref.data file
 *
 * @author Arjen Wiersma <arjenw@gmail.com>
 * @author Lukas Benda
 * @since 1.0
 * @version 1.2.2
 * @goal jdee
 * @phase process-sources
 */
public class JdeeMojo extends AbstractMojo {

    /**
     * The Maven Project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    protected MavenProject executedProject;

    /**
     * Artifact factory, needed to download source jars for inclusion in classpath.
     *
     * @component role="org.apache.maven.artifact.factory.ArtifactFactory"
     * @required
     * @readonly
     */
    protected ArtifactFactory artifactFactory;

    /**
     * Artifact Id
     *
     * @parameter expression="${project.artifactId}"
     * @required
     */
    private String artifactId;

    /**
     * Artifact collector, needed to resolve dependencies.
     *
     * @component role="org.apache.maven.artifact.resolver.ArtifactCollector"
     * @required
     * @readonly
     */
    protected ArtifactCollector artifactCollector;

    /**
     * Location of the source directory.
     *
     * @parameter expression="${project.build.sourceDirectory}"
     * @required
     */
    private File sourceDirectory;

    /**
     * Build directory
     *
     * @parameter expression="${project.build.outputDirectory}"
     * @required
     */
    private File buildDirectory;

    /**
     * Test source directory
     *
     * @parameter expression="${project.build.testSourceDirectory}"
     * @required
     */
    private File testSourceDirectory;

    /**
     * Test output directory
     *
     * @parameter expression="${project.build.testOutputDirectory}"
     * @required
     */
    private File testBuildDirectory;

    /**
     * Dependencies
     *
     * @parameter expression="${project.dependencies}"
     * @required
     */
    private List dependencies;

    /**
     * Remote repositories which will be searched for source attachments.
     *
     * @parameter expression="${project.remoteArtifactRepositories}"
     * @required
     * @readonly
     */
    protected List remoteArtifactRepositories;

    /**
     * Local repos
     *
     * @parameter expression="${localRepository}"
     * @required
     */
    private ArtifactRepository localRepo;

    /**
     * Artifact resolver, needed to download source jars for inclusion in classpath.
     *
     * @component role="org.apache.maven.artifact.resolver.ArtifactResolver"
     * @required
     * @readonly
     */
    protected ArtifactResolver artifactResolver;

    /**
     * version of project
     *
     * @parameter expression="${project.version}"
     * @required
     */
    private String projectVersion;

    /**
     * @component role="org.apache.maven.artifact.metadata.ArtifactMetadataSource" hint="maven"
     */
    protected ArtifactMetadataSource artifactMetadataSource;

    private static final String VERSION = "1.2.2";

    /**
     * Path to global cache. To this path will be uncopress source files and
     * javadoc files which is downloaded from maven repositories. If is not set
     * then will be user .maven-emacs-plugin-cache directory in baseDir of
     * project
     *
     * @parameter expression="${globalCachePath}"
     */
    private String globalCachePath;

    /**
     * Set value of jde-project-file-version by version of maven project.
     * Default true
     *
     * @parameter expression="${jdeeProjectFileVersion}" default-value="true"
     */
    private boolean jdeeProjectFileVersion;

    /**
     * Switcher which enable or disable create file xref.data default fals
     *
     * @parameter expression="${makeXref}"
     */
    private boolean makeXref;

    /**
     * Enables/disables the downloading of source attachments. Defaults to false.
     * When this flag is <code>true</code> remote repositories are checked for
     * sources: in order to avoid repeated check for unavailable source archives,
     * a status cache is mantained into the target dir of the root project. Run
     * <code>mvn:clean</code> or delete the file
     * <code>mvn-eclipse-cache.properties</code> in order to reset this cache.
     *
     * @parameter expression="${downloadSources}"
     */
    private boolean downloadSources;

    /**
     * Enable/disable to set jde-depend-sourcepath variable
     *
     * @parameter expression="${dependSources}"
     */
    private boolean dependSources;

    /**
     * Enables/disables the downloading of source attachments. Defaults to false.
     * When this flag is <code>true</code> remote repositories are checked for
     * sources: in order to avoid repeated check for unavailable source archives,
     * a status cache is mantained into the target dir of the root project. Run
     * <code>mvn:clean</code> or delete the file
     * <code>mvn-eclipse-cache.properties</code> in order to reset this cache.
     *
     * @parameter expression="${downloadJavadocs}"
     */
    private boolean downloadJavadocs;

    /**
     * Enable/disable to set jde-help-docsets variable
     *
     * @parameter expression="${dependJavadocs}" default-value="true"
     */
    private boolean dependJavadocs;

    /**
     * Enable/disable the generating jacadoc from source if javadoc attachments
     * missing in repositorie
     *
     * @parameter expression="${generateMissingJavadoc}" default-value="true"
     */
    private boolean generateMissingJavadoc;

    /**
     * Directory to java home
     *
     * @parameter expression="${java.home}"
     */
    private String javaHome;

    /**
     * Default goal
     *
     * @parameter expression="${defaultGoal}"
     */
    private String defaultGoal;

    /**
     * Default profile
     *
     * @parameter expression="${defaultProfile}"
     */
    private String defaultProfile;

    /**
     * Default arguments
     *
     * @parameter expression="${defaultArguments}"
     */
    private String defaultArguments;

    /**
     * Default test goal. default is surefire:test
     *
     * @parameter expression="${testGoal}" default-value="surefire:test"
     */
    private String testGoal;

    /**
     * Default test profile.
     *
     * @parameter expression="${testProfile}"
     */
    private String testProfile;

    /**
     * Default test arguments
     *
     * @parameter expression="${testArguments}"
     */
    private String testArguments;

    /**
     * Inform if will be write to prj.el recreate command
     *
     * @parameter expression="${noRecreate}
     */
    private boolean noRecreate;

    private Set getProjectArtifacts() throws InvalidVersionSpecificationException {
        Set artifacts = new HashSet();

        for (Iterator dependencies = executedProject.getDependencies().iterator(); dependencies.hasNext();) {
            Dependency dep = (Dependency) dependencies.next();
            
            String groupId = dep.getGroupId();
            String artifactId = dep.getArtifactId();
            VersionRange versionRange = VersionRange.createFromVersionSpec(dep.getVersion());
            String type = dep.getType();
            
            if (type == null) {
                type = "jar";
            }
            
            String classifier = dep.getClassifier();
            boolean optional = dep.isOptional();
            String scope = dep.getScope();
            
            if (scope == null) {
                scope = Artifact.SCOPE_COMPILE;
            }

            Artifact artifact = artifactFactory.createDependencyArtifact(groupId, artifactId,
                                                                         versionRange, type,
                                                                         classifier,
                                                                         scope, optional);

            if (scope.equalsIgnoreCase(Artifact.SCOPE_SYSTEM)) {
                artifact.setFile(new File(dep.getSystemPath()));
            }

            List exclusions = new ArrayList();
            for (Iterator j = dep.getExclusions().iterator(); j.hasNext();) {
                Exclusion e = (Exclusion) j.next();
                exclusions.add(e.getGroupId() + ":" + e.getArtifactId());
            }

            ArtifactFilter newFilter = new ExcludesArtifactFilter(exclusions);

            artifact.setDependencyFilter(newFilter);

            artifacts.add(artifact);
        }

        return artifacts;
    }

    private Map createManagedVersionMap(ArtifactFactory artifactFactory, String projectId, DependencyManagement dependencyManagement)
        throws ProjectBuildingException {
        Map map;
        if (dependencyManagement != null && dependencyManagement.getDependencies() != null) {
            map = new HashMap();
            for (Iterator i = dependencyManagement.getDependencies().iterator(); i.hasNext();) {
                Dependency d = (Dependency) i.next();

                try {
                    VersionRange versionRange = VersionRange.createFromVersionSpec(d.getVersion());
                    Artifact artifact = artifactFactory .createDependencyArtifact(d.getGroupId(), d.getArtifactId(),
                                                                                  versionRange, d.getType(),
                                                                                  d.getClassifier(), d.getScope(),
                                                                                  d.isOptional());
                    map.put(d.getManagementKey(), artifact);
                }
                catch (InvalidVersionSpecificationException e) {
                    throw new ProjectBuildingException(projectId, "Unable to parse version '"
                                                       + d.getVersion() +
                                                       "' for dependency '"
                                                       + d.getManagementKey() + "': "
                                                       + e.getMessage(), e);
                }
            }
        } else {
            map = Collections.EMPTY_MAP;
        }
        return map;
    }

    /** Resolve project dependencies. Manual resolution is needed in order to
     * avoid resolution of multiproject artifacts (if projects will be linked
     * each other an installed jar is not needed) and to avoid a failure when a
     * jar is missing.
     *
     * @param project project which dependencies is resolved
     * @return resolved IDE dependencies, with attached jars for non-reactor
     *         dependencies
     * @throws MojoExecutionException if dependencies can't be resolved
     * @throws ProjectBuildingException exception which can be throw
     *         by methode createManagedVersionMap
     * @throws InvalidVersionSpecificationException exception which can be throw
     *         by methode artifactCollector
     * @athor Fabrizio Giustina
     * @athor Lukas Benda - small changes
     */
    protected IdeDependency[] doDependencyResolution(MavenProject project)
        throws MojoExecutionException, ProjectBuildingException, InvalidVersionSpecificationException {
        List deps = project.getDependencies();

        // Collect the list of resolved IdeDependencies.
        List dependencies = new ArrayList();

        if (deps != null) {
            Map managedVersions = createManagedVersionMap(artifactFactory, project.getId(),
                                                          project.getDependencyManagement());

            ArtifactResolutionResult artifactResolutionResult = null;

            Set firstLevelDep = new HashSet();
            MavenProject pro = project;
            while (pro != null) {
                getLog().debug("Project to string: " + pro.toString());
                for (Iterator iter = pro.getDependencies().iterator(); iter.hasNext();) {
                    Dependency dep = (Dependency) iter.next();
                    getLog().debug("doDependencyResolution - first level dependencies: "
                                   + dep.getGroupId() + ":" + dep.getArtifactId());
                    firstLevelDep.add(dep.getGroupId() + ":" + dep.getArtifactId());
                }
                pro = pro.getParent();
            }

            try {
                List listeners = new ArrayList();

                artifactResolutionResult = artifactCollector.collect(getProjectArtifacts(),
                                                                     project.getArtifact(),
                                                                     managedVersions,
                                                                     localRepo,
                                                                     project.getRemoteArtifactRepositories(),
                                                                     artifactMetadataSource, null,
                                                                     listeners);
            } catch (ArtifactResolutionException e) {
                getLog().error("Artifact resolution failed for:\n" +
                               "Group: " + e.getGroupId() + "\n" +
                               "Artifact: " + e.getArtifactId() + "\n" +
                               "Version: " + e.getVersion() + "\n" +
                               "Message: " + e.getMessage() + "\n" 
                               );
                // if we are here artifactResolutionResult is null, create a
                // project without dependencies but don't fail (this could be a
                // reactor projects, we don't want to fail everything)
                // Causes MECLIPSE-185. Not sure if it should be handled this way??
                return new IdeDependency[0];
            }

            // keep track of added reactor projects in order to avoid duplicates
            Set emittedReactorProjectId = new HashSet();

            for (Iterator i = artifactResolutionResult.getArtifactResolutionNodes().iterator(); i.hasNext(); ) {
                ResolutionNode node = (ResolutionNode) i.next();
                int dependencyDepth = node.getDepth();
                Artifact art = node.getArtifact();

                try {
                    artifactResolver.resolve(art, node.getRemoteRepositories(), localRepo);
                } catch (ArtifactNotFoundException e) {
                    getLog().debug("Artifact download failed:\n" +
                                   "Group: " + e.getGroupId() + "\n" +
                                   "Artifact: " + e.getArtifactId() + "\n" +
                                   "Version: " + e.getVersion() + "\n" +
                                   "Message: " + e.getMessage() + "\n");
                } catch (ArtifactResolutionException e) {
                    getLog().debug("Artifact resolution failed" +
                                   "Group: " + e.getGroupId() + "\n" +
                                   "Artifact: " + e.getArtifactId() + "\n" +
                                   "Version: " + e.getVersion() + "\n" +
                                   "Message: " + e.getMessage() + "\n");
                }

                if (emittedReactorProjectId.add(art.getGroupId() + '-' + art.getArtifactId())) {
                    boolean firstLevel = firstLevelDep.contains(art.getGroupId() + ":" + art.getArtifactId());

                    IdeDependency dep = new IdeDependency(art.getGroupId(), art.getArtifactId(),
                                                          art.getVersion(), art.getClassifier(),
                                                          firstLevel,
                                                          Artifact.SCOPE_TEST.equals(art.getScope()),
                                                          Artifact.SCOPE_SYSTEM.equals(art.getScope()),
                                                          Artifact.SCOPE_PROVIDED.equals(art.getScope()),
                                                          art.getArtifactHandler().isAddedToClasspath(),
                                                          art.getFile(), art.getType(), dependencyDepth);
                    
                    // no duplicate entries allowed. System paths can cause this problem.
                    if (!dependencies.contains(dep)) {
                        dependencies.add(dep);
                    }
                }
            }

            // @todo a final report with the list of
            // missingArtifacts?
        }

        IdeDependency[] ideDeps = (IdeDependency[]) dependencies.toArray(new IdeDependency[dependencies.size()]);
        return ideDeps;
    }

    /** Methode which create file prj.el
     * @param sourceDir direcotry in which will be prj.el write
     * @param outputDir name of direcotry in which shell be write copiled class
     * @param deps dependencies all dependensies of this project
     * @param test true if now is created test prj.el
     * @throws MojoExecutionException common exception
     * @since 1.0
     * @version 1.2.2
     */
    private void createJdeePrj(File sourceDir, File outputDir, IdeDependency[] deps, boolean test)
        throws MojoExecutionException {
        getLog().debug("createJdeePrj");
        File f = sourceDir;

        if (!f.exists()) { f.mkdirs(); }
        File jdeePrjEl = new File(f, "prj.el");

        StringBuffer sb = new StringBuffer();

        sb.append(";; Generated by maven-jdee-plugin version " + VERSION + "\n");
        sb.append(";; Don't make any changes in this file.\n");
        if (isJdeeProjectFileVersion()) {
            sb.append("(jde-project-file-version \"");
            sb.append(projectVersion);
            sb.append("\")\n");
        } else { sb.append("(jde-project-file-version \"1.0\")\n"); }
        sb.append("(jde-set-variables\n" + "  '(jde-project-name \"" + artifactId);
        if (test) { sb.append("-test"); }
        sb.append("\")\n" + "  '(jde-project-file-name \"prj.el\")\n\n");
        sb.append("  '(jde-sourcepath '(\"" + sourceDir + "\"");
        if (test) { sb.append(" \"" + sourceDirectory + "\""); }
        sb.append("))\n");
        sb.append("  '(jde-compile-option-directory \"" + outputDir + "\")\n");

        sb.append("  '(jde-global-classpath '(\n");
        sb.append("    \"" + outputDir + "\"\n");
        if (test) { sb.append("    \"" + buildDirectory + "\"\n"); }

        StringBuffer javadoc = new StringBuffer("  '(jde-help-docsets (append '(\n");
        StringBuffer dependSourcePath = new StringBuffer("  '(jde-depend-sourcepath '(\n");

        boolean appendJavadoc = false;
        boolean appendSourcepath = false;
        for (int i = 0; i < deps.length; i++) {
            sb.append("    \"");
            sb.append(deps[i].getFile().getAbsolutePath());
            sb.append("\"\n");

            if (deps[i].isFirstLevel()) {
                getLog().debug("First level dependecies: " + deps[i].getGroupId() + ":" + deps[i].getArtifactId());
                String sourcePath = null;
                if (dependSources || (dependJavadocs && generateMissingJavadoc)) {
                    sourcePath = classifierPath(deps[i], "sources");
                    if (dependSources && sourcePath != null && !"".equals(sourcePath)) {
                        dependSourcePath.append("    \"");
                        dependSourcePath.append(sourcePath);
                        dependSourcePath.append("\"\n");
                        appendSourcepath = true;
                    }
                }

                if (dependJavadocs) {
                    String javadocPath = classifierPath(deps[i], "javadoc");
                    if ((javadocPath == null || "".equals(javadocPath))
                        && (generateMissingJavadoc && sourcePath != null && !"".equals(sourcePath))) {
                        javadocPath = generateJavadoc(deps[i], sourcePath, "javadoc");
                    }
                    if (javadocPath != null && !"".equals(javadocPath)) {
                        javadoc.append("    (\"User (javadoc)\" \"");
                        javadoc.append(javadocPath);
                        javadoc.append("\" nil)\n");
                        appendJavadoc = true;
                    }
                }
            }
        }
        sb.append("))\n");

        if (appendJavadoc) {
            sb.append("\n");
            sb.append(javadoc.toString());
            sb.append(") jde-global-help-docsets))\n");
        }
        if (appendSourcepath) {
            sb.append("\n");
            sb.append(dependSourcePath.toString());
            sb.append("))\n");
        }
        sb.append(")\n\n");

        sb.append("(setq jde-maven-project-file-name \""
                  + executedProject.getBasedir().getAbsolutePath() + File.separator
                  + "pom.xml\")\n\n");

        String checkstyle = checkStyleConfiguration();
        if (checkstyle != null && !"".equals(checkstyle)) {
            sb.append("(setq jde-checkstyle-style \"" + checkstyle + "\")\n\n");
        }

        if (test) {
            if (testGoal != null && !"".equals(testGoal)) {
                sb.append("(jde-maven2-set-current-goal\n"
                          + "  (if (jde-maven2-get-goal) (jde-maven2-get-goal) \""
                          + testGoal + "\"))\n");
            }
            if (testProfile != null && !"".equals(testProfile)) {
                sb.append("(jde-maven2-set-current-profile\n"
                          + "  (if (jde-maven2-get-profile) (jde-maven2-get-profile) \""
                          + testProfile + "\"))");
            }
            if (testArguments != null && !"".equals(testArguments)) {
                sb.append("(jde-maven2-set-current-arguments\n"
                          + "  (if (jde-maven2-get-arguments) (jde-maven2-get-arguments) \""
                          + testArguments + "\"))");
            }
        } else {
            if (defaultGoal != null && !"".equals(defaultGoal)) {
                sb.append("(jde-maven2-set-current-goal\n"
                          + "  (if (jde-maven2-get-goal) (jde-maven2-get-goal) \""
                          + defaultGoal + "\"))\n");
            }
            if (defaultProfile != null && !"".equals(defaultProfile)) {
                sb.append("(jde-maven2-set-current-profile\n"
                          + "  (if (jde-maven2-get-profile) (jde-maven2-get-profile) \""
                          + defaultProfile + "\"))");
            }
            if (defaultArguments != null && !"".equals(defaultArguments)) {
                sb.append("(jde-maven2-set-current-arguments\n"
                          + "  (if (jde-maven2-get-arguments) (jde-maven2-get-arguments) \""
                          + defaultArguments + "\"))");
            }
        }

        if (!noRecreate) {
            sb.append("\n\n");
            sb.append("(jde-maven2-recreate-project-file-if-change \""
                      + VERSION + "\" ");
            sb.append(arguments());
            sb.append(")\n");
            //      sb.append("(jde-mode)\n\n");
        }

        File basicPrjEl = new File(executedProject.getBasedir().getAbsolutePath(), "prj.el");
        if (basicPrjEl.exists()) {
            sb.append("\n");
            sb.append("(load-library \"");
            sb.append(basicPrjEl.getAbsolutePath());
            sb.append("\")");
            sb.append("\n");
        }
        FileWriter w = null;
        try {
            w = new FileWriter(jdeePrjEl);
            w.write(sb.toString());
        } catch (IOException e) {
            throw new MojoExecutionException("Error creating file " + jdeePrjEl, e);
        } finally {
            if (w != null) {
                try {
                    w.close();
                }
                catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    /** Methode which return arguments which will be call this maven
     * @return arguments to run
     */
    private String arguments() {
        StringBuffer result = new StringBuffer();

        if (globalCachePath != null && !"".equals(globalCachePath)) {
            result.append("\"-DglobalCachePath=" + globalCachePath + "\" ");
        }
        if (!jdeeProjectFileVersion) {
            result.append("\"-DjdeeProjectFileVersion=false\" ");
        }
        if (makeXref) { result.append("\"-DmakeXref=true\" "); }
        if (downloadSources) { result.append("\"-DdownloadSources=true\" "); }
        if (dependSources) { result.append("\"-DdependSources=true\" "); }
        if (downloadJavadocs) { result.append("\"-DdownloadJavadocs=true\" "); }
        if (!dependJavadocs) { result.append("\"-DdependJavadocs=false\" "); }
        if (!generateMissingJavadoc) {
            result.append("\"-DgenerateMissingJavadoc=false\" ");
        }
        if (defaultGoal != null && !"".equals(defaultGoal)) {
            result.append("\"-DdefaultGoal=" + defaultGoal + "\" ");
        }
        if (defaultProfile != null && !"".equals(defaultProfile)) {
            result.append("\"-DdefaultProfile=" + defaultProfile + "\" ");
        }
        if (defaultArguments != null && !"".equals(defaultArguments)) {
            result.append("\"-DdefaultArguments=" + defaultArguments + "\" ");
        }
        if (testGoal != null && !"".equals(testGoal)) {
            result.append("\"-DtestGoal=" + testGoal + "\" ");
        }
        if (testProfile != null && !"".equals(testProfile)) {
            result.append("\"-DtestProfile=" + testProfile + "\" ");
        }
        if (testArguments != null && !"".equals(testArguments)) {
            result.append("\"-DtestArguments=" + testArguments + "\" ");
        }

        return result.toString();
    }

    /** <p>Methode which return cache path of dependencie (and create it if is it
     * necessary).</p>
     * <p>Use basic cache path from parameter <code>globalCachePath</code> if is
     * null or void string then will be use
     * <code>.maven-emacs-plugin-cache</code> in project basic directory.</p>
     * @param dependency dependecie which chache path is neaded
     * @return File which represent cache direcotry of dependecy
     * @since 1.2.1
     * @version 1.2.1
     */
    private File dependecyCachePath(IdeDependency dependency) {
        String path = globalCachePath;
        if (path == null || "".equals(path)) {
            path = executedProject.getBasedir() + File.separator + ".maven-emacs-plugin-cache";
        }
        if (path.lastIndexOf(File.separator) < path.length() - 1) {
            path += File.separator;
        }

        File f = new File(path + dependency.getGroupId() + File.separator
                          + dependency.getArtifactId() + File.separator
                          + dependency.getVersion() + File.separator);
        if (!f.exists()) {
            getLog().info("Create directory: " + f.getAbsolutePath());
            f.mkdirs();
        }
        return f;
    }

    /** Methode which uncompress jar file to cache directory and return his path.
     * <p>This methode uncompress file to cache directory from parametr
     * globalCachePath or if si null or "" then will use
     * .maven-emacs-plugin-cache in project.getBasedir().</p>
     * <p>This methode uncompress file only if last modification time of cache
     * directory is less then file last modification time.</p>
     * @param dependency dependenci which have document jarfile
     * @param classifier classifier - javadoc or source which will be unpack and
     *        return absolute path to direcotry
     * @return path to cache direcotry or null
     * @since 1.2
     * @version 1.2.1
     */
    private String classifierPath(IdeDependency dependency, String classifier) {
        String result = null;
        File attachment;
        String subDir = "";
        if ("javadoc".equals(classifier)) {
            attachment = dependency.getJavadocAttachment();
            subDir = "javadoc";
        } else {
            attachment = dependency.getSourceAttachment();
        }

        if (attachment != null && attachment.exists()) {
            File f = dependecyCachePath(dependency);
            if (subDir != null && !"".equals(subDir)) {
                f = new File(f, subDir + File.separator);
            }

            result = f.getAbsolutePath();
            if (!f.exists()) {
                getLog().info("Create directory: " + f.getAbsolutePath());
                f.mkdirs();
            } else if (f.lastModified() < attachment.lastModified()) {
                FileUtil.cleanDirectory(f);
            }

            if (f.listFiles() == null || f.listFiles().length == 0) {
                try {
                    ZipFile zipFile = new ZipFile(attachment);
                    byte[] data = new byte[100000];
                    for (Enumeration en = zipFile.entries(); en.hasMoreElements(); ) {
                        ZipEntry entry = (ZipEntry) en.nextElement();
                        File file = new File(f, entry.getName());
                        try {
                            if (entry.isDirectory()) {
                                file.mkdirs();
                            } else {
                                file.createNewFile();
                            }
                            if (entry.getTime() != -1) {
                                file.setLastModified(entry.getTime());
                            }
                            if (!entry.isDirectory()) {
                                try {
                                    InputStream is = zipFile.getInputStream(entry);
                                    try {
                                        FileOutputStream fos = new FileOutputStream(file);
                                        int readed;
                                        while ((readed = is.read(data)) > -1) {
                                            fos.write(data, 0, readed);
                                        }
                                        fos.flush();
                                        fos.close();
                                        is.close();
                                    } catch (FileNotFoundException e) {
                                        getLog().error("Cannot uzip entry in zip file: "
                                                       + entry.getName() + " message: "
                                                       + e.getMessage());
                                    }
                                } catch (IOException e) {
                                    getLog().error("Cannot uzip entry in zip file: "
                                                   + entry.getName() + " message: "
                                                   + e.getMessage());
                                }
                            }
                        } catch (IOException e) {
                            getLog().error("Cannot unzip entry in zip file: "
                                           + entry.getName()+ " message: "
                                           + e.getMessage());
                        }
                    }
                } catch (IOException e) { // ZipException is catched too
                    getLog().error("Cannot unzip file: " + attachment.getAbsolutePath() + " message: " + e.getMessage());
                    result = "";
                }
            }
        }
        return result;
    }

    /**
     * Methode which generate javadoc for given depencies
     *
     * @param dependency dependency which will be generated
     * @param sourcePath path to sources
     * @param subDir name of javadoc subdirectory
     * @return path to javadoc directory
     * @since 1.2.1
     * @version 1.2.1
     */
    private String generateJavadoc(IdeDependency dependency, String sourcePath, String subDir) {
        String result;
        File f = dependecyCachePath(dependency);
        if (subDir != null && !"".equals(subDir)) {
            f = new File(f, subDir + File.separator);
        }

        result = f.getAbsolutePath();
        File spf = new File(sourcePath + File.separator);
        if (!f.exists()) {
            getLog().info("Create directory: " + f.getAbsolutePath());
            f.mkdirs();
        } else if (f.lastModified() < spf.lastModified()) {
            FileUtil.cleanDirectory(f);
        }

        if (f.list() == null || f.list().length == 0) {
            List javadocParams = new LinkedList(Arrays.asList("-d", f.getAbsolutePath(), "-docletpath",
                                                              javaHome + "/../lib/tools.jar",
                                                              //"-doclet",
                                                              //"com.sun.tools.doclets.standard.Standard",
                                                              "-sourcepath", sourcePath, "-protected",
                                                              "-use", "-version", "-author",
                                                              "-subpackages"));

            try {
                File[] files = spf.listFiles();
                for (int i = 0; i < files.length; i++) {
                    if (files[i].isDirectory()
                        && !"META-INF".equals(files[i].getName())
                        && (subDir == null || "".equals(subDir)
                            || !subDir.equals(files[i].getName()))
                        && files[i].list() != null
                        && files[i].list().length > 0) {
                        javadocParams.add(files[i].getName());
                    }
                }
                StringBuffer parameters = new StringBuffer(javaHome + "/../bin/javadoc ");
                String[] mainParam = new String[javadocParams.size()];
                int j = 0;
                for (Iterator iter = javadocParams.iterator(); iter.hasNext();) {
                    String param = (String) iter.next();
                    mainParam[j] = param;
                    parameters.append(param);
                    parameters.append(" ");
                    j++;
                }
                getLog().info(parameters.toString());

                /*
                  This end with error: javadoc: error - In doclet class com.sun.tools.doclets.standard.Standard, method languageVersion must return LanguageVersion.
                  PrintWriter pwErr = new PrintWriter(System.err);
                  PrintWriter pwOut = new PrintWriter(System.out);
                  com.sun.tools.javadoc.Main.execute("javadoc", pwErr, pwErr,
                  pwOut,
                  "com.sun.tools.doclets.standard.Standard",
                  mainParam);
                */

                Process process = Runtime.getRuntime().exec(parameters.toString());
                AsynchronousProcess ap = new AsynchronousProcess(process);
                ap.setInputStream(System.out);
                ap.setErrorStream(System.err);
                ap.run();

                try {
                    if (process.exitValue() != 0) {
                        DataInputStream dis = new DataInputStream(process.getErrorStream());
                        getLog().error(dis.readUTF());
                    }
                } catch (IllegalThreadStateException e) {
                    // ignore
                }
            } catch (Exception e) {
                getLog().error("Cannot generate javadoc in path: " + result
                               + " because of error raise: " + e.getMessage());
                e.printStackTrace();
                result = null;
            }
        }

        return result;
    }

    /** Methode which return checkstyle configuration location.  This methode return
     * config location as absolute path. This methode can find if location is write
     * relative from this project or from some parent and transform file path from
     * relative to absolute
     * @todo reada checkstyle from repository or predefined checkstyle
     *       <ul>
     *         <li>config/sun_checks.xml - Sun Microsystems Definition (default).</li>
     *         <li>config/maven_checks.xml - Maven Development Definitions.</li>
     *         <li>config/turbine_checks.xml - Turbine Development Definitions.</li>
     *         <li>config/avalon_checks.xml - Avalon Development Definitions.</li>
     *       </ul>
     * @todo make customization by propertyExpansion and propertesLocation tags
     * @todo implement tags suppressionsLocation and suppressionsFileExpression
     * @return chekstyle configuration location or null
     * @since 1.2
     * @version 1.2
     */
    private String checkStyleConfiguration() {
        String result = "";
        for (Iterator iter = executedProject.getBuildPlugins().iterator();
             iter.hasNext();) {
            Object o = iter.next();
            if (Plugin.class.isAssignableFrom(o.getClass())) {
                Plugin plugin = (Plugin) o;
                if ("org.apache.maven.plugins".equals(plugin.getGroupId())
                    && "maven-checkstyle-plugin".equals(plugin.getArtifactId())) {
                    if (Xpp3Dom.class.isAssignableFrom(plugin.getConfiguration().getClass())) {
                        Xpp3Dom pluginConfig = (Xpp3Dom) plugin.getConfiguration();
                        result = pluginConfig.getChild("configLocation").getValue();

                    }
                }
            }
        }

        for (Iterator iter = executedProject.getReportPlugins().iterator();
             iter.hasNext();) {
            Object o = iter.next();
            if (ReportPlugin.class.isAssignableFrom(o.getClass())) {
                ReportPlugin plugin = (ReportPlugin) o;
                if ("org.apache.maven.plugins".equals(plugin.getGroupId())
                    && "maven-checkstyle-plugin".equals(plugin.getArtifactId())) {
                    if (Xpp3Dom.class.isAssignableFrom(plugin.getConfiguration().getClass())) {
                        Xpp3Dom pluginConfig = (Xpp3Dom) plugin.getConfiguration();
                        result = pluginConfig.getChild("configLocation").getValue();

                    }
                }
            }
        }
        // This part make absolute path from relative path
        if (result != null && !"".equals(result)) {
            File file = new File(result);
            if (file.exists()) {
                result = file.getAbsolutePath();
            } else {
                // Try find configuration which is write as relative path in some parent of
                // this projet
                MavenProject parent = executedProject.getParent();
                while (parent != null && !file.exists()) {
                    file = new File(parent.getBasedir(), result);
                    parent = parent.getParent();
                }
                if (file.exists()) {
                    result = file.getAbsolutePath();
                }
            }
        }
        return result;
    }

    private void createXrefFile(File[] sourceDir, File[] outputDir)
        throws MojoExecutionException {
        File f = sourceDir[0];

        if (!f.exists()) {
            f.mkdirs();
        }

        File xref = new File(f, "xref.data");
        FileWriter w = null;
        try {
            w = new FileWriter("xref.data");

            w.write("[" + artifactId + "]\n");
            w.write("  -javafilesonly\n");
            for (File sd : sourceDir) {
                w.write("  " + sd + "\n");
            }
            w.write("  -refs /Users/arjenwiersma/.Xrefs/" + artifactId + "\n");
            w.write("  -refalphahash\n");
            w.write("  -set cp ");
            int entries = executedProject.getArtifacts().size();
            int pos = 0;
            for (Object o : executedProject.getArtifacts()) {
                Artifact a = (Artifact) o;
                w.write(a.getFile().toString());
                if (pos++ < entries) {
                    w.write(":");
                }
            }
            w.write("\n");
            w.write("  -set qcp \"${dq}${cp}${dq}\"\n");
            w.write("  -set jhome \"/System/Library/Frameworks/JavaVM.framework/Versions/CurrentJDK/Home/\"\n");
            w.write("  -set jbin \"${jhome}bin/\"\n");
            w.write("  -jdkclasspath \"${jhome}jre/lib/rt.jar\"\n");
            w.write("  -classpath ${cp}\n");
            w.write("  -sourcepath " + sourceDir[0] + "\n");

            /*
            //  setting for Emacs compile and run
            -set compilefile "${jbin}javac -classpath ${qcp} -d /Users/arjenwiersma/EBuddy/ESB/esb/target ${dq}${__file}${dq}"
            -set compiledir "${jbin}javac -classpath ${qcp} -d /Users/arjenwiersma/EBuddy/ESB/esb/target *.java"
            -set compileproject "
            cd esb
            ant
            "
            -set runthis "${jbin}java -classpath ${qcp} %s"
            -set run1 "${jbin}java -classpath ${qcp} com.ebuddy.esb.impl.component.DataBrokerImpl"
            -set run5 ""  // an empty run; C-F8 will only compile
            //  set default to run1
            -set run ${run1}
            //  HTML configuration
            -htmlroot=/Users/arjenwiersma/EBuddy/ESB
            -htmlgxlist -htmllxlist -htmldirectx -htmllinenums
            -htmltab=4 -htmllinenumcolor=000000
            -htmlgenjavadoclinks -htmlcutsuffix -htmllinenumlabel=line
            */
        } catch (IOException e) {
            throw new MojoExecutionException("Error creating file xref.data", e);
        } finally {
            if (w != null) {
                try { w.close(); } catch (IOException e) {}
            }
        }
    }

    /** Resolve the required artifacts for each of the dependency.
     * <code>sources</code> or <code>javadoc</code> artifacts (depending on the
     * <code>classifier</code>) are attached to the dependency.
     *
     * @param deps resolved dependencies
     * @param classifier the classifier we are looking for
     *        (either <code>sources</code> or <code>javadoc</code>)
     * @param useRemoteRepos flag whether we should search remote
     *        repositories for the artifacts or not
     * @return the list of dependencies for which the required artifact was not found
     * @author Fabrizio Giustina
     * @author Lukas Benda - small changes
     */
    private List resolveDependenciesWithClassifier(IdeDependency[] deps,
                                                   String inClassifier,
                                                   boolean useRemoteRepos) {
        List missingClassifierDependencies = new ArrayList();

        // if downloadSources is off, just check
        // local repository for reporting missing source jars
        List remoteRepos = useRemoteRepos ? getRemoteArtifactRepositories() : Collections.EMPTY_LIST;

        for (int j = 0; j < deps.length; j++) {
            IdeDependency dependency = deps[j];

            if (dependency.isSystemScoped() && !dependency.isFirstLevel()) {
                // artifact not needed
                continue;
            }

            String classifier = inClassifier;
            String type = inClassifier;
            if ("sources".equals(classifier)
                && "tests".equals(dependency.getClassifier())) {
                classifier = "test-sources";
                type = "java-source";
            }
            if ("sources".equals(classifier)) { type = "jar"; }

            Artifact artifact = artifactFactory.createArtifactWithClassifier(dependency.getGroupId(),
                                                                             dependency.getArtifactId(),
                                                                             dependency.getVersion(),
                                                                             type, classifier);
            try {
                artifactResolver.resolve(artifact, remoteRepos, localRepo);
            } catch (ArtifactNotFoundException e) {
                // ignore, the jar has not been found
            } catch (ArtifactResolutionException e) {
                getLog().warn("Error resolving artifact:\n" +
                              "Group: " + e.getGroupId() + "\n" +
                              "Artifact: " + e.getArtifactId() + "\n" +
                              "Version: " + e.getVersion() + "\n" +
                              "Message: " + e.getMessage() + "\n");
            }

            /*      boolean resolve = artifact.isResolved();
                    if (!resolve) {
                    try {
                    File doc = new File(new File(new URI(localRepo.getUrl())),
                    localRepo.pathOf(artifact));
                    getLog().debug(doc.getAbsolutePath());
                    getLog().debug(localRepo.getProtocol());
                    resolve = doc.exists();
                    } catch (URISyntaxException e) {
                    // ignore
                    }
                    } */

            if (artifact.isResolved()){
                if ("sources".equals(classifier)) {
                    dependency.setSourceAttachment(artifact.getFile());
                } else if ("javadoc".equals(classifier)) {
                    dependency.setJavadocAttachment(artifact.getFile());
                }
            } else {
                // add the dependencies to the list
                // of those lacking the required
                // artifact
                missingClassifierDependencies.add(dependency);
            }
        }

        // return the list of dependencies missing the
        // required artifact
        return missingClassifierDependencies;
    }

    /** Resolve source artifacts and download them if <code>downloadSources</code>
     * is <code>true</code>. Source and javadocs artifacts will be attached to the
     * <code>IdeDependency</code> Resolve source and javadoc artifacts. The
     * resolved artifacts will be downloaded based on the
     * <code>downloadSources</code> and <code>downloadJavadocs</code> attributes.
     *
     * @param deps resolved dependencies
     * @since 1.2
     * @version 1.2.2
     */
    private void resolveSourceAndJavadocArtifacts(IdeDependency[] deps) {
        final List missingSources = resolveDependenciesWithClassifier(deps, "sources",
                                                                      isDownloadSources());
        //    missingSourceDependencies.addAll(missingSources);
        final List missingJavadocs = resolveDependenciesWithClassifier(deps, "javadoc",
                                                                       isDownloadJavadocs());
        //    missingJavadocDependencies.addAll(missingJavadocs);
    }

    /** Methode which execute goal
     * @since 1.0
     * @version 1.2.2
     */
    public void execute() throws MojoExecutionException {
        getLog().debug("execute");
        try {
            //      doDependencyResolution( executedProject, localRepo );
            IdeDependency[] deps = doDependencyResolution(executedProject);
            getLog().debug("Count of dependecies: " + Integer.toString(deps.length));

            resolveSourceAndJavadocArtifacts(deps);

            createJdeePrj(sourceDirectory, buildDirectory, deps, false);
            createJdeePrj(testSourceDirectory, testBuildDirectory, deps, true);

            if (isMakeXref()) {
                File[] sourceDirs = new File[] {sourceDirectory, testSourceDirectory};
                File[] outputDirs = new File[] {buildDirectory, testBuildDirectory};

                createXrefFile(sourceDirs, outputDirs);
            }

        } catch (Exception e) {
            throw new MojoExecutionException("Unable to build project dependencies.",
                                             e);
        }
    }

    /** Getter for <code>remoteArtifactRepositories</code>.
     * @return Returns the remoteArtifactRepositories.
     */
    public List getRemoteArtifactRepositories() {
        return this.remoteArtifactRepositories;
    }

    /** Setter for <code>remoteArtifactRepositories</code>.
     * @param remoteArtifactRepositories The remoteArtifactRepositories to set.
     */
    public void setRemoteArtifactRepositories(List remoteArtifactRepositories) {
        this.remoteArtifactRepositories = remoteArtifactRepositories;
    }

    /** Get the <code>JdeeProjectFileVersion</code> value.
     * @return a value
     */
    public final boolean isJdeeProjectFileVersion() {
        return jdeeProjectFileVersion;
    }

    /** Set the <code>JdeeProjectFileVersion</code> value.
     * @param newJdeeProjectFileVersion The new JdeeProjectFileVersion value.
     */
    public final void setJdeeProjectFileVersion(final boolean newJdeeProjectFileVersion) {
        this.jdeeProjectFileVersion = newJdeeProjectFileVersion;
    }

    /** Get the <code>MakeXref</code> value.
     * @return a value
     */
    public final boolean isMakeXref() {
        return makeXref;
    }

    /** Set the <code>MakeXref</code> value.
     * @param newMakeXref The new MakeXref value.
     */
    public final void setMakeXref(final boolean newMakeXref) {
        this.makeXref = newMakeXref;
    }

    /** Get the <code>DownloadSources</code> value.
     * @return a value
     */
    public final boolean isDownloadSources() {
        return downloadSources;
    }

    /** Set the <code>DownloadSources</code> value.
     * @param newDownloadSources The new DownloadSources value.
     */
    public final void setDownloadSources(final boolean newDownloadSources) {
        this.downloadSources = newDownloadSources;
    }

    /** Get the <code>DownloadJavadocs</code> value.
     * @return a value
     */
    public final boolean isDownloadJavadocs() {
        return downloadJavadocs;
    }

    /** Set the <code>DownloadJavadocs</code> value.
     * @param newDownloadJavadocs The new DownloadJavadocs value.
     */
    public final void setDownloadJavadocs(final boolean newDownloadJavadocs) {
        this.downloadJavadocs = newDownloadJavadocs;
    }
}
