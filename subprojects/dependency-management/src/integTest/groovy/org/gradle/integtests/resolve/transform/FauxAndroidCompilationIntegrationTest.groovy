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

public class FauxAndroidCompilationIntegrationTest extends AbstractDependencyResolutionTest {

    def setup() {
        settingsFile << """
    rootProject.name = 'fake-android-build'
    include 'java-lib'
    include 'android-lib'
    include 'android-app'
"""
        buildFile << """
import java.nio.file.Files
import java.nio.file.Paths

    project(':java-lib') {
        apply plugin: 'java'
    }
    project(':android-lib') {
        apply plugin: 'base'

        configurations {
            compile
        }
        configurations['default'].extendsFrom(configurations['compile'])

        task aar(type: Zip) {
            from file('aar-content')
            destinationDir = project.buildDir
            extension = 'aar'
        }

        artifacts {
            compile aar
        }
    }
    project(':android-app') {
        apply plugin: 'java'

        repositories {
            maven { url '${mavenRepo.uri}' }
        }

        def compileClasspath = configurations.compile.transform('aar', AarClassesExtractor) {
            outputDirectory = project.file("transformed")
            antBuilder = project.ant
        }

        task fakeCompile {
            dependsOn compileClasspath
            doLast {
                compileClasspath.each { println it.absolutePath - rootDir }
            }
        }
    }

    class AarClassesExtractor extends org.gradle.api.artifacts.transform.DependencyTransform {
        def antBuilder
        File output

        void transform(File input) {
            if (input.name.endsWith('.aar')) {
                outputIsClassesDirOfExtractedArchive(input)
            } else {
                outputIsExactCopy(input)
            }
        }

        private void outputIsClassesDirOfExtractedArchive(def input) {
            def explodedAar = new File(outputDirectory, input.name)

            if (!explodedAar.exists()) {
                antBuilder.unzip(src:  input,
                                 dest: explodedAar,
                                 overwrite: "false")
            }

            output = new File(explodedAar, "classes")
            assert output.exists()
        }

        // TODO:DAZ We shouldn't need to copy: should only tranform AAR file types.
        private void outputIsExactCopy(def input) {
            output = new File(outputDirectory, input.name)

            if (!output.exists()) {
                Files.copy(input.toPath(), output.toPath())
            }
        }
    }

"""
        file('android-lib/aar-content/classes/foo.txt') << "something"
        file('android-lib/aar-content/classes/bar/baz.txt') << "something"
        file('android-app').mkdirs()
    }

    def "compile classpath directly references jars from local java libraries"() {
        when:
        dependency "project(':java-lib')"

        then:
        classpath '/java-lib/build/libs/java-lib.jar'
    }

    def "compile classpath includes classes dir from local android libraries"() {
        when:
        dependency "project(':android-lib')"

        then:
        classpath '/transformed/android-lib.aar/classes'
    }

    def "compile classpath includes jars from published java modules"() {
        when:
        mavenRepo.module("org.gradle", "ext-java-lib").publish()
        dependency "'org.gradle:ext-java-lib:1.0'"

        then:
        classpath '/maven-repo/org/gradle/ext-java-lib/1.0/ext-java-lib-1.0.jar'
    }

    def "compile classpath includes classes dir from published android modules"() {
        when:
        def module = mavenRepo.module("org.gradle", "ext-android-lib").hasType('aar').publish()
        assert module.artifactFile.delete()
        file("android-lib/aar-content").zipTo(module.artifactFile)

        dependency "'org.gradle:ext-android-lib:1.0'"

        then:
        classpath '/transformed/ext-android-lib-1.0.aar/classes'
    }

    def dependency(String notation) {
        buildFile << """
    project(':android-app') {
        dependencies {
            compile ${notation}
        }
    }
"""
    }

    void classpath(String... classpathElements) {
        assert succeeds('fakeCompile')

        for (String classpathElement : classpathElements) {
            outputContains(classpathElement)
        }
    }

}
