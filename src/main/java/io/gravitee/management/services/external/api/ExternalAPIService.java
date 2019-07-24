/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.management.services.external.api;

import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.event.EventManager;
import io.gravitee.common.service.AbstractService;
import io.gravitee.management.model.api.ApiEntity;
import io.gravitee.management.service.event.ApiEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import java.text.Collator;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

/**
 * @author Azize ELAMRANI (azize.elamrani at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ExternalAPIService extends AbstractService implements EventListener<ApiEvent, ApiEntity> {

    private final Logger logger = LoggerFactory.getLogger(ExternalAPIService.class);
    private static final String SHARDING_TAGS_PROPERTY = "services.external-api.tags";

    @Autowired
    private EventManager eventManager;
    @Autowired
    private Environment environment;
    @Autowired(required = false)
    private APIService apiService;

    private List<String> shardingTags;

    @Override
    protected String name() {
        return "External API Service";
    }

    @Override
    protected void doStart() throws Exception {
        if (apiService != null) {
            super.doStart();
            eventManager.subscribeForEvents(this, ApiEvent.class);
            final String systemPropertyTags = System.getProperty(SHARDING_TAGS_PROPERTY);
            final String tags = systemPropertyTags == null ? environment.getProperty(SHARDING_TAGS_PROPERTY) : systemPropertyTags;
            if (tags != null && !tags.isEmpty()) {
                shardingTags = asList(tags.split(","));
            } else {
                shardingTags = emptyList();
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
    }

    @Override
    public void onEvent(Event<ApiEvent, ApiEntity> event) {
        final ApiEntity api = event.content();
        switch (event.type()) {
            case DEPLOY:
                if (hasMatchingTags(api.getTags())) {
                    logger.info("Starting the external API id[{}] name[{}] from {}", api.getId(), api.getName(), apiService.name());
                    apiService.startAPI(api);
                }
                break;
            case UNDEPLOY:
                apiService.stopAPI(api);
                break;
            case UPDATE:
                if (hasMatchingTags(api.getTags())) {
                    logger.info("Updating the external API id[{}] name[{}] from {}", api.getId(), api.getName(), apiService.name());
                    apiService.stopAPI(api);
                    apiService.startAPI(api);
                } else {
                    apiService.stopAPI(api);
                }
                break;
        }
    }

    private boolean hasMatchingTags(Set<String> tags) {
        if (!shardingTags.isEmpty()) {
            if (tags != null) {
                final List<String> inclusionTags = shardingTags.stream()
                        .map(String::trim)
                        .filter(tag -> !tag.startsWith("!"))
                        .collect(Collectors.toList());

                final List<String> exclusionTags = shardingTags.stream()
                        .map(String::trim)
                        .filter(tag -> tag.startsWith("!"))
                        .map(tag -> tag.substring(1))
                        .collect(Collectors.toList());

                if (inclusionTags.stream().anyMatch(exclusionTags::contains)) {
                    throw new IllegalArgumentException("You must not configure a tag to be included and excluded");
                }

                return inclusionTags.stream()
                        .anyMatch(tag -> tags.stream()
                                .anyMatch(crtTag -> {
                                    final Collator collator = Collator.getInstance();
                                    collator.setStrength(Collator.NO_DECOMPOSITION);
                                    return collator.compare(tag, crtTag) == 0;
                                })
                        ) || (!exclusionTags.isEmpty() &&
                        exclusionTags.stream()
                                .noneMatch(tag -> tags.stream()
                                        .anyMatch(crtTag -> {
                                            final Collator collator = Collator.getInstance();
                                            collator.setStrength(Collator.NO_DECOMPOSITION);
                                            return collator.compare(tag, crtTag) == 0;
                                        })
                                ));
            }
            //    logger.debug("Tags {} are configured on gateway instance but not found on the API {}", tagList, api.getName());
            return false;
        }
        // no tags configured on this gateway instance
        return true;
    }
}
