package com.premise.data.proxy.config;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class RequestResponseLogFilter implements GlobalFilter, Ordered {

    private static final String MAGIC_HEADER = "x-debug";

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestResponseLogFilter.class);

    private static final String START_TIME = "startTime";

    private static final String HTTP_SCHEME = "http";

    private static final String HTTPS_SCHEME = "https";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        List<String> debugHeader = exchange.getRequest().getHeaders().get(MAGIC_HEADER);
//        if (!LOGGER.isDebugEnabled() && debugHeader == null) {
//            // DO NOTHING
//            return chain.filter(exchange);
//        }
        ServerHttpRequest request = exchange.getRequest();
        URI requestURI = request.getURI();
        String scheme = requestURI.getScheme();
        if (debugHeader != null) {
            String debugHeaderContent = debugHeader.get(0);
            if (!debugHeaderContent.equalsIgnoreCase("true") && !requestURI.getPath().toLowerCase().contains(debugHeaderContent.toLowerCase())) {
                return chain.filter(exchange);
            }
        }
        if ((!HTTP_SCHEME.equalsIgnoreCase(scheme) && !HTTPS_SCHEME.equals(scheme))) {
            return chain.filter(exchange);
        }
        long startTime = System.currentTimeMillis();
        exchange.getAttributes().put(START_TIME, startTime);
        logRequest(request, exchange.getAttribute("cachedRequestBodyObject"));
        return chain.filter(exchange.mutate().response(logResponse(exchange)).build());
    }

    @Override
    public int getOrder() {
        return Integer.MIN_VALUE;
    }

    private void logRequest(ServerHttpRequest request, String body) {
        URI requestURI = request.getURI();
        String scheme = requestURI.getScheme();
        HttpHeaders headers = request.getHeaders();
        LOGGER.info("Request Scheme:{},Path:{}", scheme, requestURI.getPath());
        LOGGER.info("Request Method:{},IP:{},Host:{}", request.getMethod(), request.getRemoteAddress(), requestURI.getHost());
        headers.forEach((key, value) -> LOGGER.debug("Request Headers:Key->{},Value->{}", key, value));
        MultiValueMap<String, String> queryParams = request.getQueryParams();
        if (!queryParams.isEmpty()) {
            queryParams.forEach((key, value) -> LOGGER.info("Request Query Param :Key->({}),Value->({})", key, value));
        }
        MediaType contentType = headers.getContentType();
        long length = headers.getContentLength();
        LOGGER.info("Request ContentType:{},Content Length:{}", contentType, length);
        if (body != null) {
            LOGGER.info("Request Body:{}", body);
        }
    }

    private ServerHttpResponseDecorator logResponse(ServerWebExchange exchange) {
        ServerHttpResponse origResponse = exchange.getResponse();
        Long startTime = exchange.getAttribute(START_TIME);
        LOGGER.info("Response HttpStatus:{}", origResponse.getStatusCode());
        HttpHeaders headers = origResponse.getHeaders();
        headers.forEach((key, value) -> LOGGER.debug("[RequestLogFilter]Headers:Key->{},Value->{}", key, value));
        MediaType contentType = headers.getContentType();
        long length = headers.getContentLength();
        LOGGER.info("Response ContentType:{},Content Length:{}", contentType, length);
        Long executeTime = (System.currentTimeMillis() - startTime);
        LOGGER.info("Response Original Path:{},Cost:{} ms", exchange.getRequest().getURI().getPath(), executeTime);
        DataBufferFactory bufferFactory = origResponse.bufferFactory();

        return new ServerHttpResponseDecorator(origResponse) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                if (body instanceof Flux) {
                    Flux<? extends DataBuffer> fluxBody = (Flux<? extends DataBuffer>) body;

                    return super.writeWith(fluxBody.map(dataBuffer -> {
                        try {
                            byte[] content = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(content);
                            var bodyContent = new String(content, StandardCharsets.UTF_8);
                            LOGGER.info("Response:{}", bodyContent);
                            return bufferFactory.wrap(content);
                        } finally {
                            DataBufferUtils.release(dataBuffer);
                        }
                    }));

                }
                return super.writeWith(body);

            }
        };

    }
}
