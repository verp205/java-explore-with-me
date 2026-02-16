package ru.practicum.statsserver;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.ViewStatsDto;

import java.time.LocalDateTime;
import java.util.List;

public interface EndpointHitRepository extends JpaRepository<EndpointHit, Long> {

    @Query(value = """
        SELECT h.app AS app,
               h.uri AS uri,
               COUNT(DISTINCT CASE WHEN :unique THEN h.ip ELSE h.ip END) AS hits
        FROM endpoint_hits h
        WHERE h.hit_time BETWEEN :start AND :end
          AND (:uris IS NULL OR h.uri IN :uris)
        GROUP BY h.app, h.uri
        ORDER BY hits DESC
        """, nativeQuery = true)
    List<Object[]> findStats(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("uris") List<String> uris,
            @Param("unique") boolean unique
    );
}
