package com.photoshare.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EventMemberRepository extends JpaRepository<EventMember, EventMemberId> {
    boolean existsByIdEventIdAndIdUserId(Long eventId, Long userId);
    List<EventMember> findAllByIdEventIdOrderByAddedAtAsc(Long eventId);

    @Query("""
            select e from EventEntity e
            where e.id in (select m.id.eventId from EventMember m where m.id.userId = :userId)
            order by e.createdAt asc, e.id asc
            """)
    List<EventEntity> findAssignedEvents(Long userId);
}

