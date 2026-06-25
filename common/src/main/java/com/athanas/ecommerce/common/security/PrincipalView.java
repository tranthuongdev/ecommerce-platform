package com.athanas.ecommerce.common.security;

import java.util.Set;
import java.util.UUID;

public interface PrincipalView {
    UUID userId();
    Set<String> roleNames();
}
