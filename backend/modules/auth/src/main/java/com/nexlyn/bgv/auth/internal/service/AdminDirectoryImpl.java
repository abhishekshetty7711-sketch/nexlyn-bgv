package com.nexlyn.bgv.auth.internal.service;

import com.nexlyn.bgv.auth.AdminDirectory;
import com.nexlyn.bgv.auth.internal.domain.Admin;
import com.nexlyn.bgv.auth.internal.domain.AdminStatus;
import com.nexlyn.bgv.auth.internal.repository.AdminRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
class AdminDirectoryImpl implements AdminDirectory {

    private final AdminRepository admins;

    AdminDirectoryImpl(AdminRepository admins) {
        this.admins = admins;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, AdminSummary> find(Collection<UUID> ids) {
        Map<UUID, AdminSummary> found = new HashMap<>();
        if (ids.isEmpty()) {
            return found;
        }
        admins.findAllById(ids).forEach(admin -> found.put(admin.getId(), summary(admin)));
        return found;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminSummary> listActive() {
        return admins.findAll().stream()
                .filter(admin -> admin.getStatus() == AdminStatus.ACTIVE)
                .map(AdminDirectoryImpl::summary)
                .sorted(Comparator.comparing(AdminSummary::fullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static AdminSummary summary(Admin admin) {
        return new AdminSummary(admin.getId(), admin.getEmail(), admin.getFullName(),
                admin.getStatus() == AdminStatus.ACTIVE);
    }
}
