package io.gravitee.management.services.external.api;

import io.gravitee.management.model.api.ApiEntity;

/**
 * @author Azize ELAMRANI (azize.elamrani at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface APIService {
    String name();
    void startAPI(ApiEntity api);
    void stopAPI(ApiEntity api);
}
