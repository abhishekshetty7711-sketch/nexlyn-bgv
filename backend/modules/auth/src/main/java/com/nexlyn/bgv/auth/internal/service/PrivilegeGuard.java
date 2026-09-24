package com.nexlyn.bgv.auth.internal.service;

import com.nexlyn.bgv.auth.AdminPrincipal;
import com.nexlyn.bgv.auth.internal.domain.AdminStatus;
import com.nexlyn.bgv.auth.internal.domain.Role;
import com.nexlyn.bgv.auth.internal.repository.AdminRepository;
import com.nexlyn.bgv.common.error.ApiException;
import com.nexlyn.bgv.common.error.ErrorCode;
import com.nexlyn.bgv.common.security.Permission;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/**
 * Rules that stop administration from being used to gain or destroy power:
 * <ul>
 *   <li><b>No escalation:</b> nobody can grant a role or permission they do not hold themselves, or
 *       manage an admin who holds more than they do.</li>
 *   <li><b>No lock-out:</b> the system must always keep at least one active admin who can manage
 *       admins and one who can manage roles.</li>
 * </ul>
 */
@Component
class PrivilegeGuard {

    private final AdminRepository admins;

    PrivilegeGuard(AdminRepository admins) {
        this.admins = admins;
    }

    /** The caller must hold every permission that the given roles grant. */
    void assertCanGrant(AdminPrincipal caller, Collection<Role> roles) {
        Set<String> wanted = new TreeSet<>();
        roles.forEach(role -> wanted.addAll(role.getPermissions()));
        assertHoldsAll(caller, wanted);
    }

    void assertHoldsAll(AdminPrincipal caller, Collection<String> permissions) {
        if (!caller.permissions().containsAll(permissions)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "You cannot grant more access than you have yourself.");
        }
    }

    /** The caller must hold every permission the target admin holds. */
    void assertCanManage(AdminPrincipal caller, Collection<Role> targetRoles) {
        Set<String> theirs = new TreeSet<>();
        targetRoles.forEach(role -> theirs.addAll(role.getPermissions()));
        if (!caller.permissions().containsAll(theirs)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "You cannot manage an admin who has more access than you.");
        }
    }

    /**
     * Call after applying a change (inside the transaction, after a flush): if nobody would be left
     * who can manage admins or roles, the exception rolls the change back.
     */
    void assertAdministrable() {
        for (Permission required : new Permission[]{Permission.USER_MANAGE, Permission.ROLE_MANAGE}) {
            if (admins.countWithPermission(AdminStatus.ACTIVE, required.name()) < 1) {
                throw new ApiException(ErrorCode.CONFLICT,
                        "This change would leave no active admin able to use " + required.name() + ".");
            }
        }
    }
}
