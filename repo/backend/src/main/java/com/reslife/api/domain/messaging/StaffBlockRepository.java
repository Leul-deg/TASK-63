package com.reslife.api.domain.messaging;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface StaffBlockRepository extends JpaRepository<StaffBlock, StaffBlockId> {

    boolean existsByIdStudentUserIdAndIdBlockedStaffUserId(UUID studentUserId, UUID blockedStaffUserId);

    /** All blocks created by the given student. */
    List<StaffBlock> findByIdStudentUserId(UUID studentUserId);

    /**
     * True if ANY recipient in the list has blocked the given staff user.
     * Used before creating a DIRECT thread.
     */
    @Query("SELECT COUNT(b) > 0 FROM StaffBlock b " +
           "WHERE b.id.studentUserId IN :recipientIds " +
           "  AND b.id.blockedStaffUserId = :staffUserId")
    boolean anyRecipientBlocksStaff(@Param("recipientIds") List<UUID> recipientIds,
                                    @Param("staffUserId") UUID staffUserId);
}
