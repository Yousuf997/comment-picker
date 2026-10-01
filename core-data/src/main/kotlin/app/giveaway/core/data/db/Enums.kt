package app.giveaway.core.data.db

/** Decides which screen opens from Home (spec: User flows). Stored by name. */
enum class GiveawayStatus { DRAFT, COMMITTED, IMPORTING, REVIEW, DRAWN, ARCHIVED }

/** Whether the draw code was found in the caption when it was re-read before the draw (plan A7). */
enum class CaptionCheck { FOUND, NOT_FOUND, NOT_CHECKED }

/** A winner's state after the manual follow check on S14. */
enum class ConfirmationStatus { PENDING, CONFIRMED, REPLACED }

/** Files the app writes for a giveaway. */
enum class MediaKind { VIDEO, CERTIFICATE_PDF, CERTIFICATE_IMAGE }

/** How the app unlocks when app lock is on (spec: S3, S5). */
enum class AppLockMethod { BIOMETRIC, PIN }
