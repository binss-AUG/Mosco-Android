package com.vn.jet.mosco.spinserver.exception;

import com.vn.jet.mosco.spinserver.dto.ApiResponse;
import com.vn.jet.mosco.spinserver.utils.MessageConstants;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private boolean isSseResponse(HttpServletResponse response) {
        String ct = response.getContentType();
        return ct != null && ct.contains("text/event-stream");
    }

    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindingException(ServletRequestBindingException ex, HttpServletResponse response) {
        if (isSseResponse(response)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        if (ex.getMessage() != null && ex.getMessage().contains("userId")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(401, MessageConstants.AUTH_REQUIRED));
        }
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex, HttpServletResponse response) {
        if (isSseResponse(response)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(404, ex.getMessage()));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(BadRequestException ex, HttpServletResponse response) {
        if (isSseResponse(response)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex, HttpServletResponse response) {
        logger.error("Unhandled Exception caught by GlobalExceptionHandler", ex);
        if (isSseResponse(response)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, ex.getMessage() != null ? ex.getMessage() : "Internal Server Error"));
    }
}
