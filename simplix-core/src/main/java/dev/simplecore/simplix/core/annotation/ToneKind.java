package dev.simplecore.simplix.core.annotation;

/**
 * The visual weight a status value carries on a badge or a chip. Published lower-cased. PENDING is
 * a value waiting for something to start; PROCESSING one whose work is running now.
 */
public enum ToneKind {
    NEUTRAL, INFO, SUCCESS, WARNING, DANGER, PENDING, PROCESSING
}
