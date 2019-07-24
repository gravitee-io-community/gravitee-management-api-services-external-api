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
package io.gravitee.management.services.external.api.mulesoft;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.common.util.Maps;
import io.gravitee.management.model.api.ApiEntity;
import io.gravitee.management.model.parameters.Key;
import io.gravitee.management.service.ParameterService;
import io.gravitee.management.services.external.api.APIService;
import io.gravitee.management.services.external.api.mulesoft.model.MuleSoftApiEntity;
import io.gravitee.management.services.external.api.mulesoft.model.MuleSoftAssetEntity;
import io.gravitee.management.services.external.api.mulesoft.model.MuleSoftEndpointEntity;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.*;
import static java.lang.String.format;
import static org.apache.http.HttpHeaders.*;

/**
 * @author Azize ELAMRANI (azize.elamrani at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MuleSoftAPIService implements APIService {

    private final Logger logger = LoggerFactory.getLogger(MuleSoftAPIService.class);

    @Value("${services.external-api.endpoint:#{null}}")
    private String endpoint;
    @Value("${services.external-api.username:#{null}}")
    private String username;
    @Value("${services.external-api.password:#{null}}")
    private String password;
    @Value("${services.external-api.mulesoft.organization:#{null}}")
    private String organization;
    @Value("${services.external-api.mulesoft.environment:#{null}}")
    private String environment;

    @Autowired
    private ParameterService parameterService;

    private String authorization;
    private final CloseableHttpClient httpClient;
    private final ObjectMapper mapper;

    public MuleSoftAPIService() {
        httpClient = HttpClients.createDefault();
        mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public String name() {
        return "MuleSoft";
    }

    @Override
    public void startAPI(final ApiEntity api) {
        connect();
        if (existsAPI(api)) {
            updateAPI(api);
        } else {
            createAPI(api);
        }
    }

    @Override
    public void stopAPI(final ApiEntity api) {
        connect();
        deleteAPI(api);
    }

    private void connect() {
        if (authorization == null) {
            try {
                final HttpEntityEnclosingRequestBase request = new HttpPost(endpoint + "/accounts/login");
                request.setHeader(ACCEPT, "application/json");
                request.setHeader(CONTENT_TYPE, "application/json");

                request.setEntity(new StringEntity(mapper.writeValueAsString(
                        Maps.builder().put("username", username).put("password", password).build()),
                        ContentType.APPLICATION_JSON));

                httpClient.execute(request, response -> {
                    int status = response.getStatusLine().getStatusCode();
                    final String entity = EntityUtils.toString(response.getEntity());
                    if (status < 200 || status >= 300) {
                        logger.error(format("Error while trying to connect to MuleSoft API: Status[%s] - %s",
                                status, entity));
                    } else {
                        final Map entityMap = mapper.readValue(entity, Map.class);
                        authorization = entityMap.get("token_type") + " " + entityMap.get("access_token");
                    }
                    return null;
                });
            } catch (final IOException ioe) {
                logger.error("Error while trying to connect to the MuleSoft API", ioe);
            }
        }
    }

    private boolean existsAPI(final ApiEntity api) {
        try {
            return existsMuleSoftAPI(getMuleSoftAPIId(api));
        } catch (final IOException ioe) {
            logger.error("Error while trying to call the MuleSoft API", ioe);
            throw new IllegalStateException("Error while trying to check if the MuleSoft API exists", ioe);
        }
    }

    private String getMuleSoftAPIId(ApiEntity api) throws IOException {
        final HttpRequestBase request = new HttpGet(endpoint +
                format("/exchange/api/v1/assets/%s/%s?includeSnapshots=true", organization, api.getId()));
        request.setHeader(AUTHORIZATION, authorization);
        request.setHeader(ACCEPT, "application/json");
        request.setHeader(CONTENT_TYPE, "application/json");
        return httpClient.execute(request, response -> {
            if (response.getStatusLine().getStatusCode() == 200) {
                final MuleSoftAssetEntity entity = mapper.readValue(EntityUtils.toString(response.getEntity()), MuleSoftAssetEntity.class);
                final List<MuleSoftAssetEntity> instances = entity.getInstances();
                if (!instances.isEmpty()) {
                    return instances.iterator().next().getId();
                }
            }
            return null;
        });
    }

    private boolean existsMuleSoftAPI(final String apiId) throws IOException {
        final HttpRequestBase request = new HttpGet(endpoint +
                format("/apimanager/api/v1/organizations/%s/environments/%s/apis/%s",
                        organization, environment, apiId));
        request.setHeader(AUTHORIZATION, authorization);
        request.setHeader(ACCEPT, "application/json");
        request.setHeader(CONTENT_TYPE, "application/json");
        return httpClient.execute(request, response -> response.getStatusLine().getStatusCode() == 200);
    }

    private void createAPI(final ApiEntity api) {
        try {
            final HttpEntityEnclosingRequestBase request = new HttpPost(endpoint + "/exchange/api/v1/assets");
            request.setHeader(AUTHORIZATION, authorization);
            final MuleSoftAssetEntity muleSoftAssetEntity = convert(api);
            request.setEntity(getMultipartData(muleSoftAssetEntity));
            httpClient.execute(request, response -> {
                int status = response.getStatusLine().getStatusCode();
                if (status < 200 || status >= 300) {
                    if (status == 409) {
                        createMuleSoftAPI(api, muleSoftAssetEntity);
                    } else {
                        logger.error(format("Error while trying to create MuleSoft asset: Status[%s] - %s",
                                status, EntityUtils.toString(response.getEntity())));
                    }
                } else {
                    createMuleSoftAPI(api, muleSoftAssetEntity);
                }
                return null;
            });
        } catch (final IOException ioe) {
            logger.error("Error while trying to call the MuleSoft API", ioe);
        }
    }

    private void createMuleSoftAPI(final ApiEntity api, final MuleSoftAssetEntity muleSoftAssetEntity) throws IOException {
        final HttpEntityEnclosingRequestBase request = new HttpPost(endpoint +
                format("/apimanager/api/v1/organizations/%s/environments/%s/apis", organization, environment));
        request.setHeader(AUTHORIZATION, authorization);
        request.setHeader(ACCEPT, "application/json");
        request.setHeader(CONTENT_TYPE, "application/json");
        request.setEntity(new StringEntity(mapper.writeValueAsString(convert(api, muleSoftAssetEntity)), ContentType.APPLICATION_JSON));

        httpClient.execute(request, response -> {
            int status = response.getStatusLine().getStatusCode();
            if (status < 200 || status >= 300) {
                logger.error(format("Error while trying to create MuleSoft API: Status[%s] - %s",
                        status, EntityUtils.toString(response.getEntity())));
            } else {
                logger.info(format("API '%s' created with success on MuleSoft", api.getName()));
            }
            return null;
        });
    }

    private HttpEntity getMultipartData(final MuleSoftAssetEntity muleSoftAssetEntity) throws IOException {
        final MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();
        final Map<String, String> values = mapper.readValue(mapper.writeValueAsString(muleSoftAssetEntity), Map.class);
        for (final Map.Entry<String, String> entry : values.entrySet()) {
            final String value = entry.getValue();
            if (value != null) {
                multipartEntityBuilder.addTextBody(entry.getKey(), value);
            }
        }
        return multipartEntityBuilder.build();
    }

    private void updateAPI(final ApiEntity api) {
        try {
            final HttpEntityEnclosingRequestBase request = new HttpPatch(endpoint +
                    format("/apimanager/api/v1/organizations/%s/environments/%s/apis/%s",
                            organization, environment, getMuleSoftAPIId(api)));
            request.setHeader(AUTHORIZATION, authorization);
            request.setHeader(ACCEPT, "application/json");
            request.setHeader(CONTENT_TYPE, "application/json");
            request.setEntity(new StringEntity(mapper.writeValueAsString(convert(api)), ContentType.APPLICATION_JSON));
            httpClient.execute(request, response -> {
                int status = response.getStatusLine().getStatusCode();
                if (status < 200 || status >= 300) {
                    logger.error(format("Error while trying to update MuleSoft API: Status[%s] - %s",
                            status, EntityUtils.toString(response.getEntity())));
                }
                return null;
            });
        } catch (final IOException ioe) {
            logger.error("Error while trying to call the MuleSoft API", ioe);
        }
    }

    private void deleteAPI(final ApiEntity api) {
        if (existsAPI(api)) {
            try {
                final HttpRequestBase request = new HttpDelete(endpoint +
                        format("/apimanager/api/v1/organizations/%s/environments/%s/apis/%s",
                                organization, environment, getMuleSoftAPIId(api)));
                request.setHeader(AUTHORIZATION, authorization);
                request.setHeader(ACCEPT, "application/json");
                httpClient.execute(request, response -> {
                    int status = response.getStatusLine().getStatusCode();
                    if (status < 200 || status >= 300) {
                        logger.error(format("Error while trying to delete MuleSoft API: Status[%s] - %s",
                                status, EntityUtils.toString(response.getEntity())));
                    }
                    return null;
                });
            } catch (final IOException ioe) {
                logger.error("Error while trying to call the MuleSoft API", ioe);
            }
        }
    }

    private MuleSoftAssetEntity convert(final ApiEntity api) {
        final MuleSoftAssetEntity muleSoftAssetEntity = new MuleSoftAssetEntity();
        muleSoftAssetEntity.setAssetId(api.getId());
        muleSoftAssetEntity.setOrganizationId(organization);
        muleSoftAssetEntity.setGroupId(organization);
        muleSoftAssetEntity.setVersion("1.0.0");
        muleSoftAssetEntity.setApiVersion(api.getVersion());
        muleSoftAssetEntity.setName(api.getName());
        muleSoftAssetEntity.setClassifier("http");
        return muleSoftAssetEntity;
    }

    private MuleSoftApiEntity convert(final ApiEntity api, final MuleSoftAssetEntity muleSoftAssetEntity) {
        final MuleSoftApiEntity muleSoftApiEntity = new MuleSoftApiEntity();
        muleSoftApiEntity.setSpec(muleSoftAssetEntity);

        String description = api.getDescription();
        if (description.length() > 255) {
            description = description.substring(0, 252) + "...";
        }
        muleSoftApiEntity.setInstanceLabel(description);

        final MuleSoftEndpointEntity endpoint = new MuleSoftEndpointEntity();
        endpoint.setUri(parameterService.find(Key.PORTAL_ENTRYPOINT) + api.getProxy().getContextPath());
        endpoint.setProxyUri(api.getProxy().getGroups().iterator().next().getEndpoints().iterator().next().getTarget());
        muleSoftApiEntity.setEndpoint(endpoint);
        return muleSoftApiEntity;
    }
}
