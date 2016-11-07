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

public class ExpandAARTransformIntegrationTest extends AbstractAndroidPluginDependencyResolutionTest {

    def setup() {
        createAppSubproject('android-app')
        createAndroidLibSubproject('android-lib')
        createJavaLibSubproject('java-lib')

        publishJar("ext-java-lib")
        publishAAR("ext-android-lib")
    }

    def "compile classpath directly references jars from local java libraries"() {
        when:
        dependency "android-app", "project(':java-lib')"

        then:
        classpath '/java-lib/build/libs/java-lib.jar'

        and:
        executed ':java-lib:jar'
    }

    def "local java libraries can expose classes directory directly as classpath artifact"() {
        when:
        dependency "android-app", "project(':java-lib')"

        and:
        classpathArtifacts "java-lib", "compile(compileJava.destinationDir)"

        then:
        classpath '/java-lib/build/classes/main'

        and:
        executed ":java-lib:classes"

        // TODO We shouldn't be building the jar in this case, but this will require a much deeper change
//        notExecuted ":java-lib:jar"
    }

    def "compile classpath includes classes dir from local android libraries"() {
        when:
        dependency "android-app", "project(':android-lib')"

        then:
        classpath '/transformed/android-lib.aar/classes.jar'

        and:
        executed ":android-lib:aar"
    }

    def "local android library can expose classes directory directly as classpath artifact"() {
        when:
        dependency "android-app", "project(':android-lib')"

        and:
        classpathArtifacts "android-lib", "compile(file('classes'))"

        then:
        classpath '/android-lib/classes'

        and:
        executed ":android-lib:classes"

        // TODO We shouldn't be building the aar in this case, but this will require a much deeper change
//        notExecuted ":android-lib:aar"
    }

    def "compile classpath includes jars from published java modules"() {
        when:
        dependency "android-app", "'org.gradle:ext-java-lib:1.0'"

        then:
        classpath '/maven-repo/org/gradle/ext-java-lib/1.0/ext-java-lib-1.0.jar'
    }

    def "compile classpath includes classes jar from published android modules"() {
        when:
        dependency "android-app", "'org.gradle:ext-android-lib:1.0'"

        then:
        classpath '/transformed/ext-android-lib-1.0.aar/classes.jar'
    }

    def "compile dependencies include a combination of aars and jars"() {
        when:
        dependency "android-app", "project(':java-lib')"
        dependency "android-app", "project(':android-lib')"
        dependency "android-app", "'org.gradle:ext-java-lib:1.0'"
        dependency "android-app", "'org.gradle:ext-android-lib:1.0'"

        then:
        classpath '/java-lib/build/libs/java-lib.jar',
            '/transformed/android-lib.aar/classes.jar',
            '/maven-repo/org/gradle/ext-java-lib/1.0/ext-java-lib-1.0.jar',
            '/transformed/ext-android-lib-1.0.aar/classes.jar'
    }

    def "no manifest for local java library or published java module"() {
        when:
        dependency "android-app", "project(':java-lib')"
        dependency "android-app", "'org.gradle:ext-java-lib:1.0'"

        then:
        manifest()
    }

    def "manifest returned for local android library"() {
        when:
        dependency "android-app", "project(':android-lib')"

        then:
        manifest '/transformed/android-lib.aar/AndroidManifest.xml'
    }

    def "manifest returned for published android module"() {
        when:
        dependency "android-app", "'org.gradle:ext-android-lib:1.0'"

        then:
        manifest '/transformed/ext-android-lib-1.0.aar/AndroidManifest.xml'
    }

    def "manifests returned for a combination of aars and jars"() {
        when:
        dependency "android-app", "project(':java-lib')"
        dependency "android-app", "project(':android-lib')"
        dependency "android-app", "'org.gradle:ext-java-lib:1.0'"
        dependency "android-app", "'org.gradle:ext-android-lib:1.0'"

        then:
        manifest '/transformed/android-lib.aar/AndroidManifest.xml',
            '/transformed/ext-android-lib-1.0.aar/AndroidManifest.xml'
    }
}
