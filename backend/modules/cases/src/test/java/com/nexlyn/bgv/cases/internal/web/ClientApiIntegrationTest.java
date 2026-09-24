package com.nexlyn.bgv.cases.internal.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexlyn.bgv.cases.CasesIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

class ClientApiIntegrationTest extends CasesIntegrationTestBase {

    private JsonNode createClient(String name) throws Exception {
        MvcResult created = send(post("/api/clients"), superToken(),
                Map.of("name", name, "displayName", name + "\nPrivate Limited", "defaultCheckTypes", List.of("AADHAAR", "PAN")));
        assertThat(status(created)).as(created.getResponse().getContentAsString()).isEqualTo(201);
        return body(created);
    }

    @Test
    void requiresSignInAndTheRightPermission() throws Exception {
        assertThat(status(send(get("/api/clients"), null, null))).isEqualTo(401);
        assertThat(status(send(get("/api/clients"), tokenFor(ownerId, OWNER, "AUDIT_READ"), null))).isEqualTo(403);
        assertThat(status(send(post("/api/clients"), tokenFor(ownerId, OWNER, "CASE_READ_ALL"),
                Map.of("name", "Acme", "displayName", "Acme")))).as("read-only cannot create").isEqualTo(403);
        assertThat(status(send(post("/api/clients"), null, Map.of("name", "Acme", "displayName", "Acme")))).isEqualTo(401);
    }

    @Test
    void aClientManagerCreatesAndAnyCaseReaderLists() throws Exception {
        JsonNode client = createClient("Acme Corp");
        assertThat(client.get("name").asText()).isEqualTo("Acme Corp");
        assertThat(client.get("displayName").asText()).as("multi-line name kept").isEqualTo("Acme Corp\nPrivate Limited");
        assertThat(client.get("defaultCheckTypes")).extracting(JsonNode::asText).containsExactly("AADHAAR", "PAN");
        assertThat(client.get("active").asBoolean()).isTrue();

        for (String permission : List.of("CASE_READ_ALL", "CASE_READ_ASSIGNED", "CLIENT_MANAGE")) {
            MvcResult list = send(get("/api/clients"), tokenFor(ownerId, OWNER, permission), null);
            assertThat(status(list)).as(permission).isEqualTo(200);
            assertThat(body(list)).as(permission).hasSize(1);
        }
        assertThat(status(send(get("/api/clients/" + client.get("id").asText()), tokenFor(ownerId, OWNER, "CASE_READ_ASSIGNED"), null))).isEqualTo(200);
        assertThat(auditActionsSinceStart()).contains("CLIENT_CREATED");
    }

    @Test
    void namesAreUniqueIgnoringCaseAndInputIsValidated() throws Exception {
        createClient("Acme Corp");
        assertThat(status(send(post("/api/clients"), superToken(), Map.of("name", " ACME corp ", "displayName", "x")))).isEqualTo(409);
        assertThat(status(send(post("/api/clients"), superToken(), Map.of("name", "", "displayName", "x")))).isEqualTo(400);
        assertThat(status(send(post("/api/clients"), superToken(), Map.of("name", "New", "displayName", "")))).isEqualTo(400);
        assertThat(status(send(post("/api/clients"), superToken(), Map.of("name", "x".repeat(201), "displayName", "x")))).isEqualTo(400);
        assertThat(status(send(get("/api/clients/" + UUID.randomUUID()), superToken(), null))).isEqualTo(404);
        assertThat(status(send(get("/api/clients/not-a-uuid"), superToken(), null))).isEqualTo(400);
    }

    @Test
    void updatingNeedsTheCurrentVersionSoNobodyOverwritesAnothersChange() throws Exception {
        JsonNode client = createClient("Acme Corp");
        String id = client.get("id").asText();
        long version = client.get("version").asLong();

        MvcResult updated = send(put("/api/clients/" + id), superToken(), Map.of(
                "version", version, "name", "Acme Corporation", "displayName", "Acme\nCorporation", "active", false));
        assertThat(status(updated)).isEqualTo(200);
        assertThat(body(updated).get("name").asText()).isEqualTo("Acme Corporation");
        assertThat(body(updated).get("active").asBoolean()).isFalse();
        assertThat(body(updated).get("version").asLong()).isGreaterThan(version);

        MvcResult stale = send(put("/api/clients/" + id), superToken(), Map.of("version", version, "name", "Old edit", "displayName", "x"));
        assertThat(status(stale)).isEqualTo(409);
        assertThat(code(stale)).isEqualTo("CONFLICT");
        assertThat(status(send(put("/api/clients/" + id), superToken(), Map.of("name", "No version", "displayName", "x")))).isEqualTo(400);
        assertThat(auditActionsSinceStart()).contains("CLIENT_UPDATED");
    }

    @Test
    void renamingToAnotherClientsNameIsRefusedButKeepingYourOwnNameIsFine() throws Exception {
        createClient("Alpha");
        JsonNode beta = createClient("Beta");
        long version = beta.get("version").asLong();

        assertThat(status(send(put("/api/clients/" + beta.get("id").asText()), superToken(),
                Map.of("version", version, "name", "alpha", "displayName", "x")))).isEqualTo(409);
        assertThat(status(send(put("/api/clients/" + beta.get("id").asText()), superToken(),
                Map.of("version", version, "name", "BETA", "displayName", "Beta renamed")))).isEqualTo(200);
    }

    @Test
    void theListCanBeFilteredByActiveAndIsSortedByName() throws Exception {
        createClient("Zeta");
        JsonNode alpha = createClient("Alpha");
        send(put("/api/clients/" + alpha.get("id").asText()), superToken(),
                Map.of("version", alpha.get("version").asLong(), "name", "Alpha", "displayName", "Alpha", "active", false));

        JsonNode everyone = body(send(get("/api/clients"), superToken(), null));
        assertThat(everyone).extracting(n -> n.get("name").asText()).containsExactly("Alpha", "Zeta");
        JsonNode active = body(send(get("/api/clients?active=true"), superToken(), null));
        assertThat(active).extracting(n -> n.get("name").asText()).containsExactly("Zeta");
        JsonNode inactive = body(send(get("/api/clients?active=false"), superToken(), null));
        assertThat(inactive).extracting(n -> n.get("name").asText()).containsExactly("Alpha");
    }
}
