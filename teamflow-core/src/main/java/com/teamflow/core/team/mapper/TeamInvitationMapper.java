package com.teamflow.core.team.mapper;

import com.teamflow.core.team.domain.TeamInvitation;
import com.teamflow.core.team.domain.TeamInvitationStatus;
import com.teamflow.core.team.dto.TeamInvitationDetails;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/** 团队邀请持久化接口。 */
@Mapper
public interface TeamInvitationMapper {

    int insert(TeamInvitation invitation);

    Optional<TeamInvitation> findById(@Param("id") String id);

    Optional<TeamInvitation> findByIdForUpdate(@Param("id") String id);

    Optional<TeamInvitation> findPendingByTeamAndInvitee(
            @Param("teamId") String teamId,
            @Param("inviteeId") String inviteeId
    );

    List<TeamInvitationDetails> findDetailsByInvitee(
            @Param("inviteeId") String inviteeId
    );

    List<TeamInvitationDetails> findDetailsByTeam(
            @Param("teamId") String teamId
    );

    Optional<TeamInvitationDetails> findDetailsById(@Param("id") String id);

    int updateState(
            @Param("invitation") TeamInvitation invitation,
            @Param("expectedStatus") TeamInvitationStatus expectedStatus,
            @Param("expectedVersion") int expectedVersion
    );
}
