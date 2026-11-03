# Pulse – Player de música para Android

Kotlin + Jetpack Compose + Media3. O APK é gerado pelo GitHub Actions.

## Como gerar o APK
1. Crie um repositório no GitHub e envie todos os arquivos desta pasta (inclusive `.github/`).
2. Abra a aba **Actions** → **Build APK**. O build roda sozinho a cada push (ou clique em *Run workflow*).
3. Quando ficar verde, abra a execução e baixe **Pulse-APK** em *Artifacts*. Dentro do zip está o `app-release.apk`.
4. Instale no celular (permita "instalar apps desconhecidos").

Se o build falhar, abra o log do passo **Build APK** e mande o erro para corrigir.

## Pelo Termux
```
pkg install git unzip
unzip Pulse-Player.zip && cd Pulse
git init && git add . && git commit -m "Pulse"
git branch -M main
git remote add origin https://github.com/SEU_USUARIO/SEU_REPO.git
git push -u origin main
```
(No push, o GitHub pede usuário e um token de acesso pessoal no lugar da senha.)
