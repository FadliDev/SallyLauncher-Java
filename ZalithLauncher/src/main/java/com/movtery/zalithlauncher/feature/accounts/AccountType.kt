package com.movtery.zalithlauncher.feature.accounts

enum class AccountType(val type: String) {
    MICROSOFT("Microsoft"),
    LOCAL("Local"),
    SALLY_AUTH("Sally Auth");

    companion object {
        /** Accounts saved by older builds used this name for the on-device auth account. */
        const val LEGACY_SALLY_AUTH = "FeatherAuth"
    }
}
