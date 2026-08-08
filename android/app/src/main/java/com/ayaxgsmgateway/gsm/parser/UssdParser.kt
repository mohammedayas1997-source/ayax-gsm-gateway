package com.ayaxgsmgateway.gsm.parser

object UssdParser {

    enum class ResultType {
        SUCCESS,
        FAILED,
        WAITING,
        UNKNOWN
    }

    data class ParsedResult(
        val type: ResultType,
        val message: String
    ) {
        override fun toString(): String {
            return "ParsedResult(type=$type, message='$message')"
        }
    }

    private val MENU_LINE_REGEX = Regex("(?m)^\\s*\\d+[.)]\\s")

    fun parse(message: String): ParsedResult {
        val text = message.lowercase().trim()

        // ===== FAILED =====
        if (
            text.contains("failed") ||
            text.contains("failure") ||
            text.contains("insufficient") ||
            text.contains("unable") ||
            text.contains("error") ||
            text.contains("invalid") ||
            text.contains("not allowed") ||
            text.contains("try again") ||
            text.contains("expired") ||
            text.contains("cancelled") ||
            text.contains("wrong") ||
            text.contains("decline")
        ) {
            return ParsedResult(ResultType.FAILED, message)
        }

        // ===== WAITING (MENU / OPTIONS) =====
        // Mun fara duba WAITING da farko domin idan saƙon menu ne mai lamba (misali 1. Buy 2. Check), kar a dauke shi a matsayin SUCCESS
        val hasMenuLine = MENU_LINE_REGEX.containsMatchIn(message)

        if (
            hasMenuLine ||
            text.contains("reply with") ||
            text.contains("reply") ||
            text.contains("select") ||
            text.contains("choose") ||
            text.contains("enter") ||
            text.contains("input") ||
            text.contains("press") ||
            text.contains("option")
        ) {
            return ParsedResult(ResultType.WAITING, message)
        }

        // ===== SUCCESS =====
        // Sai a tabbatar cewa SUCCESS zai fito ne kawai idan akwai takamaiman alamun cewa an kammala aiki ko an samu sakamako
        if (
            text.contains("successful") ||
            text.contains("successfully") ||
            text.contains("completed") ||
            text.contains("approved") ||
            text.contains("thank you") ||
            text.contains("dear customer") ||
            text.contains("bundle") ||
            text.contains("airtime balance") ||
            text.contains("data balance") ||
            text.contains("available balance") ||
            text.contains("account balance") ||
            text.contains("bal:") ||
            text.contains("balance is") ||
            text.contains("naira") ||
            text.contains("ngn") ||
            text.contains("transfer of") ||
            text.contains("sent to") ||
            text.contains("₦") ||
            Regex("\\b[0-9]+(?:\\.[0-9]+)?\\s*(?:gb|mb|kb|gigs?)\\b").containsMatchIn(text)
        ) {
            return ParsedResult(ResultType.SUCCESS, message)
        }

        // Idan har ba menu bane, ba error bane, kuma babu alamar nasara/kudi a ciki, 
        // muna mayar da shi UNKNOWN ko WAITING maimakon mu saurin cewa shi SUCCESS ne.
        return ParsedResult(ResultType.UNKNOWN, message)
    }
}