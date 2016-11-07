/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.resolve.transform

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest

public abstract class AbstractAndroidPluginDependencyResolutionTest extends AbstractDependencyResolutionTest {

    def setup() {
        mockAndroidPlugin()
        settingsFile << "rootProject.name = 'fake-android-build'"
        buildFile    << ""
    }

    def mockAndroidPlugin() {
        def pluginSrcPackage = file("buildSrc/src/main/java/org/gradle/test/android/build/gradle");

        //Transformation classes
        pluginSrcPackage.file("AarExtractor.java") << """
            package org.gradle.test.android.build.gradle;

            import java.io.File;

            import org.gradle.api.Project;
            import org.gradle.api.Action;
            import org.gradle.api.file.CopySpec;
            import org.gradle.api.artifacts.transform.DependencyTransform;
            import org.gradle.api.artifacts.transform.TransformInput;
            import org.gradle.api.artifacts.transform.TransformOutput;

            @TransformInput(type = "aar")
            public class AarExtractor extends DependencyTransform {
                private Project project;

                private File outputDirectory;
                private File explodedAar;

                public void setProject(Project project) {
                    this.project = project;
                }

                @TransformOutput(type = "classpath")
                public File getClassesJar() {
                    return new File(explodedAar, "classes.jar");
                }

                @TransformOutput(type = "android-manifest")
                public File getManifest() {
                    return new File(explodedAar, "AndroidManifest.xml");
                }

                @Override
                public void transform(final File input) {
                    assert input.getName().endsWith(".aar");

                    explodedAar = new File(project.file("transformed"), input.getName());
                    if (!explodedAar.exists()) {
                        project.copy(new Action<CopySpec>() {
                            @Override
                            public void execute(CopySpec copySpec) {
                                copySpec.from(project.zipTree(input)).into(explodedAar);
                            }
                        });
                    }
                }
            }
        """.stripIndent()
        pluginSrcPackage.file("JarClasspathTransform.java") << """
            package org.gradle.test.android.build.gradle;

            import java.io.File;

            import org.gradle.api.artifacts.transform.DependencyTransform;
            import org.gradle.api.artifacts.transform.TransformInput;
            import org.gradle.api.artifacts.transform.TransformOutput;

            @TransformInput(type = "jar")
            public class JarClasspathTransform extends DependencyTransform {
                private File output;

                @TransformOutput(type = "classpath")
                public File getOutput() {
                    return output;
                }

                public void transform(File input) {
                    output = input;
                }
            }
        """.stripIndent()

        //Plugin classes
        pluginSrcPackage.file("BasePlugin.java") << """
            package org.gradle.test.android.build.gradle;

            import java.io.File;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.Action;
            import org.gradle.api.Task;
            import org.gradle.api.file.FileCollection;
            import org.gradle.api.tasks.bundling.Zip;
            import org.gradle.api.artifacts.Configuration;
            import org.gradle.api.artifacts.ResolutionStrategy;
            import org.gradle.api.artifacts.transform.DependencyTransform;
            import org.gradle.api.logging.Logging;

            public class BasePlugin implements Plugin<Project> {
                public void apply(Project project) {
                    project.getPluginManager().apply(org.gradle.api.plugins.BasePlugin.class);

                    Configuration compile = project.getConfigurations().create("compile");
                    project.getConfigurations().getAt("default").extendsFrom(compile);

                    Task classes = project.getTasks().create("classes");
                    Task aar = project.getTasks().create("aar", Zip.class, new Action<Zip>() {
                        @Override
                        public void execute(Zip zip) {
                            zip.from(project.file("aar-image"));
                            zip.setDestinationDir(project.getBuildDir());
                            zip.setExtension("aar");
                        }
                    }).dependsOn("classes");

                    project.getArtifacts().add("compile", aar);

                    ResolutionStrategy resolutionStrategy = project.getConfigurations().getAt("compile").getResolutionStrategy();
                    resolutionStrategy.registerTransform(AarExtractor.class, new Action<DependencyTransform>() {
                        @Override
                        public void execute(DependencyTransform transform) {
                            ((AarExtractor) transform).setProject(project);
                        }
                    });
                    resolutionStrategy.registerTransform(JarClasspathTransform.class, new Action<DependencyTransform>() {
                        @Override
                        public void execute(DependencyTransform transform) { }
                    });

                    final FileCollection compileClasspath = project.getConfigurations().getAt("compile").withType("classpath");
                    project.getTasks().create("processClasspath").dependsOn(compileClasspath).getActions().add(new Action<Object>() {
                        @Override
                        public void execute(Object transform) {
                            for (File file : compileClasspath) {
                                Logging.getLogger(this.getClass()).lifecycle(file.getAbsolutePath().substring(project.getRootDir().getAbsolutePath().length()));
                            }
                        }
                    });

                    final FileCollection compileManifests = project.getConfigurations().getAt("compile").withType("android-manifest");
                    project.getTasks().create("processManifests").dependsOn(compileManifests).getActions().add(new Action<Object>() {
                        @Override
                        public void execute(Object transform) {
                            for (File file : compileManifests) {
                                Logging.getLogger(this.getClass()).lifecycle(file.getAbsolutePath().substring(project.getRootDir().getAbsolutePath().length()));
                            }
                            if (compileManifests.isEmpty()) {
                                Logging.getLogger(this.getClass()).lifecycle("no manifest found");
                            }
                        }
                    });
                }
            }
        """.stripIndent()
        pluginSrcPackage.file("AppPlugin.java") << """
            package org.gradle.test.android.build.gradle;

            public class AppPlugin extends BasePlugin {
            }
        """.stripIndent()
        pluginSrcPackage.file("LibraryPlugin.java") << """
            package org.gradle.test.android.build.gradle;

            public class LibraryPlugin extends BasePlugin {
            }
        """.stripIndent()
    }

    def createAppSubproject(String appName) {
        settingsFile << """
            include '$appName'
        """.stripIndent()
        file("$appName/build.gradle") << """
            apply plugin: org.gradle.test.android.build.gradle.AppPlugin

            repositories {
                maven { url '${mavenRepo.uri}' }
            }
        """.stripIndent()
    }

    def createAndroidLibSubproject(String libName) {
        file(libName).mkdirs()
        settingsFile << """
            include '$libName'
        """.stripIndent()
        file("$libName/build.gradle") << """
            apply plugin: org.gradle.test.android.build.gradle.LibraryPlugin
        """.stripIndent()
    }

    def createJavaLibSubproject(String libName) {
        file(libName).mkdirs()
        settingsFile << """
            include '$libName'
        """.stripIndent()
        file("$libName/build.gradle") << """
            apply plugin: 'java'
        """.stripIndent()
    }

    def publishJar(String artifactId) {
        mavenRepo.module("org.gradle", artifactId).publish()
    }

    def publishAAR(String artifactId) {
        def aarImage = file('android-lib/aar-image')
        aarImage.file('AndroidManifest.xml') << "<AndroidManifest/>"
        file('android-lib/classes/foo.txt') << "something"
        file('android-lib/classes/bar/baz.txt') << "something"
        file('android-lib/classes').zipTo(aarImage.file('classes.jar'))

        def module = mavenRepo.module("org.gradle", artifactId).hasType('aar').publish()
        module.artifactFile.delete()
        aarImage.zipTo(module.artifactFile)
    }

    def dependency(String from, String to) {
        file("$from/build.gradle") << """
            dependencies {
                compile $to
            }
        """.stripIndent()
    }

    def classpathArtifacts(String project, String selector) {
        file("$project/build.gradle") << """
            artifacts {
                $selector {
                    type 'classpath'
                }
            }
        """.stripIndent()
    }

    void classpath(String... classpathElements) {
        assert succeeds('processClasspath')

        for (String classpathElement : classpathElements) {
            outputContains(classpathElement)
        }
    }

    void manifest(String... manifests) {
        assert succeeds('processManifests')

        if (manifests.length == 0) {
            outputContains('no manifest found')
        }

        for (String manifest : manifests) {
            outputContains(manifest)
        }
    }

}
