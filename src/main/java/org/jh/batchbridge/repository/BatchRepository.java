package org.jh.batchbridge.repository;

import java.util.List;
import org.jh.batchbridge.domain.Batch;
import org.jh.batchbridge.domain.BatchStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BatchRepository extends JpaRepository<Batch, Long> {

    List<Batch> findAllByStatus(BatchStatus status);

    Page<Batch> findAllByStatus(BatchStatus status, Pageable pageable);

    @Query(
            value = """
                    select
                        b.id as id,
                        b.label as label,
                        b.model as model,
                        b.status as status,
                        count(p.id) as promptCount,
                        b.createdAt as createdAt,
                        b.submittedAt as submittedAt,
                        b.completedAt as completedAt
                    from Batch b
                    left join b.prompts p
                    where (:status is null or b.status = :status)
                    and b.deletedAt is null
                    group by
                        b.id,
                        b.label,
                        b.model,
                        b.status,
                        b.createdAt,
                        b.submittedAt,
                        b.completedAt
                    """,
            countQuery = """
                    select count(b)
                    from Batch b
                    where (:status is null or b.status = :status)
                    and b.deletedAt is null
                    """
    )
    Page<BatchSummaryView> findBatchSummaries(@Param("status") BatchStatus status, Pageable pageable);
}
