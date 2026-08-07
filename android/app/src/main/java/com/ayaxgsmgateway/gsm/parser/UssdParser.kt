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

        // ===== SUCCESS =====
        // An kara kalmomi da dama da networks ke amfani da su wajen nuna kudi ko gama aiki
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
            text.contains("sent to")
        ) {
            return ParsedResult(ResultType.SUCCESS, message)
        }

        // ===== WAITING =====
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

        // Idan har sakon ya zo kuma yana da dan tsawo (ba menu ba, ba error ba), 
        // maimakon mu barshi ya zama UNKNOWN, muna iya chanja shi zuwa SUCCESS 
        // domin gudun kada a rasa sakon a dashboard.
        if (text.length > 3) {
            return ParsedResult(ResultType.SUCCESS, message)
        }

        return ParsedResult(ResultType.UNKNOWN, message)
    }
}