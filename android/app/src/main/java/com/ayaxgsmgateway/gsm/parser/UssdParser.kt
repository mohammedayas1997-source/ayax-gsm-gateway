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
    text.contains("\n1.") ||
    text.contains("\n1)") ||
    text.contains("1.")
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
    text.lines().any { line ->
        val value = line.trim()

        value.startsWith("1.") ||
        value.startsWith("2.") ||
        value.startsWith("3.") ||
        value.startsWith("1)") ||
        value.startsWith("2)") ||
        value.startsWith("3)")
    }
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