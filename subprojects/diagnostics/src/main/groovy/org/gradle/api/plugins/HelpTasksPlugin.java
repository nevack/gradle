/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.plugins;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.internal.component.BuildableJavaComponent;
import org.gradle.api.internal.component.ComponentRegistry;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.TaskContainerInternal;
import org.gradle.api.reporting.components.ComponentReport;
import org.gradle.api.tasks.diagnostics.*;
import org.gradle.configuration.Help;

import java.util.concurrent.Callable;

/**
 * Adds various reporting tasks that provide information about the project.
 */
@Incubating
public class HelpTasksPlugin implements Plugin<ProjectInternal> {

    public static final String HELP_GROUP = "help";
    public static final String PROPERTIES_TASK = "properties";
    public static final String DEPENDENCIES_TASK = "dependencies";
    public static final String DEPENDENCY_INSIGHT_TASK = "dependencyInsight";
    public static final String COMPONENTS_TASK = "components";

    public void apply(final ProjectInternal project) {
        final TaskContainerInternal tasks = project.getTasks();

        tasks.addPlaceholderAction(ProjectInternal.HELP_TASK, Help.class, new Action<Help>() {
            public void execute(Help task) {
                task.setDescription("Displays a help message.");
                task.setGroup(HELP_GROUP);
                task.setImpliesSubProjects(true);
            }
        });

        tasks.addPlaceholderAction(ProjectInternal.PROJECTS_TASK, ProjectReportTask.class, new Action<ProjectReportTask>() {
            public void execute(ProjectReportTask task) {
                task.setDescription("Displays the sub-projects of " + project + ".");
                task.setGroup(HELP_GROUP);
                task.setImpliesSubProjects(true);
            }
        });

        tasks.addPlaceholderAction(ProjectInternal.TASKS_TASK, TaskReportTask.class, new Action<TaskReportTask>() {
            public void execute(TaskReportTask task) {
                String description;
                if (project.getChildProjects().isEmpty()) {
                    description = "Displays the tasks runnable from " + project + ".";
                } else {
                    description = "Displays the tasks runnable from " + project + " (some of the displayed tasks may belong to subprojects).";
                }
                task.setDescription(description);
                task.setGroup(HELP_GROUP);
                task.setImpliesSubProjects(true);
            }
        });

        tasks.addPlaceholderAction(PROPERTIES_TASK, PropertyReportTask.class, new Action<PropertyReportTask>() {
            public void execute(PropertyReportTask task) {
                task.setDescription("Displays the properties of " + project + ".");
                task.setGroup(HELP_GROUP);
                task.setImpliesSubProjects(true);
            }
        });

        tasks.addPlaceholderAction(DEPENDENCY_INSIGHT_TASK, DependencyInsightReportTask.class, new Action<DependencyInsightReportTask>() {
            public void execute(final DependencyInsightReportTask task) {
                task.setDescription("Displays the insight into a specific dependency in " + project + ".");
                task.setGroup(HELP_GROUP);
                task.setImpliesSubProjects(true);
                new DslObject(task).getConventionMapping().map("configuration", new Callable<Object>() {
                    public Object call() {
                        BuildableJavaComponent javaProject = project.getServices().get(ComponentRegistry.class).getMainComponent();
                        return javaProject == null ? null : javaProject.getCompileDependencies();
                    }
                });
            }
        });


        tasks.addPlaceholderAction(DEPENDENCIES_TASK, DependencyReportTask.class, new Action<DependencyReportTask>() {
            public void execute(DependencyReportTask task) {
                task.setDescription("Displays all dependencies declared in " + project + ".");
                task.setGroup(HELP_GROUP);
                task.setImpliesSubProjects(true);
            }
        });

        tasks.addPlaceholderAction(COMPONENTS_TASK, ComponentReport.class, new Action<ComponentReport>() {
            public void execute(ComponentReport task) {
                task.setDescription("Displays the components produced by " + project + ". [incubating]");
                task.setGroup(HELP_GROUP);
                task.setImpliesSubProjects(true);
            }
        });
    }
}