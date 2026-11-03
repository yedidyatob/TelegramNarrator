package io.github.yedidyatob.telegramnarrator.domain.about

/** What About shows about the installed build (#87). Pure, so the formatting is unit-tested. */
data class AppVersionInfo(
    val versionName: String,
    val versionCode: Int,
    val buildType: String,
    /** GitHub Actions run number, empty for local builds. */
    val ciRunNumber: String,
    /** Short commit SHA: of the CI build, or from git for a local build ("-dirty" with uncommitted changes). */
    val gitSha: String,
) {
    /** "1.0.0 (10000)". versionName already carries the CI suffix for debug CI builds. */
    val version: String get() = "$versionName ($versionCode)"

    /** "CI run 123 · abc1234", "Local build · abc1234" (sha from git), or null when neither is known. */
    val buildLine: String?
        get() =
            when {
                ciRunNumber.isEmpty() && gitSha.isEmpty() -> null
                gitSha.isEmpty() -> "CI run $ciRunNumber"
                ciRunNumber.isEmpty() -> "Local build · $gitSha"
                else -> "CI run $ciRunNumber · $gitSha"
            }
}
