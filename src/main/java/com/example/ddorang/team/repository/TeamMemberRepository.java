package com.example.ddorang.team.repository;

import com.example.ddorang.auth.entity.User;
import com.example.ddorang.team.entity.Team;
import com.example.ddorang.team.entity.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TeamMemberRepository extends JpaRepository<TeamMember, UUID> {

    @Query("SELECT tm FROM TeamMember tm JOIN FETCH tm.user u WHERE tm.team = :team ORDER BY tm.joinedAt ASC")
    List<TeamMember> findByTeamOrderByJoinedAtAsc(@Param("team") Team team);

    @Query("SELECT tm FROM TeamMember tm JOIN FETCH tm.user u JOIN FETCH tm.team t WHERE tm.user = :user ORDER BY tm.joinedAt DESC")
    List<TeamMember> findByUserOrderByJoinedAtDesc(@Param("user") User user);

    @Query("SELECT tm FROM TeamMember tm JOIN FETCH tm.user u JOIN FETCH tm.team t WHERE tm.team = :team AND tm.user = :user")
    Optional<TeamMember> findByTeamAndUser(@Param("team") Team team, @Param("user") User user);

    boolean existsByTeamAndUser(Team team, User user);

    @Query("SELECT tm FROM TeamMember tm JOIN FETCH tm.user u WHERE tm.team = :team AND tm.role = 'OWNER'")
    List<TeamMember> findTeamOwners(@Param("team") Team team);

    @Query("SELECT COUNT(tm) FROM TeamMember tm WHERE tm.team = :team")
    long countByTeam(Team team);
}