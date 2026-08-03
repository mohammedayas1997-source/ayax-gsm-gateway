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
    )

    fun parse(message: String): ParsedResult {

        val text = message.lowercase()

        // ===== SUCCESS =====

        if (
            text.contains("successful") ||
            text.contains("successfully") ||
            text.contains("completed") ||
            text.contains("purchase was successful") ||
            text.contains("you have successfully") ||
            text.contains("transaction successful") ||
            text.contains("approved")
        ) {
            return ParsedResult(
                ResultType.SUCCESS,
                message
            )
        }

        // ===== FAILED =====

        if (
            text.contains("failed") ||
            text.contains("insufficient") ||
            text.contains("unable") ||
            text.contains("error") ||
            text.contains("invalid") ||
            text.contains("not allowed") ||
            text.contains("try again")
        ) {
            return ParsedResult(
                ResultType.FAILED,
                message
            )
        }

        // ===== WAITING =====

        if (
            text.contains("reply") ||
            text.contains("select") ||
            text.contains("choose") ||
            text.contains("enter") ||
            text.contains("input") ||
            text.contains("press")
        ) {
            return ParsedResult(
                ResultType.WAITING,
                message
            )
        }

        return ParsedResult(
            ResultType.UNKNOWN,
            message
        )
    }
}
