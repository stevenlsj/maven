package org.apache.maven.lifecycle.internal;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE
 * file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.LifecycleNotFoundException;
import org.apache.maven.lifecycle.LifecyclePhaseNotFoundException;
import org.apache.maven.plugin.*;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.prefix.NoPluginFoundForPrefixException;
import org.apache.maven.plugin.version.PluginVersionResolutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;

/**
 * <p>
 * Calculates the task segments in the build
 * </p>
 * <strong>NOTE:</strong> This class is not part of any public api and can be changed or deleted without prior notice.
 *
 * @since 3.0
 * @author Benjamin Bentmann
 * @author Jason van Zyl
 * @author jdcasey
 * @author Kristian Rosenvold (extracted class)
 */
@Component(role = LifecycleTaskSegmentCalculator.class)
public class DefaultLifecycleTaskSegmentCalculator implements LifecycleTaskSegmentCalculator {
    @Requirement
    private MojoDescriptorCreator mojoDescriptorCreator;

    @Requirement
    private LifecyclePluginResolver lifecyclePluginResolver;
    public static Logger logger = LoggerFactory.getILoggerFactory().getLogger("DefaultLifecycleTaskSegmentCalculator");

    public DefaultLifecycleTaskSegmentCalculator() {}

    @Override
    public List<TaskSegment> calculateTaskSegments(MavenSession session)
        throws PluginNotFoundException, PluginResolutionException, PluginDescriptorParsingException,
        MojoNotFoundException, NoPluginFoundForPrefixException, InvalidPluginDescriptorException,
        PluginVersionResolutionException, LifecyclePhaseNotFoundException, LifecycleNotFoundException {

        MavenProject rootProject = session.getTopLevelProject();

        List<String> tasks = session.getGoals();
        logger.info("session goals:{}", tasks);
        if ((tasks == null || tasks.isEmpty()) && !StringUtils.isEmpty(rootProject.getDefaultGoal())) {
            tasks = Arrays.asList(StringUtils.split(rootProject.getDefaultGoal()));
            logger.info("getDefaultGoal:{}", tasks);
        }

        return calculateTaskSegments(session, tasks);
    }

    @Override
    public List<TaskSegment> calculateTaskSegments(MavenSession session, List<String> tasks)
        throws PluginNotFoundException, PluginResolutionException, PluginDescriptorParsingException,
        MojoNotFoundException, NoPluginFoundForPrefixException, InvalidPluginDescriptorException,
        PluginVersionResolutionException {
        List<TaskSegment> taskSegments = new ArrayList<>(tasks.size());

        TaskSegment currentSegment = null;

        for (String task : tasks) {
            if (isGoalSpecification(task)) {
                // "pluginPrefix:goal" or "groupId:artifactId[:version]:goal"

                lifecyclePluginResolver.resolveMissingPluginVersions(session.getTopLevelProject(), session);

                MojoDescriptor mojoDescriptor =
                    mojoDescriptorCreator.getMojoDescriptor(task, session, session.getTopLevelProject());

                boolean aggregating = mojoDescriptor.isAggregator() || !mojoDescriptor.isProjectRequired();

                if (currentSegment == null || currentSegment.isAggregating() != aggregating) {
                    currentSegment = new TaskSegment(aggregating);
                    taskSegments.add(currentSegment);
                }

                currentSegment.getTasks().add(new GoalTask(task));
            } else {
                // lifecycle phase

                if (currentSegment == null || currentSegment.isAggregating()) {
                    currentSegment = new TaskSegment(false);
                    taskSegments.add(currentSegment);
                }

                currentSegment.getTasks().add(new LifecycleTask(task));
            }
        }
        logger.info("taskSegments:{}", JSON.toJSONString(taskSegments));
        return taskSegments;
    }

    public boolean requiresProject(MavenSession session) {
        List<String> goals = session.getGoals();
        if (goals != null) {
            for (String goal : goals) {
                if (!isGoalSpecification(goal)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isGoalSpecification(String task) {
        return task.indexOf(':') >= 0;
    }

}