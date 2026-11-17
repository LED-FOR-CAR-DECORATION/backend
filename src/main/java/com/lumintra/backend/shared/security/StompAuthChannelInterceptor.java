package com.lumintra.backend.shared.security;

import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.Principal;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StompAuthChannelInterceptor.class);
    private static final Pattern COMMISSION_TOPIC_PATTERN = Pattern.compile("^/topic/commissions/(\\d+)$");
    private static final Set<String> MANAGEMENT_ROLES = Set.of("STAFF", "MANAGER", "ADMIN");

    private final JwtTokenProvider tokenProvider;
    private final TokenBlacklistService blacklistService;
    private final CommissionRequestRepository commissionRequestRepository;
    private final InternalEmployeeAccessService internalEmployeeAccessService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();

        if (command == StompCommand.CONNECT) {
            authenticateConnection(accessor);
        } else if (command == StompCommand.SUBSCRIBE) {
            authorizeSubscription(accessor);
        } else if (command == StompCommand.SEND) {
            // Clients send messages via REST POST /api/commissions/{id}/messages (CMS-12);
            // block direct client publishes to /topic/** so unvalidated messages cannot be spoofed.
            String destination = accessor.getDestination();
            if (destination != null && destination.startsWith("/topic/")) {
                throw new AccessDeniedException("Direct client publishing to broker topics is forbidden.");
            }
        }

        return message;
    }

    private void authenticateConnection(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            throw new AuthenticationCredentialsNotFoundException("Missing Bearer token in STOMP CONNECT frame.");
        }

        String token = authHeader.substring(7).trim();
        if (!StringUtils.hasText(token) || blacklistService.isBlacklisted(token) || !tokenProvider.validateToken(token)) {
            throw new AuthenticationCredentialsNotFoundException("Invalid or revoked JWT token.");
        }

        Authentication authentication = tokenProvider.getAuthentication(token);
        accessor.setUser(authentication);
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        if (!(principal instanceof Authentication auth) || !auth.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("Unauthenticated STOMP subscription attempt.");
        }

        String destination = accessor.getDestination();
        if (destination == null) {
            throw new AccessDeniedException("Subscription destination is required.");
        }

        Matcher matcher = COMMISSION_TOPIC_PATTERN.matcher(destination);
        if (!matcher.matches()) {
            throw new AccessDeniedException("Unsupported subscription topic: " + destination);
        }

        Long commissionId = Long.parseLong(matcher.group(1));
        CommissionRequestEntity commission = commissionRequestRepository.findById(commissionId)
                .orElseThrow(() -> new AccessDeniedException("Commission chat room not found."));

        boolean isCustomer = hasAuthority(auth, "ROLE_CUSTOMER");
        boolean isManagement = hasAuthority(auth, "ROLE_STAFF")
                || hasAuthority(auth, "ROLE_MANAGER")
                || hasAuthority(auth, "ROLE_ADMIN");

        if (isCustomer) {
            Long customerId = parseAccountId(auth.getName());
            if (!commission.isOwnedBy(customerId)) {
                log.warn("Blocked unauthorized customer STOMP subscription [commissionId={}] [customerId={}]",
                        commissionId, customerId);
                throw new AccessDeniedException("You do not own this commission request.");
            }
            return;
        }

        if (isManagement) {
            // Verifies against DB that employee is still ACTIVE with STAFF/MANAGER/ADMIN role (anti-stale-JWT)
            internalEmployeeAccessService.requireActiveEmployeeId(auth.getName(), MANAGEMENT_ROLES);
            return;
        }

        // Blocks DESIGNER and any other unauthorized roles per BR-FL3-03
        log.warn("Blocked unauthorized role STOMP subscription [commissionId={}] [subject={}]",
                commissionId, auth.getName());
        throw new AccessDeniedException("You are not authorized to subscribe to this commission chat room.");
    }

    private boolean hasAuthority(Authentication auth, String role) {
        return auth.getAuthorities() != null
                && auth.getAuthorities().stream().anyMatch(a -> role.equals(a.getAuthority()));
    }

    private Long parseAccountId(String subject) {
        try {
            return Long.parseLong(subject.trim());
        } catch (NumberFormatException ex) {
            throw new AuthenticationCredentialsNotFoundException("Invalid account ID in token.");
        }
    }
}