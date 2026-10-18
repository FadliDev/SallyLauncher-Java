# FeatherAuth - on-device auth server (skin + cape) for Sally Launcher

Cara pasang (dari root repo launcher):
    python3 feather-auth/apply.py
Script nambah 3 file Kotlin (feature/localauth/) dan nge-patch 6 file launcher lewat anchor teks.
Kalau ada "FAILED ... anchor", file lu beda dari yang gw patch -> kirim file itu.

Cara kerja:
- Akun tipe "FeatherAuth" = akun "Other Login" yang URL-nya http://127.0.0.1:25585 (authlib-injector, sudah ada di launcher).
- Server (LocalAuthServer) nyala di proses :game sebelum JVM, bind 127.0.0.1 saja. Tidak ada koneksi keluar.
- Profil/skin/cape/key RSA disimpan di <filesDir>/local_auth/ (tidak pernah dikirim ke mana pun).
- Login launcher dilewati (nggak butuh internet buat akun ini).
- UI: tombol "Local skin account" di halaman Account -> Create new / Edit current (skin, cape, model slim).

Batasan: skin/cape cuma kelihatan di game lu sendiri (singleplayer / server offline-mode).
Server online-mode orang lain tetap verifikasi ke Mojang, jadi akun ini nggak bisa join ke sana.
