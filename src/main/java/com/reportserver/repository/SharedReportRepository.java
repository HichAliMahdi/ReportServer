package com.reportserver.repository;

import com.reportserver.model.SharedReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SharedReportRepository extends JpaRepository<SharedReport, Long> {
    Optional<SharedReport> findByReportFileName(String reportFileName);
    List<SharedReport> findBySharedWithReadOnlyTrue();
    Page<SharedReport> findBySharedWithReadOnlyTrue(Pageable pageable);
    Page<SharedReport> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<SharedReport> findBySharedWithReadOnlyTrueOrderByCreatedAtDesc(Pageable pageable);
    Page<SharedReport> findByCategoryContainingIgnoreCaseOrderByCreatedAtDesc(String category, Pageable pageable);
    Page<SharedReport> findByTagsContainingIgnoreCaseOrderByCreatedAtDesc(String tags, Pageable pageable);
    Page<SharedReport> findByCategoryContainingIgnoreCaseAndTagsContainingIgnoreCaseOrderByCreatedAtDesc(String category, String tags, Pageable pageable);
    List<SharedReport> findAll();

     @Query(
          value = """
                select r from SharedReport r
                where r.sharedWithReadOnly = true
                    or exists (
                         select 1 from ReportShareRecipient rs
                         where rs.report.id = r.id and rs.user.id = :userId
                    )
                order by r.createdAt desc
                """,
          countQuery = """
                select count(r) from SharedReport r
                where r.sharedWithReadOnly = true
                    or exists (
                         select 1 from ReportShareRecipient rs
                         where rs.report.id = r.id and rs.user.id = :userId
                    )
                """
     )
     Page<SharedReport> findAccessibleForReadOnly(@Param("userId") Long userId, Pageable pageable);

     @Query(
          value = """
                select r from SharedReport r
                where lower(coalesce(r.category, '')) like lower(concat('%', :category, '%'))
                  and (
                          r.sharedWithReadOnly = true
                      or exists (
                          select 1 from ReportShareRecipient rs
                          where rs.report.id = r.id and rs.user.id = :userId
                      )
                  )
                order by r.createdAt desc
                """,
          countQuery = """
                select count(r) from SharedReport r
                where lower(coalesce(r.category, '')) like lower(concat('%', :category, '%'))
                  and (
                          r.sharedWithReadOnly = true
                      or exists (
                          select 1 from ReportShareRecipient rs
                          where rs.report.id = r.id and rs.user.id = :userId
                      )
                  )
                """
     )
     Page<SharedReport> findAccessibleForReadOnlyByCategory(@Param("userId") Long userId, @Param("category") String category, Pageable pageable);

     @Query(
          value = """
                select r from SharedReport r
                where lower(coalesce(r.tags, '')) like lower(concat('%', :tag, '%'))
                  and (
                          r.sharedWithReadOnly = true
                      or exists (
                          select 1 from ReportShareRecipient rs
                          where rs.report.id = r.id and rs.user.id = :userId
                      )
                  )
                order by r.createdAt desc
                """,
          countQuery = """
                select count(r) from SharedReport r
                where lower(coalesce(r.tags, '')) like lower(concat('%', :tag, '%'))
                  and (
                          r.sharedWithReadOnly = true
                      or exists (
                          select 1 from ReportShareRecipient rs
                          where rs.report.id = r.id and rs.user.id = :userId
                      )
                  )
                """
     )
     Page<SharedReport> findAccessibleForReadOnlyByTag(@Param("userId") Long userId, @Param("tag") String tag, Pageable pageable);

     @Query(
          value = """
                select r from SharedReport r
                where lower(coalesce(r.category, '')) like lower(concat('%', :category, '%'))
                  and lower(coalesce(r.tags, '')) like lower(concat('%', :tag, '%'))
                  and (
                          r.sharedWithReadOnly = true
                      or exists (
                          select 1 from ReportShareRecipient rs
                          where rs.report.id = r.id and rs.user.id = :userId
                      )
                  )
                order by r.createdAt desc
                """,
          countQuery = """
                select count(r) from SharedReport r
                where lower(coalesce(r.category, '')) like lower(concat('%', :category, '%'))
                  and lower(coalesce(r.tags, '')) like lower(concat('%', :tag, '%'))
                  and (
                          r.sharedWithReadOnly = true
                      or exists (
                          select 1 from ReportShareRecipient rs
                          where rs.report.id = r.id and rs.user.id = :userId
                      )
                  )
                """
     )
     Page<SharedReport> findAccessibleForReadOnlyByCategoryAndTag(
          @Param("userId") Long userId,
          @Param("category") String category,
          @Param("tag") String tag,
          Pageable pageable
     );
}
