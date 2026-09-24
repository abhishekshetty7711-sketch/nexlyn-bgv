package com.nexlyn.bgv.cases.internal.service;

import com.nexlyn.bgv.auth.AuditEvent;
import com.nexlyn.bgv.auth.AuthApi;
import com.nexlyn.bgv.cases.internal.domain.Client;
import com.nexlyn.bgv.cases.internal.repository.ClientRepository;
import com.nexlyn.bgv.common.error.ApiException;
import com.nexlyn.bgv.common.error.ErrorCode;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The client master (CLAUDE.md {@literal §9.2}). Anyone who can read cases can read clients (they
 * pick one for a case); changing them needs {@code CLIENT_MANAGE}. Clients are never deleted, only
 * deactivated, because old cases still refer to them.
 */
@Service
public class ClientService {

    public record ClientView(UUID id, String name, String displayName, List<String> defaultCheckTypes,
                             boolean active, long version) {
    }

    public record ClientInput(String name, String displayName, List<String> defaultCheckTypes, Boolean active) {
    }

    private final ClientRepository clients;
    private final AuthApi auth;
    private final ApplicationEventPublisher events;

    public ClientService(ClientRepository clients, AuthApi auth, ApplicationEventPublisher events) {
        this.clients = clients;
        this.auth = auth;
        this.events = events;
    }

    @PreAuthorize("hasAnyAuthority('CASE_READ_ALL', 'CASE_READ_ASSIGNED', 'CLIENT_MANAGE')")
    @Transactional(readOnly = true)
    public List<ClientView> list(Boolean active) {
        List<Client> found = active == null ? clients.findAllByOrderByNameAsc() : clients.findAllByActiveOrderByNameAsc(active);
        return found.stream().map(ClientService::view).toList();
    }

    @PreAuthorize("hasAnyAuthority('CASE_READ_ALL', 'CASE_READ_ASSIGNED', 'CLIENT_MANAGE')")
    @Transactional(readOnly = true)
    public ClientView get(UUID id) {
        return view(find(id));
    }

    @PreAuthorize("hasAuthority('CLIENT_MANAGE')")
    @Transactional
    public ClientView create(ClientInput input) {
        String name = input.name().trim();
        if (clients.findByNameIgnoreCase(name).isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT, "A client with this name already exists.");
        }
        Client client = clients.saveAndFlush(new Client(name, input.displayName().trim(),
                cleanTypes(input.defaultCheckTypes()), input.active() == null || input.active(),
                auth.requireCurrentAdmin().id()));
        events.publishEvent(new AuditEvent("CLIENT_CREATED", null, null, "CLIENT", client.getId().toString(), null,
                null, null, null, snapshot(client)));
        return view(client);
    }

    @PreAuthorize("hasAuthority('CLIENT_MANAGE')")
    @Transactional
    public ClientView update(UUID id, long expectedVersion, ClientInput input) {
        Client client = find(id);
        if (client.getVersion() != expectedVersion) {
            throw new ApiException(ErrorCode.CONFLICT, "This client was changed by someone else. Reload and try again.");
        }
        String name = input.name().trim();
        clients.findByNameIgnoreCase(name).filter(other -> !other.getId().equals(id)).ifPresent(other -> {
            throw new ApiException(ErrorCode.CONFLICT, "A client with this name already exists.");
        });
        Map<String, Object> before = snapshot(client);
        client.update(name, input.displayName().trim(), cleanTypes(input.defaultCheckTypes()),
                input.active() == null ? client.isActive() : input.active(), auth.requireCurrentAdmin().id());
        clients.saveAndFlush(client);
        events.publishEvent(new AuditEvent("CLIENT_UPDATED", null, null, "CLIENT", client.getId().toString(), null,
                null, null, before, snapshot(client)));
        return view(client);
    }

    private Client find(UUID id) {
        return clients.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Client not found."));
    }

    private static List<String> cleanTypes(List<String> types) {
        return types == null ? List.of() : types.stream().map(String::trim).filter(t -> !t.isEmpty()).distinct().toList();
    }

    static ClientView view(Client client) {
        return new ClientView(client.getId(), client.getName(), client.getDisplayName(),
                List.copyOf(client.getDefaultCheckTypes()), client.isActive(), client.getVersion());
    }

    private static Map<String, Object> snapshot(Client client) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", client.getName());
        map.put("displayName", client.getDisplayName());
        map.put("defaultCheckTypes", client.getDefaultCheckTypes());
        map.put("active", client.isActive());
        return map;
    }
}
