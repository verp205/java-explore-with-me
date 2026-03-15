package ru.practicum.request.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.request.model.Request;
import ru.practicum.request.model.RequestState;

import java.util.List;
import java.util.Optional;

public interface RequestRepository extends JpaRepository<Request, Long> {
    Long countByEventIdAndStatus(Long eventId, RequestState status);

    List<Request> findByRequesterId(Long requesterId);

    List<Request> findByEventId(Long eventId);

    List<Request> findByIdIn(List<Long> ids);

    Optional<Request> findByRequesterIdAndEventId(Long requesterId, Long eventId);

    @Query("""
select r.event.id, count(r)
from Request r
where r.status = :status
and r.event.id in :eventIds
group by r.event.id
""")
    List<Object[]> countConfirmedByEventIds(
            @Param("eventIds") List<Long> eventIds,
            @Param("status") RequestState status
    );

    @Query("SELECT r.event.id, COUNT(r) FROM Request r " +
            "WHERE r.event.id IN :eventIds AND r.status = :status " +
            "GROUP BY r.event.id")
    List<Object[]> countConfirmedRequestsByEventIds(@Param("eventIds") List<Long> eventIds,
                                                    @Param("status") RequestState status);

    default List<Object[]> countConfirmedRequestsByEventIds(List<Long> eventIds) {
        return countConfirmedRequestsByEventIds(eventIds, RequestState.CONFIRMED);
    }
}