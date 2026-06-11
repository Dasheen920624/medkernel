package com.medkernel.compliance.personnel;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/** 任职关系仓库。 */
@Repository
public interface PersonAppointmentRepository
        extends ListCrudRepository<PersonAppointment, String> {

    List<PersonAppointment> findByTenantIdAndPersonIdOrderByEffectiveFromDesc(
        String tenantId,
        String personId
    );

    Optional<PersonAppointment>
        findFirstByTenantIdAndPersonIdAndStatusAndPrimaryFlagOrderByEffectiveFromDesc(
            String tenantId,
            String personId,
            AppointmentStatus status,
            String primaryFlag
        );
}
