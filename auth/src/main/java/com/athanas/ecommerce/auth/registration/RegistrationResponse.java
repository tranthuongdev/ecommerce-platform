package com.athanas.ecommerce.auth.registration;

import java.time.Instant;
import java.util.UUID;

public record RegistrationResponse(UUID id, String email, String fullName, Instant createdAt) {}
