package dev.spruceworks.bounty.service;

/** Result types returned by {@link BountyService} operations, for the command/listener layer to format. */
public final class BountyOutcome {

    private BountyOutcome() {
    }

    public enum PlaceStatus { SUCCESS, BELOW_MIN, ABOVE_MAX, SELF, IMMUNE, INSUFFICIENT_FUNDS, ECONOMY_UNAVAILABLE }

    /** {@code amount} is the resolved pot contribution on SUCCESS, or the offending limit on BELOW_MIN/ABOVE_MAX. */
    public record PlaceResult(PlaceStatus status, double amount) {
    }

    public enum CancelStatus { SUCCESS, NOT_FOUND, ECONOMY_UNAVAILABLE }

    public record CancelResult(CancelStatus status, double refunded) {
    }

    public enum AdminRemoveStatus { SUCCESS, NOT_FOUND }

    public record AdminRemoveResult(AdminRemoveStatus status, double refunded, int failedRefunds) {
    }

    public record AdminClearResult(int bountyCount, double refunded, int failedRefunds) {
    }

    public enum ClaimStatus { PAID, NO_BOUNTY, COOLDOWN, SAME_IP_BLOCKED, ECONOMY_UNAVAILABLE }

    public record ClaimResult(ClaimStatus status, double amount) {
    }
}
