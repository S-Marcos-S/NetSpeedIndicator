#!/bin/bash

# Script de publicação de Release do NetSpeedIndicator
# Repositório: S-Marcos-S/NetSpeedIndicator

VERSION=${1:-v1.2.3}
NOTES=${2:-"Atualização: ao clicar na notificação, o aplicativo agora é aberto diretamente."}
APK_PATH="app/build/outputs/apk/release/app-release.apk"

echo "🚀 Publicando Release $VERSION no GitHub..."

if [ ! -f "$APK_PATH" ]; then
    echo "🔨 Compilando APK Release..."
    ./gradlew assembleRelease
fi

if [ -f "$APK_PATH" ]; then
    gh release create "$VERSION" "$APK_PATH#NSI.apk" \
        --title "Release $VERSION" \
        --notes "$NOTES" \
        --repo "S-Marcos-S/NetSpeedIndicator"
    
    if [ $? -eq 0 ]; then
        echo "🎉 Release publicada com sucesso! Confira em: https://github.com/S-Marcos-S/NetSpeedIndicator/releases"
    else
        echo "❌ Erro ao criar release via GitHub CLI. Certifique-se de estar autenticado com 'gh auth login'."
    fi
else
    echo "❌ APK não encontrado em $APK_PATH."
fi
