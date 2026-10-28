package com.raibrs.geoquest.http;

import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import java.io.*;
import java.lang.reflect.Type;

@ControllerAdvice
public final class JsonBodyLimit extends RequestBodyAdviceAdapter {
    @Override public boolean supports(MethodParameter parameter, Type type, Class<? extends HttpMessageConverter<?>> converter) { return true; }
    @Override public HttpInputMessage beforeBodyRead(HttpInputMessage input, MethodParameter parameter, Type type,
                                                    Class<? extends HttpMessageConverter<?>> converter) throws IOException {
        byte[] body = input.getBody().readNBytes(4097);
        if (body.length > 4096) throw new IllegalArgumentException("Request body is too large.");
        return new HttpInputMessage() {
            public InputStream getBody() { return new ByteArrayInputStream(body); }
            public HttpHeaders getHeaders() { return input.getHeaders(); }
        };
    }
}
