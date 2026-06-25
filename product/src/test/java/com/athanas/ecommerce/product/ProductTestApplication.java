package com.athanas.ecommerce.product;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = "com.athanas.ecommerce")
@ConfigurationPropertiesScan("com.athanas.ecommerce")
public class ProductTestApplication {
}
