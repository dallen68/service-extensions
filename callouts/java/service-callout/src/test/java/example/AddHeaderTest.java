/*
 * Copyright (c) 2024 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package example;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.truth.Truth;
import com.google.common.truth.extensions.proto.ProtoTruth;
import com.google.protobuf.ByteString;
import io.envoyproxy.envoy.config.core.v3.HeaderValue;
import io.envoyproxy.envoy.config.core.v3.HeaderValueOption;
import io.envoyproxy.envoy.service.ext_proc.v3.HttpHeaders;
import io.envoyproxy.envoy.service.ext_proc.v3.ProcessingResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import service.ServiceCallout;
import service.ServiceCalloutTools;

import java.io.IOException;
import java.lang.reflect.Method;

public class AddHeaderTest {

    private AddHeader server;

    @Before
    public void setUp() throws IOException {
        server = new AddHeader.Builder()
                .setPort(8443)
                .setHealthCheckPort(8000)
                .build();

        server.start();
    }

    @After
    public void tearDown() throws Exception {
        stopServer();
    }

    @Test
    public void testOnRequestHeaders_NoCacheClear() {
        ProcessingResponse.Builder processingResponseBuilder = ProcessingResponse.newBuilder();

        // Define headers to add
        ImmutableMap<String, String> headersToAdd = ImmutableMap.of(
                "header-1", "value-1",
                "header-2", "value-2"
        );

        // Call the header mutation utility without clearing the cache
        ServiceCalloutTools.addHeaderMutations(
                processingResponseBuilder.getRequestHeadersBuilder(),
                headersToAdd.entrySet(),
                null,  // No headers to remove
                false, // Do not clear route cache
                null   // No append action
        );

        // Build the ProcessingResponse
        ProcessingResponse response = processingResponseBuilder.build();

        // Assert the response
        Truth.assertThat(response).isNotNull();
        ProtoTruth.assertThat(response.getRequestHeaders().getResponse().getHeaderMutation().getSetHeadersList())
                .containsExactly(
                        HeaderValueOption.newBuilder()
                                .setHeader(HeaderValue.newBuilder()
                                        .setKey("header-1")
                                        .setRawValue(ByteString.copyFromUtf8("value-1"))
                                        .build())
                                .build(),
                        HeaderValueOption.newBuilder()
                                .setHeader(HeaderValue.newBuilder()
                                        .setKey("header-2")
                                        .setRawValue(ByteString.copyFromUtf8("value-2"))
                                        .build())
                                .build()
                );

        // Ensure that the cache is not cleared
        Truth.assertThat(response.getRequestHeaders().getResponse().getClearRouteCache()).isFalse();
    }

    @Test
    public void testOnResponseHeaders_RemoveMultipleHeaders() {
        ProcessingResponse.Builder processingResponseBuilder = ProcessingResponse.newBuilder();

        // Define headers to remove
        ImmutableList<String> headersToRemove = ImmutableList.of("header-1", "header-2");

        // Call the header mutation utility to remove multiple headers
        ServiceCalloutTools.addHeaderMutations(
                processingResponseBuilder.getResponseHeadersBuilder(),
                null,  // No headers to add
                headersToRemove,  // Headers to remove
                false,  // Do not clear the route cache
                null  // No append action
        );

        // Build the ProcessingResponse
        ProcessingResponse response = processingResponseBuilder.build();

        // Assert the response
        Truth.assertThat(response).isNotNull();
        Truth.assertThat(response.getResponseHeaders().getResponse().getHeaderMutation().getRemoveHeadersList())
                .containsExactly("header-1", "header-2");

        // Ensure that the cache is not cleared
        Truth.assertThat(response.getResponseHeaders().getResponse().getClearRouteCache()).isFalse();
    }

    @Test
    public void testOnRequestHeaders_AddAndRemoveHeaders() {
        ProcessingResponse.Builder processingResponseBuilder = ProcessingResponse.newBuilder();

        // Define headers to add and remove
        ImmutableMap<String, String> headersToAdd = ImmutableMap.of("new-header", "new-value");
        ImmutableList<String> headersToRemove = ImmutableList.of("old-header");

        // Call the header mutation utility to add and remove headers
        ServiceCalloutTools.addHeaderMutations(
                processingResponseBuilder.getRequestHeadersBuilder(),
                headersToAdd.entrySet(),  // Headers to add
                headersToRemove,  // Headers to remove
                true,  // Clear the route cache
                null   // No append action
        );

        // Build the ProcessingResponse
        ProcessingResponse response = processingResponseBuilder.build();

        // Assert the added and removed headers
        Truth.assertThat(response).isNotNull();
        ProtoTruth.assertThat(response.getRequestHeaders().getResponse().getHeaderMutation().getSetHeadersList())
                .containsExactly(
                        HeaderValueOption.newBuilder()
                                .setHeader(HeaderValue.newBuilder()
                                        .setKey("new-header")
                                        .setRawValue(ByteString.copyFromUtf8("new-value"))
                                        .build())
                                .build()
                );
        Truth.assertThat(response.getRequestHeaders().getResponse().getHeaderMutation().getRemoveHeadersList())
                .containsExactly("old-header");

        // Ensure the cache is cleared
        Truth.assertThat(response.getRequestHeaders().getResponse().getClearRouteCache()).isTrue();
    }

    @Test
    public void testOnRequestHeaders_AppendHeaders() {
        ProcessingResponse.Builder processingResponseBuilder = ProcessingResponse.newBuilder();

        // Define headers to append
        ImmutableMap<String, String> headersToAdd = ImmutableMap.of("existing-header", "new-value");

        // Call the header mutation utility with an append action
        ServiceCalloutTools.addHeaderMutations(
                processingResponseBuilder.getRequestHeadersBuilder(),
                headersToAdd.entrySet(),  // Headers to add
                null,  // No headers to remove
                false,  // Do not clear the route cache
                HeaderValueOption.HeaderAppendAction.APPEND_IF_EXISTS_OR_ADD  // Append action
        );

        // Build the ProcessingResponse
        ProcessingResponse response = processingResponseBuilder.build();

        // Assert that the header was appended
        Truth.assertThat(response).isNotNull();
        ProtoTruth.assertThat(response.getRequestHeaders().getResponse().getHeaderMutation().getSetHeadersList())
                .containsExactly(
                        HeaderValueOption.newBuilder()
                                .setHeader(HeaderValue.newBuilder()
                                        .setKey("existing-header")
                                        .setRawValue(ByteString.copyFromUtf8("new-value"))
                                        .build())
                                .setAppendAction(HeaderValueOption.HeaderAppendAction.APPEND_IF_EXISTS_OR_ADD)
                                .build()
                );

        // Ensure that the cache is not cleared
        Truth.assertThat(response.getRequestHeaders().getResponse().getClearRouteCache()).isFalse();
    }

    @Test
    public void testOnRequestHeaders_NoHeadersAddedOrRemoved() {
        ProcessingResponse.Builder processingResponseBuilder = ProcessingResponse.newBuilder();

        // Call the header mutation utility with no headers to add or remove
        ServiceCalloutTools.addHeaderMutations(
                processingResponseBuilder.getRequestHeadersBuilder(),
                null,  // No headers to add
                null,  // No headers to remove
                false,  // Do not clear the route cache
                null  // No append action
        );

        // Build the ProcessingResponse
        ProcessingResponse response = processingResponseBuilder.build();

        // Assert that no headers were added or removed
        Truth.assertThat(response).isNotNull();
        Truth.assertThat(response.getRequestHeaders().getResponse().getHeaderMutation().getSetHeadersList()).isEmpty();
        Truth.assertThat(response.getRequestHeaders().getResponse().getHeaderMutation().getRemoveHeadersList()).isEmpty();
    }

    @Test
    public void testOnRequestHeaders_AddsExpectedHeaders() {
        ProcessingResponse.Builder processingResponseBuilder = ProcessingResponse.newBuilder();
        HttpHeaders headers = HttpHeaders.newBuilder().build(); // Empty headers for testing

        // Call the AddHeader onRequestHeaders method directly
        server.onRequestHeaders(processingResponseBuilder, headers);

        ProcessingResponse response = processingResponseBuilder.build();

        // Verify that "request-header" and "c" have been added
        ProtoTruth.assertThat(response.getRequestHeaders().getResponse().getHeaderMutation().getSetHeadersList())
                .containsAtLeast(
                        HeaderValueOption.newBuilder()
                                .setHeader(HeaderValue.newBuilder()
                                        .setKey("request-header")
                                        .setRawValue(ByteString.copyFromUtf8("added-request"))
                                        .build())
                                .build(),
                        HeaderValueOption.newBuilder()
                                .setHeader(HeaderValue.newBuilder()
                                        .setKey("c")
                                        .setRawValue(ByteString.copyFromUtf8("d"))
                                        .build())
                                .build()
                );
        // Check that route cache is cleared
        Truth.assertThat(response.getRequestHeaders().getResponse().getClearRouteCache()).isTrue();
    }

    @Test
    public void testOnResponseHeaders_AddsAndRemovesHeaders() {
        ProcessingResponse.Builder processingResponseBuilder = ProcessingResponse.newBuilder();
        HttpHeaders headers = HttpHeaders.newBuilder().build(); // Empty headers for testing

        // Call the AddHeader onResponseHeaders method directly
        server.onResponseHeaders(processingResponseBuilder, headers);

        ProcessingResponse response = processingResponseBuilder.build();

        // Verify that "response-header" and "c" have been added, and "foo" removed
        ProtoTruth.assertThat(response.getResponseHeaders().getResponse().getHeaderMutation().getSetHeadersList())
                .containsAtLeast(
                        HeaderValueOption.newBuilder()
                                .setHeader(HeaderValue.newBuilder()
                                        .setKey("response-header")
                                        .setRawValue(ByteString.copyFromUtf8("added-response"))
                                        .build())
                                .build(),
                        HeaderValueOption.newBuilder()
                                .setHeader(HeaderValue.newBuilder()
                                        .setKey("c")
                                        .setRawValue(ByteString.copyFromUtf8("d"))
                                        .build())
                                .build()
                );
        // Check that "foo" was removed
        Truth.assertThat(response.getResponseHeaders().getResponse().getHeaderMutation().getRemoveHeadersList())
                .contains("foo");
        // Check that route cache is not cleared
        Truth.assertThat(response.getResponseHeaders().getResponse().getClearRouteCache()).isFalse();
    }

    private void stopServer() throws Exception {
        Method stopMethod = ServiceCallout.class.getDeclaredMethod("stop");
        stopMethod.setAccessible(true);
        stopMethod.invoke(server);
    }
}
