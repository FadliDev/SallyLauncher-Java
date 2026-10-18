# Sally Auth - on-device auth server (skin + cape) for Sally Launcher

Source ada di `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/feature/localauth/`
(`LocalAuthServer`, `LocalAuthStore`, `SallyAuthUi`, `SallyAuthDialog`).

- Akun tipe "Sally Auth" = akun "Other Login" ke `http://127.0.0.1:25585` (authlib-injector).
- Server nyala di proses :game sebelum JVM, bind 127.0.0.1 saja, tidak ada koneksi keluar.
- Profil/skin/cape/key RSA disimpan di `<filesDir>/local_auth/`.
- Model skin (Slim/Alex atau Classic/Steve) dideteksi otomatis dari PNG skin.
- Halaman Account: tombol **Sally Auth** (tambah akun) dan **Change Skin** (ganti skin/cape akun terpilih).
- Akun lama bertipe "FeatherAuth" tetap dikenali.

Batasan: skin/cape cuma kelihatan di game sendiri (singleplayer / server offline-mode).
