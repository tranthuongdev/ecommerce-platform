package com.athanas.ecommerce.common.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Map;

@Component
public class ProblemDetailFactory {

    public ProblemDetail of(HttpStatus status, String detail, String type) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(type));
        return problem;
    }

    public ProblemDetail validation(Map<String, String> fieldErrors) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation Failed");
        problem.setProperty("errors", fieldErrors);
        return problem;
    }
}
