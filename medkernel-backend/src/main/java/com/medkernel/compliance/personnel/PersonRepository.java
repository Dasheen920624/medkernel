package com.medkernel.compliance.personnel;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/** 人员主数据仓库。 */
@Repository
public interface PersonRepository extends ListCrudRepository<Person, String> {

    Optional<Person> findByTenantIdAndPersonId(String tenantId, String personId);

    Optional<Person> findByTenantIdAndEmployeeNo(String tenantId, String employeeNo);

    @Query("""
        SELECT COUNT(*) FROM mk_identity_person
        WHERE tenant_id = :tenantId
          AND (:keyword IS NULL
            OR LOWER(display_name) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(employee_no) LIKE LOWER(CONCAT('%', :keyword, '%')))
        """)
    long countDirectory(String tenantId, String keyword);

    @Query("""
        SELECT * FROM mk_identity_person
        WHERE tenant_id = :tenantId
          AND (:keyword IS NULL
            OR LOWER(display_name) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(employee_no) LIKE LOWER(CONCAT('%', :keyword, '%')))
        ORDER BY display_name, employee_no
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<Person> pageDirectory(String tenantId, String keyword, int offset, int limit);
}
