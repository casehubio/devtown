package io.casehub.devtown.app.spi;

import java.util.Map;

final class SyntheticPatches {

    private SyntheticPatches() {}

    static final Map<String, String> PATCHES = Map.ofEntries(

        // ── Auth / Security (PR #101 scenario) ──────────────────────

        Map.entry("src/auth/TokenService.java",
            "@@ -130,6 +130,20 @@ public class TokenService {\n"
            + "+    private static final int TOKEN_EXPIRY_SECONDS = 3600;\n"
            + "+\n"
            + "+    public String issueToken(User user, Set<String> scopes) {\n"
            + "+        var key = loadSigningKey();\n"
            + "+        return Jwts.builder()\n"
            + "+                .setSubject(user.id())\n"
            + "+                .claim(\"scopes\", scopes)\n"
            + "+                .setExpiration(Date.from(Instant.now().plusSeconds(TOKEN_EXPIRY_SECONDS)))\n"
            + "+                .signWith(key, SignatureAlgorithm.HS256)\n"
            + "+                .compact();\n"
            + "+    }\n"
            + "+\n"
            + "+    public boolean validateToken(String token) {\n"
            + "+        var claims = Jwts.parserBuilder().setSigningKey(loadSigningKey()).build()\n"
            + "+                .parseClaimsJws(token).getBody();\n"
            + "+        return claims.getExpiration().after(new Date());\n"
            + "+    }\n"),

        Map.entry("src/auth/SessionManager.java",
            "@@ -45,8 +45,15 @@ public class SessionManager {\n"
            + "-    public Session createSession(String userId) {\n"
            + "-        return new Session(userId, UUID.randomUUID().toString());\n"
            + "+    public Session createSession(String userId, HttpServletRequest request) {\n"
            + "+        var sessionId = UUID.randomUUID().toString();\n"
            + "+        var session = new Session(userId, sessionId);\n"
            + "+        session.setIpAddress(request.getRemoteAddr());\n"
            + "+        session.setUserAgent(request.getHeader(\"User-Agent\"));\n"
            + "+        sessionStore.put(sessionId, session);\n"
            + "+        return session;\n"
            + "     }\n"
            + "+\n"
            + "+    public void invalidateSession(String sessionId) {\n"
            + "+        sessionStore.remove(sessionId);\n"
            + "+    }\n"),

        Map.entry("src/auth/RBAC.java",
            "@@ -20,6 +20,12 @@ public class RBAC {\n"
            + "+    public boolean hasPermission(User user, String resource, String action) {\n"
            + "+        var roles = user.roles();\n"
            + "+        return roles.stream()\n"
            + "+                .flatMap(r -> permissionStore.getPermissions(r).stream())\n"
            + "+                .anyMatch(p -> p.matches(resource, action));\n"
            + "+    }\n"),

        Map.entry("src/config/SecurityConfig.java",
            "@@ -10,4 +10,9 @@ public class SecurityConfig {\n"
            + "+    @ConfigProperty(name = \"security.cors.allowed-origins\")\n"
            + "+    List<String> allowedOrigins;\n"
            + "+\n"
            + "+    @ConfigProperty(name = \"security.token.signing-key\")\n"
            + "+    String signingKey;\n"),

        Map.entry("test/auth/TokenServiceTest.java",
            "@@ -10,4 +10,12 @@ class TokenServiceTest {\n"
            + "+    @Test\n"
            + "+    void issueToken_containsSubjectAndScopes() {\n"
            + "+        var token = service.issueToken(testUser, Set.of(\"read\", \"write\"));\n"
            + "+        var claims = parseToken(token);\n"
            + "+        assertEquals(testUser.id(), claims.getSubject());\n"
            + "+    }\n"),

        Map.entry("test/auth/SessionManagerTest.java",
            "@@ -8,4 +8,10 @@ class SessionManagerTest {\n"
            + "+    @Test\n"
            + "+    void createSession_capturesClientInfo() {\n"
            + "+        var session = manager.createSession(\"user1\", mockRequest);\n"
            + "+        assertNotNull(session.getIpAddress());\n"
            + "+    }\n"),

        Map.entry("docs/security.md",
            "@@ -10,4 +10,8 @@\n"
            + "+## Token Management\n"
            + "+\n"
            + "+Tokens are signed with HS256. Token rotation on privilege escalation\n"
            + "+is handled by TokenService.issueToken().\n"),

        // ── Rename / Style (PR #102 scenario) ───────────────────────

        Map.entry("src/service/UserService.java",
            "@@ -15,8 +15,8 @@ public class UserService {\n"
            + "-    private String userName;\n"
            + "-    private String userEmail;\n"
            + "+    private String name;\n"
            + "+    private String email;\n"),

        Map.entry("src/service/OrderService.java",
            "@@ -22,6 +22,6 @@ public class OrderService {\n"
            + "-    private String orderStatus;\n"
            + "+    private String status;\n"),

        Map.entry("src/util/Naming.java",
            "@@ -8,4 +8,4 @@ public class Naming {\n"
            + "-    public static String toDisplayName(String userName) {\n"
            + "+    public static String toDisplayName(String name) {\n"),

        // ── Payment Module Extraction (PR #103 scenario) ────────────

        Map.entry("src/payment/PaymentService.java",
            "@@ -1,5 +1,20 @@\n"
            + "+package com.example.payment;\n"
            + "+\n"
            + "+public class PaymentService {\n"
            + "+    private final PaymentProvider provider;\n"
            + "+    private final TransactionRepository transactionRepo;\n"
            + "+\n"
            + "+    public Transaction charge(String customerId, long amountCents, String currency) {\n"
            + "+        var result = provider.authorize(customerId, amountCents, currency);\n"
            + "+        if (result.approved()) {\n"
            + "+            var captured = provider.capture(result.authorizationId());\n"
            + "+            var tx = new Transaction(customerId, amountCents, currency, captured.transactionId());\n"
            + "+            transactionRepo.save(tx);\n"
            + "+            return tx;\n"
            + "+        }\n"
            + "+        throw new PaymentDeclinedException(result.declineReason());\n"
            + "+    }\n"
            + "+}\n"),

        Map.entry("src/payment/PaymentGateway.java",
            "@@ -0,0 +1,7 @@\n"
            + "+package com.example.payment;\n"
            + "+\n"
            + "+public interface PaymentGateway {\n"
            + "+    AuthResult authorize(String customerId, long amountCents, String currency);\n"
            + "+    CaptureResult capture(String authorizationId);\n"
            + "+    RefundResult refund(String transactionId, long amountCents);\n"
            + "+}\n"),

        Map.entry("src/payment/StripeAdapter.java",
            "@@ -0,0 +1,14 @@\n"
            + "+package com.example.payment;\n"
            + "+\n"
            + "+import com.stripe.model.Charge;\n"
            + "+\n"
            + "+public class StripeAdapter implements PaymentGateway {\n"
            + "+    @Override\n"
            + "+    public AuthResult authorize(String customerId, long amountCents, String currency) {\n"
            + "+        Stripe.apiKey = System.getenv(\"STRIPE_API_KEY\");\n"
            + "+        var params = Map.of(\"amount\", amountCents, \"currency\", currency);\n"
            + "+        var charge = Charge.create(params);\n"
            + "+        return new AuthResult(charge.getPaid(), charge.getId(), charge.getFailureMessage());\n"
            + "+    }\n"
            + "+}\n"),

        Map.entry("src/payment/PaymentController.java",
            "@@ -0,0 +1,14 @@\n"
            + "+package com.example.payment;\n"
            + "+\n"
            + "+import jakarta.ws.rs.*;\n"
            + "+\n"
            + "+@Path(\"/api/payments\")\n"
            + "+public class PaymentController {\n"
            + "+    private final PaymentService paymentService;\n"
            + "+\n"
            + "+    @POST @Path(\"/charge\")\n"
            + "+    public Transaction charge(ChargeRequest request) {\n"
            + "+        return paymentService.charge(request.customerId(), request.amountCents(), request.currency());\n"
            + "+    }\n"
            + "+}\n"),

        Map.entry("src/order/OrderService.java",
            "@@ -30,5 +30,8 @@ public class OrderService {\n"
            + "-    public void processOrder(Order order) {\n"
            + "-        paymentService.charge(order.customerId(), order.total(), order.currency());\n"
            + "+    public void processOrder(Order order) {\n"
            + "+        eventBus.publish(new PaymentRequested(\n"
            + "+                order.id(), order.customerId(),\n"
            + "+                order.total(), order.currency()));\n"
            + "     }\n"),

        Map.entry("src/order/CheckoutFlow.java",
            "@@ -15,6 +15,10 @@ public class CheckoutFlow {\n"
            + "+    public void onPaymentCompleted(PaymentCompleted event) {\n"
            + "+        var order = orderRepo.findById(event.orderId());\n"
            + "+        order.setStatus(\"PAID\");\n"
            + "+        orderRepo.save(order);\n"
            + "+    }\n"),

        Map.entry("src/payment/model/Transaction.java",
            "@@ -0,0 +1,6 @@\n"
            + "+package com.example.payment.model;\n"
            + "+\n"
            + "+public record Transaction(\n"
            + "+    String customerId, long amountCents,\n"
            + "+    String currency, String providerTransactionId\n"
            + "+) {}\n"),

        Map.entry("src/payment/spi/PaymentProvider.java",
            "@@ -0,0 +1,6 @@\n"
            + "+package com.example.payment.spi;\n"
            + "+\n"
            + "+public interface PaymentProvider {\n"
            + "+    AuthResult authorize(String customerId, long amountCents, String currency);\n"
            + "+    CaptureResult capture(String authorizationId);\n"
            + "+}\n"),

        Map.entry("src/payment/spi/RefundPolicy.java",
            "@@ -0,0 +1,5 @@\n"
            + "+package com.example.payment.spi;\n"
            + "+\n"
            + "+public interface RefundPolicy {\n"
            + "+    boolean isRefundable(Transaction tx, Duration sinceCharge);\n"
            + "+}\n"),

        Map.entry("src/config/PaymentConfig.java",
            "@@ -0,0 +1,8 @@\n"
            + "+package com.example.config;\n"
            + "+\n"
            + "+public class PaymentConfig {\n"
            + "+    @ConfigProperty(name = \"payment.provider\")\n"
            + "+    String provider;\n"
            + "+    @ConfigProperty(name = \"payment.retry-attempts\", defaultValue = \"3\")\n"
            + "+    int retryAttempts;\n"
            + "+}\n"),

        Map.entry("src/config/ModuleConfig.java",
            "@@ -10,4 +10,6 @@ public class ModuleConfig {\n"
            + "+    @ConfigProperty(name = \"modules.payment.enabled\", defaultValue = \"true\")\n"
            + "+    boolean paymentEnabled;\n"),

        Map.entry("db/migration/V100__payment_tables.sql",
            "@@ -0,0 +1,10 @@\n"
            + "+CREATE TABLE transactions (\n"
            + "+    id UUID PRIMARY KEY,\n"
            + "+    customer_id VARCHAR(255) NOT NULL,\n"
            + "+    amount_cents BIGINT NOT NULL,\n"
            + "+    currency VARCHAR(3) NOT NULL,\n"
            + "+    provider_tx_id VARCHAR(255),\n"
            + "+    status VARCHAR(50) DEFAULT 'PENDING',\n"
            + "+    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP\n"
            + "+);\n"
            + "+CREATE INDEX idx_tx_customer ON transactions(customer_id);\n"),

        Map.entry("db/migration/V101__payment_indexes.sql",
            "@@ -0,0 +1,2 @@\n"
            + "+CREATE INDEX idx_tx_status ON transactions(status);\n"
            + "+CREATE INDEX idx_tx_created ON transactions(created_at);\n"),

        Map.entry("docs/architecture.md",
            "@@ -50,0 +51,5 @@\n"
            + "+## Payment Module\n"
            + "+\n"
            + "+Extracted as a standalone service boundary.\n"
            + "+PaymentProvider SPI for pluggable backends.\n"
            + "+Event-driven communication with order service.\n"),

        Map.entry("docs/payment-module.md",
            "@@ -0,0 +1,6 @@\n"
            + "+# Payment Module\n"
            + "+\n"
            + "+Extracted from the monolith order service.\n"
            + "+- PaymentProvider SPI for pluggable backends\n"
            + "+- Event-driven communication with order service\n"
            + "+- Transaction audit via database persistence\n"),

        Map.entry("pom.xml",
            "@@ -45,6 +45,10 @@\n"
            + "+    <dependency>\n"
            + "+        <groupId>com.stripe</groupId>\n"
            + "+        <artifactId>stripe-java</artifactId>\n"
            + "+        <version>24.0.0</version>\n"
            + "+    </dependency>\n"),

        Map.entry("payment-module/pom.xml",
            "@@ -0,0 +1,8 @@\n"
            + "+<project>\n"
            + "+    <modelVersion>4.0.0</modelVersion>\n"
            + "+    <parent>\n"
            + "+        <groupId>com.example</groupId>\n"
            + "+        <artifactId>webapp</artifactId>\n"
            + "+    </parent>\n"
            + "+    <artifactId>payment-module</artifactId>\n"
            + "+</project>\n"),

        Map.entry("payment-module/src/main/java/PaymentApp.java",
            "@@ -0,0 +1,4 @@\n"
            + "+package com.example.payment;\n"
            + "+\n"
            + "+public class PaymentApp {}\n"),

        Map.entry("test/payment/PaymentServiceTest.java",
            "@@ -0,0 +1,10 @@\n"
            + "+class PaymentServiceTest {\n"
            + "+    @Test\n"
            + "+    void charge_successfulPayment_persistsTransaction() {\n"
            + "+        when(provider.authorize(any(), anyLong(), any()))\n"
            + "+            .thenReturn(new AuthResult(true, \"auth-1\", null));\n"
            + "+        var tx = service.charge(\"cust-1\", 5000, \"USD\");\n"
            + "+        assertNotNull(tx.providerTransactionId());\n"
            + "+        verify(transactionRepo).save(any());\n"
            + "+    }\n"
            + "+}\n"),

        Map.entry("test/payment/GatewayTest.java",
            "@@ -0,0 +1,8 @@\n"
            + "+class GatewayTest {\n"
            + "+    @Test\n"
            + "+    void refund_withinWindow_succeeds() {\n"
            + "+        var result = gateway.refund(\"tx-1\", 2500);\n"
            + "+        assertTrue(result.success());\n"
            + "+    }\n"
            + "+}\n"),

        Map.entry("test/order/OrderServiceTest.java",
            "@@ -20,6 +20,10 @@ class OrderServiceTest {\n"
            + "+    @Test\n"
            + "+    void processOrder_publishesPaymentEvent() {\n"
            + "+        service.processOrder(testOrder);\n"
            + "+        verify(eventBus).publish(any(PaymentRequested.class));\n"
            + "+    }\n")
    );
}
