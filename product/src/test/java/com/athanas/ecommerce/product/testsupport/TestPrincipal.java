package com.athanas.ecommerce.product.testsupport;

import com.athanas.ecommerce.common.security.PrincipalView;

import java.util.Set;
import java.util.UUID;

public record TestPrincipal(UUID userId, Set<String> roleNames) implements PrincipalView {}
