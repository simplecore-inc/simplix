package dev.simplecore.simplix.web.exception;

import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import dev.simplecore.simplix.web.advice.SimpliXExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.servlet.error.AbstractErrorController;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Controller
@RequestMapping("${server.error.path:/error}")
public class SimpliXErrorController extends AbstractErrorController {

    private final SimpliXExceptionHandler<SimpliXApiResponse<Object>> exceptionHandler;
    private final ObjectMapper objectMapper;
    private final ServerProperties serverProperties;

    public SimpliXErrorController(
        ErrorAttributes errorAttributes,
        SimpliXExceptionHandler<SimpliXApiResponse<Object>> exceptionHandler,
        ObjectMapper objectMapper,
        ServerProperties serverProperties
    ) {
        super(errorAttributes);
        this.exceptionHandler = exceptionHandler;
        this.objectMapper = objectMapper;
        this.serverProperties = serverProperties;
    }

    @RequestMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public void handleEventStreamError(HttpServletRequest request, HttpServletResponse response) {
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    @RequestMapping(produces = MediaType.TEXT_HTML_VALUE)
    public ModelAndView handleErrorHtml(HttpServletRequest request, HttpServletResponse response) {
        // Get status code
        Object statusObj = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = statusObj != null ? (int) statusObj : HttpStatus.INTERNAL_SERVER_ERROR.value();
        
        // Get error message and exception
        String message = (String) request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        Throwable throwable = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        
        // Create ModelAndView
        ModelAndView modelAndView = new ModelAndView("error");
        
        // Force response status
        response.setStatus(status);
        
        // Basic error information
        modelAndView.addObject("status", status);
        modelAndView.addObject("error", HttpStatus.valueOf(status).getReasonPhrase());
        modelAndView.addObject("timestamp", System.currentTimeMillis());
        modelAndView.addObject("path", request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI));
        
        // Collect unique error messages from exception chain
        if (throwable != null) {
            List<String> errorMessages = new ArrayList<>();
            Set<String> uniqueMessages = new HashSet<>();
            Throwable current = throwable;
            
            while (current != null) {
                if (current.getMessage() != null) {
                    String msg = current.getMessage();
                    if (uniqueMessages.add(msg)) {
                        errorMessages.add(msg);
                    }
                }
                current = current.getCause();
            }
            
            if (!errorMessages.isEmpty()) {
                modelAndView.addObject("errorMessages", errorMessages);
                if (message == null || message.isEmpty()) {
                    message = errorMessages.get(0);
                }
            }
            
            // Add stack trace if enabled
            if (isIncludeStackTrace(request)) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                throwable.printStackTrace(pw);
                modelAndView.addObject("trace", sw.toString());
            }
        }
        
        modelAndView.addObject("message", message != null ? message : "No message available");
        
        return modelAndView;
    }

    private boolean isIncludeStackTrace(HttpServletRequest request) {
        switch (serverProperties.getError().getIncludeStacktrace()) {
            case ALWAYS:
                return true;
            case ON_PARAM:
                return request.getParameter("trace") != null;
            default:
                return false;
        }
    }

    @RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public void handleErrorJson(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        // Determine status code
        Integer statusCode = (Integer) request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (statusCode == null) {
            statusCode = HttpStatus.INTERNAL_SERVER_ERROR.value();
        }
        
        // Get original exception
        Throwable throwable = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        
        SimpliXApiResponse<Object> errorResponse;
        if (throwable != null) {
            // Handle the original exception
            Exception ex = throwable instanceof Exception ? (Exception) throwable : new Exception(throwable);
            errorResponse = exceptionHandler.handleException(ex, request);
        } else {
            // Get error details when throwable is not available
            String errorMessage = (String) request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
            String requestUri = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
            String errorServletName = (String) request.getAttribute(RequestDispatcher.ERROR_SERVLET_NAME);
            
            StringBuilder detail = new StringBuilder();
            if (requestUri != null) {
                detail.append("Failed URL: ").append(requestUri);
            }
            if (errorServletName != null) {
                detail.append(detail.length() > 0 ? ", " : "");
            }
            
            errorResponse = SimpliXApiResponse.error(
                errorMessage != null ? errorMessage : "Unknown error occurred",
                "UnknownError",
                detail.length() > 0 ? detail.toString() : null
            );
        }
        
        // Set response status and content type
        response.setStatus(statusCode);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
} 