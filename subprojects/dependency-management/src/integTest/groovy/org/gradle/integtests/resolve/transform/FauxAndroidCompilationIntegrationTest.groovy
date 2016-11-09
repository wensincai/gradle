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

/**
 * Overview of Configurations and 'formats' used in this scenario:
 *
 * Formats:
 * - aar                aar file
 * - jar                jar file
 * - classes            classes folder
 * - android-manifest   AndroidManifest.xml
 * - classpath          everything that can be in a JVM classpath (jar files, class folders, files treated as resources)
 *
 * Configurations:
 * - runtime                        behaves as runtime in Java plugin (e.g. packages classes in jars locally)
 * - compileClassesAndResources     provides all artifacts in its raw format (e.g. class folders, not jars)
 *
 * - processClasspath               filters and transforms to 'classpath' format (e.g. keeps jars, but extracts 'classes.jar' from external AAR)
 * - processClasses                 filters and transforms to 'classes' format (e.g. extracts jars to class folders)
 * - processManifests               filters for 'android-manifest' format (no transformations taking place)
 */
public class FauxAndroidCompilationIntegrationTest extends AbstractDependencyResolutionTest {

    def setup() {
        settingsFile << """
    rootProject.name = 'fake-android-build'
    include 'java-lib'
    include 'android-lib'
    include 'android-app'
"""
        buildFile << """
import org.gradle.api.artifacts.transform.*

    project(':java-lib') {
        apply plugin: 'java'

        configurations {
            compileClassesAndResources

        }
        configurations.default.extendsFrom = [configurations.compileClassesAndResources] //setter removes extendFrom(runtime)

        artifacts {
            compileClassesAndResources(compileJava.destinationDir) {
                type 'classes'
                builtBy classes
            }
        }
    }

    project(':android-lib') {
        apply plugin: 'base'

        configurations {
            compileClassesAndResources
            runtime //compiles JAR as in Java plugin
            compileAAR
        }
        configurations.default.extendsFrom = [configurations.compileClassesAndResources]

        task classes(type: Copy) {
            from file('classes/main')
            into file('build/classes/main')
        }

        task jar(type: Zip) {
            dependsOn classes
            from classes.destinationDir
            destinationDir = file('aar-image')
            baseName = 'classes'
            extension = 'jar'
        }

        task aar(type: Zip) {
            dependsOn jar
            from file('aar-image')
            destinationDir = file('build')
            extension = 'aar'
        }

        artifacts {
            compileClassesAndResources(classes.destinationDir) {
                type 'classes'
                builtBy classes
            }
            compileClassesAndResources(file('aar-image/AndroidManifest.xml')) {
                type 'android-manifest'
            }

            runtime jar

            compileAAR aar
        }
    }

    project(':android-app') {
        apply plugin: 'base'

        configurations {
            compileClassesAndResources
            runtime

            // configurations with filtering/transformation over 'compile'
            processClasspath {
                extendsFrom(compileClassesAndResources)
                format = 'classpath' // 'classes' or 'jar'
                resolutionStrategy {
                    registerTransform(AarExtractor)  {
                        outputDirectory = project.file("transformed")
                        files = project
                    }
                    registerTransform(JarClasspathTransform) {
                        outputDirectory = project.file("transformed")
                        files = project
                    }
                    registerTransform(ClassesFolderClasspathTransform) { }
                }
            }
            processClasses {
                extendsFrom(compileClassesAndResources)
                format = 'classes'
                resolutionStrategy {
                    registerTransform(AarExtractor)  {
                        outputDirectory = project.file("transformed")
                        files = project
                    }
                    registerTransform(JarClasspathTransform) {
                        outputDirectory = project.file("transformed")
                        files = project
                    }
                }
            }
            processManifests {
                extendsFrom(compileClassesAndResources)
                format = 'android-manifest'
                resolutionStrategy {
                    registerTransform(AarExtractor)  {
                        outputDirectory = project.file("transformed")
                        files = project
                    }
                }
            }
        }

        repositories {
            maven { url '${mavenRepo.uri}' }
        }

        task printArtifacts {
            dependsOn configurations[configuration]
            doLast {
                configurations[configuration].incoming.artifacts.each { println it.file.absolutePath - rootDir }
            }
        }
    }

    @TransformInput(type = 'aar')
    class AarExtractor extends DependencyTransform {
        private Project files

        private File explodedAar
        private File explodedJar

        @TransformOutput(type = 'jar')
        File getClassesJar() {
            new File(explodedAar, "classes.jar")
        }

        @TransformOutput(type = 'classpath')
        File getClasspathElement() {
            getClassesJar()
        }

        @TransformOutput(type = 'classes')
        File getClassesFolder() {
            explodedJar
        }

        @TransformOutput(type = 'android-manifest')
        File getManifest() {
            new File(explodedAar, "AndroidManifest.xml")
        }

        void transform(File input) {
            assert input.name.endsWith('.aar')

            explodedAar = new File(outputDirectory, input.name + '/explodedAar')
            explodedJar = new File(outputDirectory, input.name + '/explodedClassesJar')

            if (!explodedAar.exists()) {
                files.copy {
                    from files.zipTree(input)
                    into explodedAar
                }
            }
            if (!explodedJar.exists()) {
                files.copy {
                    from files.zipTree(new File(explodedAar, 'classes.jar'))
                    into explodedJar
                }
            }
        }
    }

    @TransformInput(type = 'jar')
    class JarClasspathTransform extends DependencyTransform {
        private Project files

        private File jar
        private File classesFolder

        @TransformOutput(type = 'classpath')
        File getClasspathElement() {
            jar
        }

        @TransformOutput(type = 'classes')
        File getClassesFolder() {
            classesFolder
        }

        void transform(File input) {
            jar = input

            //We could use a location based on the input, since the classes folder is similar for all consumers.
            //Maybe the output should not be configured from the outside, but the context of the consumer should
            //be always passed in autoamtically (as we do with "Project files") here. Then the consumer and
            //properties of it (e.g. dex options) can be used in the output location
            classesFolder = new File(outputDirectory, input.name + "/classes")
            if (!classesFolder.exists()) {
                files.copy {
                    from files.zipTree(input)
                    into classesFolder
                }
            }
        }
    }

    @TransformInput(type = 'classes')
    class ClassesFolderClasspathTransform extends DependencyTransform {
        private File classesFolder

        @TransformOutput(type = 'classpath')
        File getClasspathElement() {
            classesFolder
        }

        void transform(File input) {
            classesFolder = input
        }
    }
"""

        file('android-app').mkdirs()

        // Android Lib: "Source Code"
        file('android-lib/classes/main/foo.txt') << "something"
        file('android-lib/classes/main/bar/baz.txt') << "something"
        file('android-lib/classes/main/bar/baz.txt') << "something"

        // Android Lib: Manifest and zipped code
        def aarImage = file('android-lib/aar-image')
        aarImage.file('AndroidManifest.xml') << "<AndroidManifest/>"
        file('android-lib/classes').zipTo(aarImage.file('classes.jar'))

        // Publish an AAR
        def module = mavenRepo.module("org.gradle", "ext-android-lib").hasType('aar').publish()
        module.artifactFile.delete()
        aarImage.zipTo(module.artifactFile)

        // Publish a JAR
        mavenRepo.module("org.gradle", "ext-java-lib").publish()
    }

    // compileClassesAndResources (unfiltered, no transformations)

    def "compileClassesAndResources references class folder from local java library"() {
        when:
        dependency "project(':java-lib')"

        then:
        artifacts('compileClassesAndResources') == ['/java-lib/build/classes/main']
        executed ":java-lib:classes"
        notExecuted ':java-lib:jar'
    }

    def "compileClassesAndResources references classes folder and manifest from local android library"() {
        when:
        dependency "project(':android-lib')"

        then:
        artifacts('compileClassesAndResources') == ['/android-lib/build/classes/main', '/android-lib/aar-image/AndroidManifest.xml']
        executed ":android-lib:classes"
        notExecuted ":android-lib:jar"
        notExecuted ":android-lib:aar"
    }

    // Working with jars, using 'compile' instead of 'compileClassesAndResources'

    def "compile references jar from local java library"() {
        when:
        dependency "project(':java-lib')"
        dependency "runtime", "project(path: ':java-lib', configuration: 'runtime')" //need to declare 'runtime' as it is not teh default here anymore

        then:
        artifacts('runtime') == ['/java-lib/build/libs/java-lib.jar']
        executed ":java-lib:classes"
        executed ':java-lib:jar'
    }

    def "compile references classes.jar from local android library"() {
        when:
        dependency "project(':java-lib')"
        dependency "runtime", "project(path: ':android-lib', configuration: 'runtime')"

        then:
        artifacts('runtime') == ['/android-lib/aar-image/classes.jar']
        executed ":android-lib:classes"
        executed ":android-lib:jar"
        notExecuted ":android-lib:aar"
    }

    // processClasses filtering and transformation

    def "processClasspath includes jars from published java modules"() {
        when:
        dependency "'org.gradle:ext-java-lib:1.0'"

        then:
        artifacts('processClasspath') == ['/maven-repo/org/gradle/ext-java-lib/1.0/ext-java-lib-1.0.jar']
    }

    def "processClasspath includes classes.jar from published android modules"() {
        when:
        dependency "'org.gradle:ext-android-lib:1.0'"

        then:
        artifacts('processClasspath') == ['/android-app/transformed/ext-android-lib-1.0.aar/explodedAar/classes.jar']
    }

    def "processClasspath can include jars from file dependencies"() {
        when:
        dependency "gradleApi()"

        then:
        artifacts('processClasspath').size() > 20
        output.contains('.jar\n')
        !output.contains('.jar/classes')
    }

    def "processClasspath includes a combination of project class folders and library jars"() {
        when:
        dependency "project(':java-lib')"
        dependency "project(':android-lib')"
        dependency "'org.gradle:ext-java-lib:1.0'"
        dependency "'org.gradle:ext-android-lib:1.0'"

        then:
        artifacts('processClasspath') == [
            '/java-lib/build/classes/main',
            '/android-lib/build/classes/main',
            '/maven-repo/org/gradle/ext-java-lib/1.0/ext-java-lib-1.0.jar',
            '/android-app/transformed/ext-android-lib-1.0.aar/explodedAar/classes.jar'
        ]
    }

    // processClasses filtering and transformation

    def "processClasses includes classes folder from published java modules"() {
        when:
        dependency "'org.gradle:ext-java-lib:1.0'"

        then:
        artifacts('processClasses') == ['/android-app/transformed/ext-java-lib-1.0.jar/classes']
    }

    def "processClasses includes classes folder from published android modules"() {
        when:
        dependency "'org.gradle:ext-android-lib:1.0'"

        then:
        artifacts('processClasses') == ['/android-app/transformed/ext-android-lib-1.0.aar/explodedClassesJar']
    }

    def "processClasses can include classes folders from file dependencies"() {
        when:
        dependency "gradleApi()"

        then:
        artifacts('processClasses').size() > 20
        !output.contains('.jar\n')
        output.contains('.jar/classes')
    }

    def "processClasses includes class folders from projects and libraries"() {
        when:
        dependency "project(':java-lib')"
        dependency "project(':android-lib')"
        dependency "'org.gradle:ext-java-lib:1.0'"
        dependency "'org.gradle:ext-android-lib:1.0'"

        then:
        artifacts('processClasses') == [
            '/java-lib/build/classes/main',
            '/android-lib/build/classes/main',
            '/android-app/transformed/ext-java-lib-1.0.jar/classes',
            '/android-app/transformed/ext-android-lib-1.0.aar/explodedClassesJar'
        ]
    }

    // processManifests filtering and transformation

    def "no manifest for local java library or published java module"() {
        when:
        dependency "project(':java-lib')"
        dependency "'org.gradle:ext-java-lib:1.0'"

        then:
        artifacts('processManifests') == []
    }

    def "manifest returned for local android library"() {
        when:
        dependency "project(':android-lib')"

        then:
        artifacts('processManifests') == ['/android-lib/aar-image/AndroidManifest.xml']
    }

    def "manifest returned for published android module"() {
        when:
        dependency "'org.gradle:ext-android-lib:1.0'"

        then:
        artifacts('processManifests') == ['/android-app/transformed/ext-android-lib-1.0.aar/explodedAar/AndroidManifest.xml']
    }

    def "manifests returned for a combination of aars and jars"() {
        when:
        dependency "project(':java-lib')"
        dependency "project(':android-lib')"
        dependency "'org.gradle:ext-java-lib:1.0'"
        dependency "'org.gradle:ext-android-lib:1.0'"

        then:
        artifacts('processManifests') == [
            '/android-lib/aar-image/AndroidManifest.xml',
            '/android-app/transformed/ext-android-lib-1.0.aar/explodedAar/AndroidManifest.xml'
        ]
    }


    def dependency(String notation) {
        dependency('compileClassesAndResources', notation)
    }

    def dependency(String configuration, String notation) {
        buildFile << """
            project(':android-app') {
                dependencies {
                    $configuration $notation
                }
            }
        """
    }

    def artifacts(String configuration) {
        executer.withArgument("-Pconfiguration=$configuration")

        assert succeeds('printArtifacts')

        def result = []
        output.eachLine { line ->
            if (line.startsWith("/")) {
                result.add(line)
            }
        }
        result
    }
}
