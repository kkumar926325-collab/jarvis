package com.jarvis.assistant.core

/** Minimal context a brain may use. Extend later (e.g. recent turns) without touching modules. */
data class BrainContext(val aliases: Map<String, String>)

/**
 * The seam for future AI integration. A brain turns free text into a [Command]; it never touches
 * modules directly, so even a smarter brain cannot skip permission prompts or confirmations:
 * those live in the modules and the executor.
 *
 * To add an LLM later: implement this interface (e.g. try the model, fall back to
 * [RuleBasedBrain] when it returns Unknown), and pass it to [CommandExecutor].
 * Note: any cloud model would receive the user's text, so it must be opt-in and disclosed.
 */
interface AiBrain {
    fun interpret(text: String, context: BrainContext): Command
}

/** Default brain: fully offline, deterministic. */
class RuleBasedBrain : AiBrain {
    override fun interpret(text: String, context: BrainContext): Command = CommandParser.parse(text)
}
