package app.melosome.claimconnect.ai

/**
 * One-line quota/allowance copy for the AI check header. Pure so the exact
 * user-facing wording is pinned by unit tests.
 */
fun quotaText(remaining: Int?, allowance: Int?, entitled: Boolean, simulated: Boolean, byok: Boolean = false): String {
    if (simulated) return "Demo answers in this build — nothing leaves your device"
    if (byok) return "Live analysis · billed to your Meta key"
    if (entitled) return "Unlimited checks unlocked"
    if (remaining == null || allowance == null) return "Free checks included each month"
    if (remaining <= 0) return "Free checks used up"
    if (remaining <= 2) return "$remaining of $allowance free checks left — almost out"
    return "$remaining of $allowance free checks left"
}
