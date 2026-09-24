package com.nexlyn.bgv.cases.internal.web;

import com.nexlyn.bgv.cases.internal.service.ClientService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** {@code /api/clients}: the client master. Permissions are checked in {@link ClientService}. */
@RestController
@RequestMapping("/api/clients")
public class ClientController {

    record ClientRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 1000) String displayName,
            @Size(max = 30) List<@NotBlank @Size(max = 50) String> defaultCheckTypes,
            Boolean active) {
    }

    record UpdateClientRequest(
            @NotNull Long version,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 1000) String displayName,
            @Size(max = 30) List<@NotBlank @Size(max = 50) String> defaultCheckTypes,
            Boolean active) {
    }

    private final ClientService clients;

    public ClientController(ClientService clients) {
        this.clients = clients;
    }

    @GetMapping
    public List<ClientService.ClientView> list(@RequestParam(required = false) Boolean active) {
        return clients.list(active);
    }

    @GetMapping("/{id}")
    public ClientService.ClientView get(@PathVariable UUID id) {
        return clients.get(id);
    }

    @PostMapping
    public ResponseEntity<ClientService.ClientView> create(@Valid @RequestBody ClientRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clients.create(
                new ClientService.ClientInput(body.name(), body.displayName(), body.defaultCheckTypes(), body.active())));
    }

    @PutMapping("/{id}")
    public ClientService.ClientView update(@PathVariable UUID id, @Valid @RequestBody UpdateClientRequest body) {
        return clients.update(id, body.version(), new ClientService.ClientInput(
                body.name(), body.displayName(), body.defaultCheckTypes(), body.active()));
    }
}
