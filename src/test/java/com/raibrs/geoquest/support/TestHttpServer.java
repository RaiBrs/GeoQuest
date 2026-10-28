package com.raibrs.geoquest.support;

import com.fasterxml.jackson.databind.*;
import com.raibrs.geoquest.http.*;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.net.http.HttpHeaders;
import java.util.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/** HTTP component tests use the actual MVC mapping, conversion and error handling. */
public final class TestHttpServer implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final MockMvc mvc;
    public record Response(int statusCode, String body, HttpHeaders headers) {}
    public TestHttpServer(MockMvc mvc) { this.mvc = mvc; }
    public static TestHttpServer start(Object... controllers) {
        return new TestHttpServer(MockMvcBuilders.standaloneSetup(controllers)
                .setControllerAdvice(new ApiErrors(), new JsonBodyLimit())
                .setMessageConverters(new org.springframework.http.converter.ByteArrayHttpMessageConverter(),
                        new org.springframework.http.converter.StringHttpMessageConverter(java.nio.charset.StandardCharsets.UTF_8),
                        new MappingJackson2HttpMessageConverter(JSON)).build());
    }
    public Response get(String path) throws Exception { return read(mvc.perform(MockMvcRequestBuilders.get(path)).andReturn()); }
    public Response post(String path, Object body) throws Exception { return postRaw(path, JSON.writeValueAsString(body), "application/json"); }
    public Response postRaw(String path, String body, String contentType) throws Exception {
        var request = MockMvcRequestBuilders.post(path).with(csrf()).content(body);
        if (contentType != null) request.contentType(contentType);
        return read(mvc.perform(request).andReturn());
    }
    private Response read(MvcResult result) throws Exception {
        var response = result.getResponse();
        Map<String, List<String>> headers = new HashMap<>();
        for (String name : response.getHeaderNames()) headers.put(name, response.getHeaders(name));
        return new Response(response.getStatus(), response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8), HttpHeaders.of(headers, (k,v) -> true));
    }
    public JsonNode readJson(Response response) throws java.io.IOException { return JSON.readTree(response.body()); }
    @Override public void close() {}
}
