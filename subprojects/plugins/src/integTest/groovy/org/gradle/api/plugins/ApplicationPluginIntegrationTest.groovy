/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

class ApplicationPluginIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        createSampleProjectSetup()
    }

    def "can generate start scripts with minimal user configuration"() {
        when:
        succeeds('startScripts')

        then:
        File unixStartScript = assertGeneratedUnixStartScript()
        String unixStartScriptContent = unixStartScript.text
        unixStartScriptContent.contains('##  sample start up script for UN*X')
        unixStartScriptContent.contains('DEFAULT_JVM_OPTS=""')
        unixStartScriptContent.contains('APP_NAME="sample"')
        unixStartScriptContent.contains('CLASSPATH=\$APP_HOME/lib/sample.jar')
        unixStartScriptContent.contains('exec "\$JAVACMD" "\$@"')
        File windowsStartScript = assertGeneratedWindowsStartScript()
        String windowsStartScriptContentText = windowsStartScript.text
        windowsStartScriptContentText.contains('@rem  sample startup script for Windows')
        windowsStartScriptContentText.contains('set DEFAULT_JVM_OPTS=')
        windowsStartScriptContentText.contains('set CLASSPATH=%APP_HOME%\\lib\\sample.jar')
        windowsStartScriptContentText.contains('"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %SAMPLE_OPTS%  -classpath "%CLASSPATH%" org.gradle.test.Main %CMD_LINE_ARGS%')
    }

    def "can generate starts script generation with custom user configuration"() {
        given:
        buildFile << """
applicationName = 'myApp'
applicationDefaultJvmArgs = ["-Dgreeting.language=en", "-DappId=\${project.name - ':'}"]
"""

        when:
        succeeds('startScripts')

        then:
        File unixStartScript = assertGeneratedUnixStartScript('myApp')
        String unixStartScriptContent = unixStartScript.text
        unixStartScriptContent.contains('##  myApp start up script for UN*X')
        unixStartScriptContent.contains('APP_NAME="myApp"')
        unixStartScriptContent.contains('DEFAULT_JVM_OPTS=\'"-Dgreeting.language=en" "-DappId=sample"\'')
        unixStartScriptContent.contains('CLASSPATH=\$APP_HOME/lib/sample.jar')
        unixStartScriptContent.contains('exec "\$JAVACMD" "\$@"')
        File windowsStartScript = assertGeneratedWindowsStartScript('myApp.bat')
        String windowsStartScriptContentText = windowsStartScript.text
        windowsStartScriptContentText.contains('@rem  myApp startup script for Windows')
        windowsStartScriptContentText.contains('set DEFAULT_JVM_OPTS="-Dgreeting.language=en" "-DappId=sample"')
        windowsStartScriptContentText.contains('set CLASSPATH=%APP_HOME%\\lib\\sample.jar')
        windowsStartScriptContentText.contains('"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %MY_APP_OPTS%  -classpath "%CLASSPATH%" org.gradle.test.Main %CMD_LINE_ARGS%')
    }

    def "can change template file for default start script generators"() {
        given:
        file('customUnixStartScript.txt') << '${applicationName} start up script for UN*X'
        file('customWindowsStartScript.txt') << '${applicationName} start up script for Windows'

        buildFile << """
startScripts {
    applicationName = 'myApp'
    unixStartScriptGenerator.template = resources.text.fromFile(file('customUnixStartScript.txt'))
    windowsStartScriptGenerator.template = resources.text.fromFile(file('customWindowsStartScript.txt'))
}
"""
        when:
        succeeds('startScripts')

        then:
        File unixStartScript = assertGeneratedUnixStartScript('myApp')
        unixStartScript.text == 'myApp start up script for UN*X'
        File windowsStartScript = assertGeneratedWindowsStartScript('myApp.bat')
        windowsStartScript.text == 'myApp start up script for Windows'
    }

    def "can use custom start script generators"() {
        given:
        buildFile << '''
startScripts {
    applicationName = 'myApp'
    unixStartScriptGenerator = new CustomUnixStartScriptGenerator()
    windowsStartScriptGenerator = new CustomWindowsStartScriptGenerator()
}

class CustomUnixStartScriptGenerator implements ScriptGenerator {
    void generateScript(JavaAppStartScriptGenerationDetails details, Writer destination) {
        destination << "\${details.applicationName} start up script for UN*X"
    }
}

class CustomWindowsStartScriptGenerator implements ScriptGenerator {
    void generateScript(JavaAppStartScriptGenerationDetails details, Writer destination) {
        destination << "\${details.applicationName} start up script for Windows"
    }
}
'''
        when:
        succeeds('startScripts')

        then:
        File unixStartScript = assertGeneratedUnixStartScript('myApp')
        unixStartScript.text == 'myApp start up script for UN*X'
        File windowsStartScript = assertGeneratedWindowsStartScript('myApp.bat')
        windowsStartScript.text == 'myApp start up script for Windows'
    }

    @Requires(TestPrecondition.UNIX_DERIVATIVE)
    def "can execute generated Unix start script"() {
        when:
        succeeds('installDist')

        then:
        file('build/install/sample').exists()

        when:
        ExecutionResult result = runViaUnixStartScript()

        then:
        result.output.contains('Hello World!')
    }

    @Requires(TestPrecondition.UNIX_DERIVATIVE)
    def "can execute generated Unix start script using JAVA_HOME with spaces"() {
        given:
        def testJavaHome = file("build/java home with spaces")
        testJavaHome.createLink(Jvm.current().javaHome)

        when:
        succeeds('installDist')

        then:
        file('build/install/sample').exists()

        when:
        ExecutionResult result = runViaUnixStartScriptWithJavaHome(testJavaHome.absolutePath)

        then:
        result.output.contains('Hello World!')

        cleanup:
        testJavaHome.usingNativeTools().deleteDir() //remove symlink
    }

    @Requires(TestPrecondition.UNIX_DERIVATIVE)
    public void "java PID equals script PID"() {
        given:
        succeeds('installDist')
        def binFile = file('build/install/sample/bin/sample')
        binFile.text = """echo Script PID: \$\$

$binFile.text
"""

        when:
        ExecutionResult result = runViaUnixStartScript()
        def pids = result.output.findAll(/PID: \d+/)

        then:
        assert pids.size() == 2
        assert pids[0] == pids[1]
    }

    @Requires(TestPrecondition.WINDOWS)
    def "can execute generated Windows start script"() {
        when:
        succeeds('installDist')

        then:
        file('build/install/sample').exists()

        when:
        ExecutionResult result = runViaWindowsStartScript()

        then:
        result.output.contains('Hello World!')
    }

    ExecutionResult runViaUnixStartScript() {
        TestFile startScriptDir = file('build/install/sample/bin')
        buildFile << """
task execStartScript(type: Exec) {
    workingDir '$startScriptDir.canonicalPath'
    commandLine './sample'
}
"""
        return succeeds('execStartScript')
    }

    ExecutionResult runViaUnixStartScriptWithJavaHome(String javaHome) {
        TestFile startScriptDir = file('build/install/sample/bin')

        buildFile << """
task execStartScript(type: Exec) {
    workingDir '$startScriptDir.canonicalPath'
    commandLine './sample'
    environment JAVA_HOME: "$javaHome"
}
"""
        return succeeds('execStartScript')
    }

    ExecutionResult runViaWindowsStartScript() {
        TestFile startScriptDir = file('build/install/sample/bin')
        String escapedStartScriptDir = startScriptDir.canonicalPath.replaceAll('\\\\', '\\\\\\\\')
        buildFile << """
task execStartScript(type: Exec) {
    workingDir '$escapedStartScriptDir'
    commandLine 'cmd', '/c', 'sample.bat'
}
"""
        return succeeds('execStartScript')
    }

    def "compile only dependencies are not included in distribution"() {
        given:
        mavenRepo.module('org.gradle.test', 'compile', '1.0').publish()
        mavenRepo.module('org.gradle.test', 'compileOnly', '1.0').publish()

        and:
        buildFile << """
repositories {
    maven { url '$mavenRepo.uri' }
}

dependencies {
    compile 'org.gradle.test:compile:1.0'
    compileOnly 'org.gradle.test:compileOnly:1.0'
}
"""
        when:
        run "installDist"

        then:
        file('build/install/sample/lib').allDescendants() == ['sample.jar', 'compile-1.0.jar'] as Set
    }

    def "can use APP_HOME in DEFAULT_JVM_OPTS with custom start script"() {
        given:
        buildFile << """
applicationDefaultJvmArgs = ["-DappHomeSystemProp=REPLACE_THIS_WITH_APP_HOME"]

startScripts {
    doLast {
        unixScript.text = unixScript.text.replace("REPLACE_THIS_WITH_APP_HOME", "'\\\$APP_HOME'")
        windowsScript.text = windowsScript.text.replace("REPLACE_THIS_WITH_APP_HOME", '%APP_HOME%')
    }
}
"""
        when:
        succeeds('installDist')
        and:
        ExecutionResult result = runViaStartScript()

        then:
        result.assertOutputContains("App Home: ${file('build/install/sample').absolutePath}")
    }

    ExecutionResult runViaStartScript() {
        OperatingSystem.current().isWindows() ? runViaWindowsStartScript() : runViaUnixStartScript()
    }

    private void createSampleProjectSetup() {
        createMainClass()
        populateBuildFile()
        populateSettingsFile()
    }

    private void createMainClass() {
        file('src/main/java/org/gradle/test/Main.java') << """
package org.gradle.test;

public class Main {
    public static void main(String[] args) {
        System.out.println("App Home: " + System.getProperty("appHomeSystemProp"));
        System.out.println("App PID: " + java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0]);
        System.out.println("Hello World!");
    }
}
"""
    }

    private void populateBuildFile() {
        buildFile << """
apply plugin: 'application'

mainClassName = 'org.gradle.test.Main'
"""
    }

    private void populateSettingsFile() {
        settingsFile << """
rootProject.name = 'sample'
"""
    }

    private File assertGeneratedUnixStartScript(String filename = 'sample') {
        File startScript = getGeneratedStartScript(filename)
        assert startScript.exists()
        assert startScript.canRead()
        assert startScript.canExecute()
        startScript
    }

    private File assertGeneratedWindowsStartScript(String filename = 'sample.bat') {
        File startScript = getGeneratedStartScript(filename)
        assert startScript.exists()
        startScript
    }

    private File getGeneratedStartScript(String filename) {
        File scriptOutputDir = file('build/scripts')
        new File(scriptOutputDir, filename)
    }
}
