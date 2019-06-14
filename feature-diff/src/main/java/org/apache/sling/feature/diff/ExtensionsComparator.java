/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.sling.feature.diff;

import java.io.IOException;
import java.util.LinkedList;

import org.apache.sling.feature.Artifact;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Extension;
import org.apache.sling.feature.Extensions;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.diff.spi.FeatureElementComparator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.zjsonpatch.JsonDiff;

final class ExtensionsComparator implements FeatureElementComparator {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void computeDiff(Feature previous, Feature current, Feature target) {
        computeDiff(previous.getExtensions(), current.getExtensions(), target);
    }

    private void computeDiff(Extensions previousExtensions, Extensions currentExtensions, Feature target) {
        for (Extension previousExtension : previousExtensions) {
            Extension currentExtension = currentExtensions.getByName(previousExtension.getName());

            if (currentExtension == null) {
                target.getPrototype().getExtensionRemovals().add(previousExtension.getName());
            } else {
                computeDiff(previousExtension, currentExtension, target);
            }
        }

        for (Extension currentExtension : currentExtensions) {
            Extension previousConfiguration = previousExtensions.getByName(currentExtension.getName());

            if (previousConfiguration == null) {
                target.getExtensions().add(currentExtension);
            }
        }
    }

    public void computeDiff(Extension previousExtension, Extension currentExtension, Feature target) {
        switch (previousExtension.getType()) {
            case ARTIFACTS:
                Extension targetExtension = new Extension(previousExtension.getType(), previousExtension.getName(), previousExtension.isRequired());

                for (Artifact previous : previousExtension.getArtifacts()) {
                    Artifact current = currentExtension.getArtifacts().getSame(previous.getId());

                    boolean add = false;

                    if (current == null || (add = !previous.getId().equals(current.getId()))) {
                        target.getPrototype().getArtifactExtensionRemovals()
                                             .computeIfAbsent(previousExtension.getName(), k -> new LinkedList<ArtifactId>())
                                             .add(previous.getId());
                    }

                    if (add) {
                        targetExtension.getArtifacts().add(current);
                    }
                }

                for (Artifact current : currentExtension.getArtifacts()) {
                    Artifact previous = previousExtension.getArtifacts().getSame(current.getId());

                    if (previous == null) {
                        targetExtension.getArtifacts().add(current);
                    }
                }

                if (!targetExtension.getArtifacts().isEmpty()) {
                    target.getExtensions().add(targetExtension);
                }

                break;

            case TEXT:
                if (!previousExtension.getText().equals(currentExtension.getText())) {
                    target.getExtensions().add(currentExtension);
                }
                break;

            case JSON:
                String previousJSON = previousExtension.getJSON();
                String currentJSON = currentExtension.getJSON();

                try {
                    JsonNode previousNode = objectMapper.readTree(previousJSON);
                    JsonNode currentNode = objectMapper.readTree(currentJSON); 
                    JsonNode patchNode = JsonDiff.asJson(previousNode, currentNode); 

                    if (patchNode.size() != 0) {
                        target.getExtensions().add(currentExtension);
                    }
                } catch (IOException e) {
                    // should not happen
                    throw new RuntimeException("A JSON parse error occurred while parsing previous '"
                                               + previousJSON
                                               + "' and current '"
                                               + currentJSON
                                               + "', see nested errors:", e);
                }
                break;

            // it doesn't happen
            default:
                break;
        }
    }

}
