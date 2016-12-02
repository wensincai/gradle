/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.api.plugins

import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.javadoc.Groovydoc
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

import static org.gradle.api.tasks.TaskDependencyMatchers.dependsOn

class GroovyPluginTest extends AbstractProjectBuilderSpec {
    private final GroovyPlugin groovyPlugin = new GroovyPlugin()

    def "applies the java plugin to the project"() {
        when:
        groovyPlugin.apply(project)

        then:
        project.plugins.hasPlugin(JavaPlugin)
    }

    def "adds groovy configuration to the project"() {
        given:
        groovyPlugin.apply(project)
        def apiConfiguration = project.configurations.getByName(JavaPlugin.API_CONFIGURATION_NAME)

        when:
        def configuration = project.configurations.getByName(JavaPlugin.COMPILE_CONFIGURATION_NAME)

        then:
        configuration.extendsFrom == [apiConfiguration] as Set
        !configuration.visible
        configuration.transitive
    }

    def "adds Groovy convention to each source set"() {
        given:
        groovyPlugin.apply(project)

        when:
        def sourceSet = project.sourceSets.main

        then:
        sourceSet.groovy.displayName == "main Groovy source"
        sourceSet.groovy.srcDirs == [project.file("src/main/groovy")] as Set

        when:
        sourceSet = project.sourceSets.test

        then:
        sourceSet.groovy.displayName == "test Groovy source"
        sourceSet.groovy.srcDirs == [project.file("src/test/groovy")] as Set
    }

    def "adds compile task for each source set"() {
        given:
        groovyPlugin.apply(project)

        when:
        def task = project.tasks['compileGroovy']

        then:
        task instanceof GroovyCompile
        task.description == 'Compiles the main Groovy source.'
        dependsOn(JavaPlugin.COMPILE_JAVA_TASK_NAME).matches(task)

        when:
        task = project.tasks['compileTestGroovy']

        then:
        task instanceof GroovyCompile
        task.description == 'Compiles the test Groovy source.'
        dependsOn(JavaPlugin.COMPILE_TEST_JAVA_TASK_NAME, JavaPlugin.CLASSES_TASK_NAME).matches(task)
    }

    def "dependencies of Java plugin tasks include Groovy compile tasks"() {
        given:
        groovyPlugin.apply(project)

        when:
        def task = project.tasks[JavaPlugin.CLASSES_TASK_NAME]
        then:
        dependsOn(JavaPlugin.COMPILE_JAVA_TASK_NAME, JavaPlugin.PROCESS_RESOURCES_TASK_NAME, 'compileGroovy').matches(task)

        when:
        task = project.tasks[JavaPlugin.TEST_CLASSES_TASK_NAME]
        then:
        dependsOn(JavaPlugin.PROCESS_TEST_RESOURCES_TASK_NAME, JavaPlugin.COMPILE_TEST_JAVA_TASK_NAME, 'compileTestGroovy').matches(task)
    }

    def "adds standard tasks to the project"() {
        given:
        groovyPlugin.apply(project)

        when:
        project.sourceSets.main.groovy.srcDirs(temporaryFolder.getTestDirectory())
        temporaryFolder.file("SomeFile.groovy").touch()
        def task = project.tasks[GroovyPlugin.GROOVYDOC_TASK_NAME]

        then:
        task instanceof Groovydoc
        task.destinationDir == new File(project.docsDir, 'groovydoc')
        task.source.files == project.sourceSets.main.groovy.files
        task.docTitle == project.extensions.getByType(ReportingExtension).apiDocTitle
        task.windowTitle == project.extensions.getByType(ReportingExtension).apiDocTitle
    }
}
