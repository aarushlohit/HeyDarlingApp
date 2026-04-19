package com.example.silentoapp

class CommandRegistry private constructor(
    private val commands: List<CommandDefinition>,
) {
    fun grammarJson(): String {
        val values = commands.flatMap { it.aliases }.distinct().joinToString(",") { "\"$it\"" }
        return "[$values, \"[unk]\"]"
    }

    fun findCommand(transcript: String): CommandDefinition? {
        val normalized = normalize(transcript)
        if (normalized.isBlank()) {
            return null
        }

        val hotwordTrimmed = removeHotword(normalized)
        return commands.firstOrNull { command ->
            command.aliases.any { alias ->
                hotwordTrimmed == alias || hotwordTrimmed.contains(alias)
            }
        }
    }

    private fun normalize(input: String): String {
        return input
            .lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun removeHotword(input: String): String {
        return hotwords.fold(input) { current, hotword ->
            if (current.startsWith("$hotword ")) {
                current.removePrefix("$hotword ").trim()
            } else {
                current
            }
        }
    }

    companion object {
        private val hotwords = listOf("hey darling", "ok darling", "okay darling")

        private val defaultCommands =
            listOf(
                CommandDefinition(
                    keyword = "silent",
                    pendingAction = PendingAction.Silent,
                    aliases = listOf(
                        "silent",
                        "keep it silent",
                        "mute call",
                        "mute",
                        "ring off",
                        "silence phone",
                    ),
                ),
                CommandDefinition(
                    keyword = "vibrate",
                    pendingAction = PendingAction.Vibrate,
                    aliases = listOf(
                        "vibrate",
                        "vibrate mode",
                        "switch to vibrate",
                        "vibration mode",
                    ),
                ),
            )

        fun default(): CommandRegistry = CommandRegistry(defaultCommands)
    }
}
