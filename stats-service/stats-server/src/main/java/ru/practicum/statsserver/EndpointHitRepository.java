package ru.practicum.statsserver;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface EndpointHitRepository extends JpaRepository<EndpointHit, Long> {

    @Query(value = """
SELECT h.app AS app,
       h.uri AS uri,
       COUNT(*) AS hits
FROM endpoint_hits h
WHERE h.hit_time BETWEEN :start AND :end
GROUP BY h.app, h.uri
ORDER BY hits DESC
""", nativeQuery = true)
    List<Object[]> findStatsAll(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query(value = """
SELECT h.app AS app,
       h.uri AS uri,
       CASE
           WHEN :unique THEN COUNT(DISTINCT h.ip)
           ELSE COUNT(*)
       END AS hits
FROM endpoint_hits h
WHERE h.hit_time BETWEEN :start AND :end
  AND h.uri IN :uris
GROUP BY h.app, h.uri
ORDER BY hits DESC
""", nativeQuery = true)
    List<Object[]> findStatsByUris(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("uris") List<String> uris,
            @Param("unique") boolean unique
    );
}
