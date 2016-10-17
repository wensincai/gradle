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

package org.gradle.internal.nativeintegration.jansi

import org.gradle.util.Requires
import spock.lang.Specification

import static org.gradle.internal.nativeintegration.jansi.JansiLibraryResolver.*
import static org.gradle.testfixtures.NativeTestPrecondition.isNotMacOsXOrLinuxOrWindows
import static org.gradle.util.TestPrecondition.*

class JansiLibraryResolverTest extends Specification {

    def resolver = new JansiLibraryResolver()

    @Requires(MAC_OS_X)
    def "jansi library can be resolved for MacOSX"() {
        when:
        JansiLibrary jansiLibrary = resolver.resolve()

        then:
        jansiLibrary.platform == JansiOperatingSystemSupport.MAC_OS_X.identifier
        jansiLibrary.filename == MAC_OSX_LIB_FILENAME
        jansiLibrary.resourcePath ==  "/META-INF/native/" + jansiLibrary.path
    }

    @Requires(LINUX)
    def "jansi library can be resolved for Linux"() {
        when:
        JansiLibrary jansiLibrary = resolver.resolve()

        then:
        jansiLibrary.platform.startsWith(JansiOperatingSystemSupport.LINUX.identifier)
        jansiLibrary.filename == LINUX_LIB_FILENAME
        jansiLibrary.resourcePath ==  "/META-INF/native/" + jansiLibrary.path
    }

    @Requires(WINDOWS)
    def "jansi library can be resolved for Windows"() {
        when:
        JansiLibrary jansiLibrary = resolver.resolve()

        then:
        jansiLibrary.platform.startsWith(JansiOperatingSystemSupport.WINDOWS.identifier)
        jansiLibrary.filename == WINDOWS_LIB_FILENAME
        jansiLibrary.resourcePath ==  "/META-INF/native/" + jansiLibrary.path
    }

    @Requires(adhoc = { isNotMacOsXOrLinuxOrWindows() })
    def "jansi library cannot be resolved for unsupported OS"() {
        when:
        JansiLibrary jansiLibrary = resolver.resolve()

        then:
        !jansiLibrary
    }
}