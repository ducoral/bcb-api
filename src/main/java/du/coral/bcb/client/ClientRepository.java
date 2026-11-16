package du.coral.bcb.client;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, String> {

    boolean existsByDocumentId(String documentId);

    boolean existsByDocumentIdAndIdNot(String documentId, String id);

    /** SELECT ... FOR UPDATE. Desiste após 3 s em vez de esperar indefinidamente. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    Optional<Client> findWithLockById(String id);

    /** Restaura o limite mensal dos pós-pagos que ainda não foram renovados neste período. */
    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE client
               SET credit_limit = monthly_limit, limit_period = :period
             WHERE plan_type = 'POSTPAID'
               AND (limit_period IS NULL OR limit_period < :period)
            """, nativeQuery = true)
    int resetMonthlyLimits(@Param("period") LocalDate period);
}