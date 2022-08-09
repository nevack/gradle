/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.enterprise


import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class GradleEnterprisePluginBackgroundJobExecutorIntegrationTest extends AbstractIntegrationSpec {

    def plugin = new GradleEnterprisePluginCheckInFixture(testDirectory, mavenRepo, createExecuter())

    def setup() {
        settingsFile << plugin.pluginManagement() << plugin.plugins()
        plugin.publishDummyPlugin(executer)
    }

    def "background job executor runs jobs submitted at configuration time"() {
        given:
        buildFile << """
            def serviceRef = gradle.extensions.serviceRef
            def service = serviceRef.get()
            def requiredServices = service._requiredServices
            println "backgroundJobExecutor.execute = " + requiredServices.backgroundJobExecutor.execute({
                println "backgroundJobExecutor.executed"
           }, { e -> println "backgroundJobExecutor.status = \${e.message}"})

            task check {}
        """

        when:
        succeeds("check")

        then:
        outputContains("backgroundJobExecutor.execute = true")
        plugin.assertBackgroundJobExecutedBeforeShutdownCompleted(output, "backgroundJobExecutor.executed")
    }

    def "background job executor forwards failures of jobs submitted at configuration time"() {
        given:
        buildFile << """
            def serviceRef = gradle.extensions.serviceRef
            def service = serviceRef.get()
            def requiredServices = service._requiredServices
            println "backgroundJobExecutor.execute = " + requiredServices.backgroundJobExecutor.execute({
                throw new RuntimeException("job failed")
            }, { e -> println "backgroundJobExecutor.status = \${e.message}"})

            task check {}
        """

        when:
        succeeds("check")

        then:
        outputContains("backgroundJobExecutor.execute = true")
        plugin.assertBackgroundJobFailureCallbackInvokedAtShutdown(output, "backgroundJobExecutor.status = job failed")
    }

    def "background job executor runs jobs submitted at execution time"() {
        given:
        buildFile << """
            def serviceRef = gradle.extensions.serviceRef
            task check {
                doLast {
                    def service = serviceRef.get()
                    def requiredServices = service._requiredServices
                    println "backgroundJobExecutor.execute = " + requiredServices.backgroundJobExecutor.execute({
                        println "backgroundJobExecutor.executed"
                    }, { e -> println "backgroundJobExecutor.status = \${e.message}"})
                }
            }
        """

        when:
        succeeds("check")

        then:
        outputContains("backgroundJobExecutor.execute = true")
        plugin.assertBackgroundJobExecutedBeforeShutdownCompleted(output, "backgroundJobExecutor.executed")

        when:
        succeeds("check")

        then:
        outputContains("backgroundJobExecutor.execute = true")
        plugin.assertBackgroundJobExecutedBeforeShutdownCompleted(output, "backgroundJobExecutor.executed")
    }

    def "background job executor forwards failures of jobs submitted at execution time"() {
        given:
        buildFile << """
            def serviceRef = gradle.extensions.serviceRef
            task check {
                doLast {
                    def service = serviceRef.get()
                    def requiredServices = service._requiredServices
                    println "backgroundJobExecutor.execute = " + requiredServices.backgroundJobExecutor.execute({
                        throw new RuntimeException("job failed")
                    }, { e -> println "backgroundJobExecutor.status = \${e.message}"})
                }
            }
        """

        when:
        succeeds("check")

        then:
        outputContains("backgroundJobExecutor.execute = true")
        plugin.assertBackgroundJobFailureCallbackInvokedAtShutdown(output, "backgroundJobExecutor.status = job failed")

        when:
        succeeds("check")

        then:
        outputContains("backgroundJobExecutor.execute = true")
        plugin.assertBackgroundJobFailureCallbackInvokedAtShutdown(output, "backgroundJobExecutor.status = job failed")
    }
}
