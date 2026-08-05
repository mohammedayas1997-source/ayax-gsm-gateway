package com.ayaxgsmgateway.gsm.parser
import kotlin.text.Regex


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
    )

    fun parse(message: String): ParsedResult {

        val text = message.lowercase().trim()

        // ===== WAITING =====

        if (
            text.contains("reply with") ||
            text.contains("reply") ||
            text.contains("select") ||
            text.contains("choose") ||
            text.contains("enter") ||
            text.contains("input") ||
            text.contains("press") ||
            text.contains("send") ||
            text.contains("option") ||
            text.contains("please wait") ||
            text.contains("being processed") ||
            text.contains("processing") ||
            text.contains("loading") ||
            text.contains("connecting")
            Regex("""\n\s*1[\). ]""").find(text) != null
        ) {

            return ParsedResult(
                ResultType.WAITING,
                message
            )

        }

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
            text.contains("cancelled")
        ) {

            return ParsedResult(
                ResultType.FAILED,
                message
            )

        }

        if (
    Regex("""(^|\n)\s*\d+[\).\-: ]""").containsMatchIn(text)
) {

    return ParsedResult(
        ResultType.WAITING,
        message
    )

}

        // ===== SUCCESS =====

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

    return ParsedResult(
        ResultType.SUCCESS,
        message
    )

}

        return ParsedResult(
            ResultType.UNKNOWN,
            message
        )
    }

}