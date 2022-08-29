/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates.
 *
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
package io.quarkus.reactivemessaging.http;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import java.util.Collections;
import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.smallrye.reactive.messaging.ce.CloudEventMetadata;

public class CEResourceMockService implements QuarkusTestResourceLifecycleManager {

    private WireMockServer cloudEventService;

    @Override
    public Map<String, String> start() {
        cloudEventService = new WireMockServer(8282);
        cloudEventService.start();
        cloudEventService.stubFor(post(urlEqualTo("/"))
                .withHeader("ce-" + CloudEventMetadata.CE_ATTRIBUTE_TYPE, WireMock.equalTo("aType"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("aBody")));
        return Collections.emptyMap();
    }

    @Override
    public void stop() {
        cloudEventService.stop();
    }

    @Override
    public void inject(Object testInstance) {
        ((ReactiveMessagingHttpTest) testInstance).cloudEventService = cloudEventService;
    }
}
