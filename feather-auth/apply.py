#!/usr/bin/env python3
"""Applies FeatherAuth (on-device skin/cape auth server) to the launcher repo.
Run from the repo root:  python3 feather-auth/apply.py
Idempotent: edits already applied are skipped. Fails loudly if an anchor is not found
(then your file differs from what I patched - send me that file)."""
import os, shutil, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.getcwd()
M = 'ZalithLauncher/src/main/'
J = M + 'java/com/movtery/zalithlauncher/'

EDITS = {
  J + 'feature/accounts/AccountType.kt': [
    ('    LOCAL("Local")\n', '    LOCAL("Local"),\n    FEATHER("FeatherAuth")\n', 'FEATHER("FeatherAuth")'),
  ],
  J + 'feature/accounts/AccountUtils.kt': [
    ('            return account == null || account.accountType == AccountType.LOCAL.type\n',
     '            return account == null || account.accountType == AccountType.LOCAL.type || isFeatherAccount(account)\n',
     'isFeatherAccount(account)\n'),
    ('        @JvmStatic\n        fun isMicrosoftAccount(',
     '        /** FeatherAuth = on-device auth server, no network login needed. */\n'
     '        @JvmStatic\n        fun isFeatherAccount(account: MinecraftAccount?): Boolean {\n'
     '            return account != null && account.accountType == AccountType.FEATHER.type\n        }\n\n'
     '        @JvmStatic\n        fun isMicrosoftAccount(',
     'fun isFeatherAccount('),
  ],
  J + 'launch/LaunchArgs.kt': [
    ('        if (AccountUtils.isOtherLoginAccount(account)) {\n            if (account.otherBaseUrl.contains("auth.mc-user.com")) {',
     '        if (AccountUtils.isOtherLoginAccount(account)) {\n            if (AccountUtils.isFeatherAccount(account)) {\n'
     '                // on-device auth server must be listening before the JVM (and authlib-injector) starts\n'
     '                com.movtery.zalithlauncher.feature.localauth.LocalAuthServer.ensureStarted()\n            }\n'
     '            if (account.otherBaseUrl.contains("auth.mc-user.com")) {',
     'LocalAuthServer.ensureStarted()'),
  ],
  J + 'launch/LaunchGame.kt': [
    ('            if (!networkAvailable) {\n                // ',
     '            if (!networkAvailable && !AccountUtils.isFeatherAccount(AccountsManager.currentAccount)) {\n                // ',
     'isFeatherAccount(AccountsManager.currentAccount)'),
  ],
  J + 'ui/fragment/AccountFragment.kt': [
    ('    private lateinit var binding: FragmentAccountBinding\n',
     '    private lateinit var binding: FragmentAccountBinding\n'
     '    private val featherUi = com.movtery.zalithlauncher.feature.localauth.FeatherAccountUi(this)\n',
     'val featherUi'),
    ('            returnButton.setOnClickListener(this@AccountFragment)\n',
     '            returnButton.setOnClickListener(this@AccountFragment)\n'
     '            featherAccountButton.setOnClickListener { featherUi.showMenu() }\n',
     'featherAccountButton.setOnClickListener'),
  ],
  M + 'res/layout/fragment_account.xml': [
    ('                app:layout_constraintBottom_toTopOf="@id/return_button"\n'
     '                app:layout_constraintEnd_toEndOf="parent"\n'
     '                app:layout_constraintStart_toStartOf="parent"\n'
     '                app:layout_constraintTop_toTopOf="parent" />',
     '                app:layout_constraintBottom_toTopOf="@id/feather_account_button"\n'
     '                app:layout_constraintEnd_toEndOf="parent"\n'
     '                app:layout_constraintStart_toStartOf="parent"\n'
     '                app:layout_constraintTop_toTopOf="parent" />',
     'toTopOf="@id/feather_account_button"'),
    ('            <com.movtery.zalithlauncher.ui.view.AnimButton\n                android:id="@+id/return_button"',
     '            <com.movtery.zalithlauncher.ui.view.AnimButton\n'
     '                android:id="@+id/feather_account_button"\n'
     '                android:layout_width="0dp"\n'
     '                android:layout_height="wrap_content"\n'
     '                android:layout_marginHorizontal="@dimen/_8sdp"\n'
     '                android:layout_marginBottom="@dimen/_8sdp"\n'
     '                android:text="Local skin account"\n'
     '                app:layout_constraintBottom_toTopOf="@id/return_button"\n'
     '                app:layout_constraintEnd_toEndOf="parent"\n'
     '                app:layout_constraintStart_toStartOf="parent"\n'
     '                tools:ignore="MissingConstraints,HardcodedText" />\n'
     '            <com.movtery.zalithlauncher.ui.view.AnimButton\n                android:id="@+id/return_button"',
     'android:id="@+id/feather_account_button"'),
  ],
}

def main():
    # 1) new files
    src_root = os.path.join(HERE, 'new_files')
    for dp, _, files in os.walk(src_root):
        for f in files:
            s = os.path.join(dp, f)
            d = os.path.join(ROOT, os.path.relpath(s, src_root))
            os.makedirs(os.path.dirname(d), exist_ok=True)
            shutil.copyfile(s, d)
            print('added  ', os.path.relpath(d, ROOT))
    # 2) edits
    bad = 0
    for rel, edits in EDITS.items():
        p = os.path.join(ROOT, rel)
        if not os.path.exists(p):
            print('MISSING', rel); bad += 1; continue
        text = open(p, encoding='utf-8').read()
        for old, new, marker in edits:
            if marker in text:
                print('skip   ', rel, '(already applied)'); continue
            if text.count(old) != 1:
                print('FAILED ', rel, '-> anchor found', text.count(old), 'times:', repr(old[:70])); bad += 1; continue
            text = text.replace(old, new)
            print('patched', rel)
        open(p, 'w', encoding='utf-8').write(text)
    sys.exit(1 if bad else 0)

main()
