/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.karaf.cellar.obr;

import org.apache.felix.bundlerepository.Repository;
import org.apache.felix.bundlerepository.Resource;
import org.apache.karaf.cellar.core.Configurations;
import org.apache.karaf.cellar.core.Group;
import org.apache.karaf.cellar.core.Synchronizer;
import org.apache.karaf.cellar.core.event.EventType;
import org.osgi.service.cm.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Dictionary;
import java.util.Set;

/**
 * OBR URL synchronizer.
 */
public class ObrUrlSynchronizer extends ObrSupport implements Synchronizer {

    private static final transient Logger LOGGER = LoggerFactory.getLogger(ObrUrlSynchronizer.class);

    @Override
    public void init() {
        Set<Group> groups = groupManager.listLocalGroups();
        if (groups != null && !groups.isEmpty()) {
            for (Group group : groups) {
                sync(group);
            }
        }
    }

    @Override
    public void destroy() {
        super.destroy();
    }

    /**
     * Sync node and cluster states, depending of the sync policy.
     *
     * @param group the target cluster group.
     */
    @Override
    public void sync(Group group) {
        String policy = getSyncPolicy(group);
        if (policy != null && policy.equalsIgnoreCase("cluster")) {
            LOGGER.debug("CELLAR OBR: sync policy is set as 'cluster' for cluster group " + group.getName());
            if (clusterManager.listNodesByGroup(group).size() == 1 && clusterManager.listNodesByGroup(group).contains(clusterManager.getNode())) {
                LOGGER.debug("CELLAR OBR: node is the first and only member of the group, pushing state");
                push(group);
            } else {
                LOGGER.debug("CELLAR OBR: pulling state");
                pull(group);
            }
        }
        if (policy != null && policy.equalsIgnoreCase("node")) {
            LOGGER.debug("CELLAR OBR: sync policy is set as 'cluster' for cluster group " + group.getName());
            push(group);
        }
    }

    /**
     * Pull the OBR URLs from a cluster group to update the local state.
     *
     * @param group the cluster group.
     */
    @Override
    public void pull(Group group) {
        if (group != null) {
            String groupName = group.getName();
            Set<String> clusterUrls = clusterManager.getSet(Constants.URLS_DISTRIBUTED_SET_NAME + Configurations.SEPARATOR + groupName);
            ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
                if (clusterUrls != null && !clusterUrls.isEmpty()) {
                    for (String url : clusterUrls) {
                        try {
                            obrService.addRepository(url);
                            LOGGER.debug("CELLAR OBR: add OBR repository URL {}", url);
                        } catch (Exception e) {
                            LOGGER.error("CELLAR OBR: unable to add the OBR repository URL {}", url, e);
                        }
                    }
                }
            } finally {
                Thread.currentThread().setContextClassLoader(originalClassLoader);
            }
        }
    }

    /**
     * Push the local OBR URLs to a cluster group.
     *
     * @param group the cluster group.
     */
    @Override
    public void push(Group group) {
        if (group != null) {
            String groupName = group.getName();
            Set<String> clusterUrls = clusterManager.getSet(Constants.URLS_DISTRIBUTED_SET_NAME + Configurations.SEPARATOR + groupName);

            ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
                Repository[] repositories = obrService.listRepositories();
                for (Repository repository : repositories) {
                    if (isAllowed(group, Constants.URLS_CONFIG_CATEGORY, repository.getURI().toString(), EventType.OUTBOUND)) {
                        clusterUrls.add(repository.getURI().toString());
                        // update the OBR bundles in the cluster group
                        Set<ObrBundleInfo> clusterBundles = clusterManager.getSet(Constants.BUNDLES_DISTRIBUTED_SET_NAME + Configurations.SEPARATOR + groupName);
                        Resource[] resources = repository.getResources();
                        for (Resource resource : resources) {
                            ObrBundleInfo info = new ObrBundleInfo(resource.getPresentationName(), resource.getSymbolicName(), resource.getVersion().toString());
                            clusterBundles.add(info);
                            // TODO fire event to the other nodes ?
                        }
                    } else {
                        LOGGER.debug("CELLAR OBR: URL {} is BLOCKED OUTBOUND for cluster group {}", repository.getURI().toString(), groupName);
                    }
                }
            } finally {
                Thread.currentThread().setContextClassLoader(originalClassLoader);
            }
        }
    }

    /**
     * Get the OBR sync policy for the given cluster group.
     *
     * @param group the cluster group.
     * @return the current OBR sync policy.
     */
    @Override
    public String getSyncPolicy(Group group) {
        Boolean result = Boolean.FALSE;
        String groupName = group.getName();
        try {
            Configuration configuration = configurationAdmin.getConfiguration(Configurations.GROUP, null);
            Dictionary<String, Object> properties = configuration.getProperties();
            if (properties != null) {
                String propertyKey = groupName + Configurations.SEPARATOR + Constants.URLS_CONFIG_CATEGORY + Configurations.SEPARATOR + Configurations.SYNC;
                return properties.get(propertyKey).toString();
            }
        } catch (IOException e) {
            LOGGER.error("CELLAR OBR: error while retrieving the sync policy", e);
        }
        return "disabled";
    }

}
