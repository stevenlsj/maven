package org.apache.maven.execution;

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

import java.io.File;
import java.util.*;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.bridge.MavenRepositorySystem;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.toolchain.model.PersistedToolchains;
import org.apache.maven.toolchain.model.ToolchainModel;
import org.codehaus.plexus.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;

//
// All of this needs to go away and be couched in terms of the execution request
//
//
// Settings in core
//

/**
 * Assists in populating an execution request for invocation of Maven.
 */
@Named
public class DefaultMavenExecutionRequestPopulator implements MavenExecutionRequestPopulator {

    private final MavenRepositorySystem repositorySystem;
    public static Logger logger = LoggerFactory.getILoggerFactory().getLogger("DefaultMavenExecutionRequestPopulator");

    @Inject
    public DefaultMavenExecutionRequestPopulator(MavenRepositorySystem repositorySystem) {
        this.repositorySystem = repositorySystem;
    }

    @Override
    public MavenExecutionRequest populateFromToolchains(MavenExecutionRequest request, PersistedToolchains toolchains)
        throws MavenExecutionRequestPopulationException {
        if (toolchains != null) {
            Map<String, List<ToolchainModel>> groupedToolchains = new HashMap<>(2);

            for (ToolchainModel model : toolchains.getToolchains()) {
                if (!groupedToolchains.containsKey(model.getType())) {
                    groupedToolchains.put(model.getType(), new ArrayList<ToolchainModel>());
                }

                groupedToolchains.get(model.getType()).add(model);
            }

            request.setToolchains(groupedToolchains);
        }
        return request;
    }

    @Override
    public MavenExecutionRequest populateDefaults(MavenExecutionRequest request)
        throws MavenExecutionRequestPopulationException {
        baseDirectory(request);

        localRepository(request);

        populateDefaultPluginGroups(request);

        injectDefaultRepositories(request);

        injectDefaultPluginRepositories(request);

        return request;
    }

    //
    //
    //

    private void populateDefaultPluginGroups(MavenExecutionRequest request) {
        request.addPluginGroup("org.apache.maven.plugins");
        request.addPluginGroup("org.codehaus.mojo");
        logger.info("pluginGroup:{}", request.getPluginGroups());
    }

    private void injectDefaultRepositories(MavenExecutionRequest request)
        throws MavenExecutionRequestPopulationException {
        logger.info("getRemoteRepositories:{}", JSON.toJSONString(request.getRemoteRepositories()));
        Set<String> definedRepositories = repositorySystem.getRepoIds(request.getRemoteRepositories());
        logger.info("definedRepositories:{}", definedRepositories);
        if (!definedRepositories.contains(RepositorySystem.DEFAULT_REMOTE_REPO_ID)) {
            try {
                ArtifactRepository defaultRemoteRepository = repositorySystem.createDefaultRemoteRepository(request);
                logger.info("createDefaultRemoteRepository:{}", JSON.toJSONString(defaultRemoteRepository));
                request.addRemoteRepository(defaultRemoteRepository);
            } catch (Exception e) {
                throw new MavenExecutionRequestPopulationException("Cannot create default remote repository.", e);
            }
        }
    }

    private void injectDefaultPluginRepositories(MavenExecutionRequest request)
        throws MavenExecutionRequestPopulationException {
        Set<String> definedRepositories = repositorySystem.getRepoIds(request.getPluginArtifactRepositories());

        if (!definedRepositories.contains(RepositorySystem.DEFAULT_REMOTE_REPO_ID)) {
            try {
                request.addPluginArtifactRepository(repositorySystem.createDefaultRemoteRepository(request));
            } catch (Exception e) {
                throw new MavenExecutionRequestPopulationException("Cannot create default remote repository.", e);
            }
        }
    }

    private void localRepository(MavenExecutionRequest request) throws MavenExecutionRequestPopulationException {

        // ------------------------------------------------------------------------
        // Local Repository
        //
        // 1. Use a value has been passed in via the configuration
        // 2. Use value in the resultant settings
        // 3. Use default value
        // ------------------------------------------------------------------------

        if (request.getLocalRepository() == null) {
            request.setLocalRepository(createLocalRepository(request));
        }

        if (request.getLocalRepositoryPath() == null) {
            request.setLocalRepositoryPath(new File(request.getLocalRepository().getBasedir()).getAbsoluteFile());
        }
    }

    // ------------------------------------------------------------------------
    // Artifact Transfer Mechanism
    // ------------------------------------------------------------------------

    private ArtifactRepository createLocalRepository(MavenExecutionRequest request)
        throws MavenExecutionRequestPopulationException {
        String localRepositoryPath = null;

        if (request.getLocalRepositoryPath() != null) {
            localRepositoryPath = request.getLocalRepositoryPath().getAbsolutePath();
        }

        if (StringUtils.isEmpty(localRepositoryPath)) {
            localRepositoryPath = RepositorySystem.defaultUserLocalRepository.getAbsolutePath();
        }

        try {
            return repositorySystem.createLocalRepository(request, new File(localRepositoryPath));
        } catch (Exception e) {
            throw new MavenExecutionRequestPopulationException("Cannot create local repository.", e);
        }
    }

    private void baseDirectory(MavenExecutionRequest request) {
        if (request.getBaseDirectory() == null && request.getPom() != null) {
            request.setBaseDirectory(request.getPom().getAbsoluteFile().getParentFile());
        }
    }
}
