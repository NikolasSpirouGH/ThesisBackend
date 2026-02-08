package com.cloud_ml_app_thesis.repository.group;

import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.entity.group.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GroupRepository extends JpaRepository<Group, Integer> {

    List<Group> findByLeader(User leader);

    List<Group> findByLeaderId(UUID leaderId);

    Optional<Group> findByName(String name);

    boolean existsByName(String name);

    @Query("SELECT g FROM Group g JOIN g.members m WHERE m.id = :userId")
    List<Group> findGroupsByMemberId(@Param("userId") UUID userId);

    @Query("SELECT g FROM Group g WHERE g.leader.id = :userId OR :userId IN (SELECT m.id FROM g.members m)")
    List<Group> findGroupsByUserIdAsLeaderOrMember(@Param("userId") UUID userId);

    @Query("SELECT CASE WHEN COUNT(g) > 0 THEN true ELSE false END FROM Group g WHERE g.id = :groupId AND (g.leader.id = :userId OR :userId IN (SELECT m.id FROM g.members m))")
    boolean isUserMemberOfGroup(@Param("groupId") Integer groupId, @Param("userId") UUID userId);

    @Query("SELECT CASE WHEN COUNT(g) > 0 THEN true ELSE false END FROM Group g WHERE g.id = :groupId AND g.leader.id = :userId")
    boolean isUserLeaderOfGroup(@Param("groupId") Integer groupId, @Param("userId") UUID userId);
}
