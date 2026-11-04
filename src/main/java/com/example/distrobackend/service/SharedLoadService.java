package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.*;
import com.example.distrobackend.Domain.enums.*;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.*;
import com.example.distrobackend.repository.*;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class SharedLoadService {
    private final SharedLoadRepository loads;
    private final TripRepository trips;
    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final SharedLoadConfirmationRepository confirmations;

    @Transactional(readOnly = true)
    public Page<SharedLoadResponse> list(AuthenticatedUser actor, SharedLoadStatus status, Pageable page) {
        UUID org = actor.role() == UserRole.SUPER_ADMIN ? null : requireOrgStaff(actor);
        return loads.search(org, status, page).map(SharedLoadResponse::from);
    }
    @Transactional(readOnly = true)
    public SharedLoadResponse get(AuthenticatedUser actor, UUID id) { SharedLoad s = loads.findWithDetailsById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Shared load not found")); checkAccess(actor, s); return SharedLoadResponse.from(s); }

    @Transactional
    public SharedLoadResponse create(AuthenticatedUser actor, SharedLoadRequest request) {
        UUID orgId = requireOrgStaff(actor);
        Trip proposed = trips.findById(request.proposedTripId()).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Trip not found"));
        Trip candidate = trips.findById(request.candidateTripId()).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Candidate trip not found"));
        if (!orgId.equals(proposed.getOrganization().getId())) throw new ApiException(ErrorCode.NOT_FOUND, "Trip not found");
        if (proposed.getId().equals(candidate.getId()) || proposed.getOrganization().getId().equals(candidate.getOrganization().getId())) throw new ApiException(ErrorCode.BAD_REQUEST, "Shared-load trips must belong to different organizations");
        if (proposed.getTripType() != TripType.DELIVERY || candidate.getTripType() != TripType.DELIVERY || closed(proposed) || closed(candidate)) throw new ApiException(ErrorCode.CONFLICT, "Both trips must be open delivery trips");
        if (loads.existsByProposedTrip_IdAndCandidateTrip_IdAndStatusIn(proposed.getId(), candidate.getId(), java.util.List.of(SharedLoadStatus.RECOMMENDED, SharedLoadStatus.AWAITING_CONFIRMATION, SharedLoadStatus.CONFIRMED))) throw new ApiException(ErrorCode.CONFLICT, "An active recommendation already exists for these trips");
        SharedLoad s = new SharedLoad(); s.setProposedTrip(proposed); s.setCandidateTrip(candidate); s.setProposerOrganization(proposed.getOrganization()); s.setCandidateOrganization(candidate.getOrganization()); s.setProposedBy(users.getReferenceById(actor.userId())); s.setProposalNote(request.note());
        return SharedLoadResponse.from(loads.save(s));
    }
    @Transactional
    public SharedLoadResponse review(AuthenticatedUser actor, UUID id, SharedLoadReviewRequest request) {
        if (actor == null || actor.role() != UserRole.SUPER_ADMIN) throw new ApiException(ErrorCode.ACCESS_DENIED);
        SharedLoad s = loads.findByIdForUpdate(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Shared load not found"));
        if (s.getStatus() != SharedLoadStatus.RECOMMENDED) throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION, "Only recommendations awaiting review can be reviewed");
        s.setReviewedBy(users.getReferenceById(actor.userId())); s.setReviewNote(request.note());
        s.setStatus(request.compatible() ? SharedLoadStatus.AWAITING_CONFIRMATION : SharedLoadStatus.REJECTED);
        return SharedLoadResponse.from(loads.save(s));
    }
    @Transactional
    public SharedLoadResponse confirm(AuthenticatedUser actor, UUID id, SharedLoadConfirmationRequest request) {
        UUID orgId = requireOrgStaff(actor);
        SharedLoad s = loads.findByIdForUpdate(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Shared load not found"));
        if (s.getStatus() != SharedLoadStatus.AWAITING_CONFIRMATION) throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION, "Shared load is not awaiting participant confirmation");
        Organization org = orgId.equals(s.getProposerOrganization().getId()) ? s.getProposerOrganization() : orgId.equals(s.getCandidateOrganization().getId()) ? s.getCandidateOrganization() : null;
        if (org == null) throw new ApiException(ErrorCode.NOT_FOUND, "Shared load not found");
        if (confirmations.findBySharedLoad_IdAndOrganization_Id(id, orgId).isPresent()) throw new ApiException(ErrorCode.CONFLICT, "This organization has already responded");
        SharedLoadConfirmation c = new SharedLoadConfirmation(); c.setSharedLoad(s); c.setOrganization(org); c.setConfirmedBy(users.getReferenceById(actor.userId())); c.setDecision(request.decision()); c.setNote(request.note()); confirmations.save(c);
        if (request.decision() == SharedLoadDecision.DECLINED) s.setStatus(SharedLoadStatus.REJECTED);
        else if (confirmations.findBySharedLoad_IdAndOrganization_Id(id, otherOrg(s, orgId)).filter(x -> x.getDecision() == SharedLoadDecision.CONFIRMED).isPresent()) s.setStatus(SharedLoadStatus.CONFIRMED);
        return SharedLoadResponse.from(loads.save(s));
    }
    private UUID otherOrg(SharedLoad s, UUID orgId) { return orgId.equals(s.getProposerOrganization().getId()) ? s.getCandidateOrganization().getId() : s.getProposerOrganization().getId(); }
    private boolean closed(Trip t) { return t.getStatus() == TripStatus.COMPLETED || t.getStatus() == TripStatus.CANCELLED; }
    private UUID requireOrgStaff(AuthenticatedUser a) { if (a == null || a.organizationId() == null || (a.role() != UserRole.MANUFACTURER_ADMIN && a.role() != UserRole.MANUFACTURER_STAFF && a.role() != UserRole.DISTRIBUTOR_ADMIN && a.role() != UserRole.DISTRIBUTOR_STAFF)) throw new ApiException(ErrorCode.ACCESS_DENIED); return a.organizationId(); }
    private void checkAccess(AuthenticatedUser a, SharedLoad s) { if (a.role() == UserRole.SUPER_ADMIN) return; UUID org = requireOrgStaff(a); if (!org.equals(s.getProposerOrganization().getId()) && !org.equals(s.getCandidateOrganization().getId())) throw new ApiException(ErrorCode.NOT_FOUND, "Shared load not found"); }
}
