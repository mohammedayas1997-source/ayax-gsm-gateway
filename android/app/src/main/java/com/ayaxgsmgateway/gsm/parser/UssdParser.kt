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

    // Matches a genuine numbered menu line, e.g. "1. Buy Data" or "2) Borrow"
    // — anchored to the start of a line so it doesn't false-match things like
    // "1.2GB" or "N1.50" appearing mid-sentence in a balance/success message.
    private val MENU_LINE_REGEX = Regex("(?m)^\\s*\\d+[.)]\\s")

    fun parse(message: String): ParsedResult {

        val text = message.lowercase().trim()

        // ===== FAILED =====
        // Checked first: these are specific, decisive keywords. A menu-ish
        // word like "select" or "option" appearing alongside one of these
        // (e.g. "Invalid PIN, select 1 to retry") should still end the
        // session as FAILED, not be misread as WAITING.
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
            text.contains("cancelled")
        ) {
            return ParsedResult(ResultType.FAILED, message)
        }

        // ===== SUCCESS =====
        // Also checked before WAITING for the same reason — a final balance
        // message like "Data balance: 1.2GB. Thank you." must not be
        // swallowed by the broad WAITING keyword/number matching below.
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
            text.contains("available balance")
        ) {
            return ParsedResult(ResultType.SUCCESS, message)
        }

        // ===== WAITING =====
        // Only reached once the message didn't match a decisive FAILED or
        // SUCCESS pattern. Combines the keyword check with the precise
        // numbered-menu-line regex (previously duplicated as two separate
        // "WAITING" blocks — merged here into one).
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
            text.contains("send") ||
            text.contains("option")
        ) {
            return ParsedResult(ResultType.WAITING, message)
        }

        return ParsedResult(ResultType.UNKNOWN, message)
    }
}