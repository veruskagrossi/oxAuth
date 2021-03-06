package org.xdi.oxauth.audit.debug;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.BooleanUtils;
import org.slf4j.Logger;
import org.xdi.oxauth.audit.debug.entity.HttpRequest;
import org.xdi.oxauth.audit.debug.entity.HttpResponse;
import org.xdi.oxauth.audit.debug.wrapper.RequestWrapper;
import org.xdi.oxauth.audit.debug.wrapper.ResponseWrapper;
import org.xdi.oxauth.model.configuration.AppConfiguration;

import javax.inject.Inject;
import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;

/**
 * Created by eugeniuparvan on 5/10/17.
 */
@WebFilter(urlPatterns = {"/*"})
public class ServletLoggingFilter implements Filter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        OBJECT_MAPPER.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
    }

    @Inject
    private Logger log;

    @Inject
    private AppConfiguration appConfiguration;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
            throw new ServletException("LoggingFilter just supports HTTP requests");
        }
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if (!BooleanUtils.toBoolean(appConfiguration.getHttpLoggingEnabled())) {
            chain.doFilter(httpRequest, httpResponse);
            return;
        }
        Set<String> excludedPaths = appConfiguration.getHttpLoggingExludePaths();
        if (!CollectionUtils.isEmpty(excludedPaths)) {
            for (String excludedPath : excludedPaths) {
                String requestURI = httpRequest.getRequestURI();
                if (requestURI.startsWith(excludedPath)) {
                    chain.doFilter(httpRequest, httpResponse);
                    return;
                }
            }
        }

        RequestWrapper requestWrapper = new RequestWrapper(httpRequest);
        ResponseWrapper responseWrapper = new ResponseWrapper(httpResponse);

        log.debug(getRequestDescription(requestWrapper));
        chain.doFilter(requestWrapper, responseWrapper);
        log.debug(getResponseDescription(responseWrapper));
    }

    @Override
    public void destroy() {

    }

    protected String getRequestDescription(RequestWrapper requestWrapper) {
        HttpRequest httpRequest = new HttpRequest();
        httpRequest.setSenderIP(requestWrapper.getLocalAddr());
        httpRequest.setMethod(requestWrapper.getMethod());
        httpRequest.setPath(requestWrapper.getRequestURI());
        httpRequest.setParams(requestWrapper.isFormPost() ? null : requestWrapper.getParameters());
        httpRequest.setHeaders(requestWrapper.getHeaders());
        httpRequest.setBody(requestWrapper.getContent());
        try {
            return OBJECT_MAPPER.writeValueAsString(httpRequest);
        } catch (JsonProcessingException e) {
            log.warn("Cannot serialize Request to JSON", e);
            return null;
        }
    }

    protected String getResponseDescription(ResponseWrapper responseWrapper) {
        HttpResponse httpResponse = new HttpResponse();
        httpResponse.setStatus(responseWrapper.getStatus());
        httpResponse.setHeaders(responseWrapper.getHeaders());
        try {
            return OBJECT_MAPPER.writeValueAsString(httpResponse);
        } catch (JsonProcessingException e) {
            log.warn("Cannot serialize Response to JSON", e);
            return null;
        }
    }
}
