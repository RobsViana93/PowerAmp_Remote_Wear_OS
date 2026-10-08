# Poweramp Remote for Wear OS

Projeto pessoal em Kotlin para Galaxy S23+ e Galaxy Watch 7. O áudio continuará no celular.

## Etapa atual: comunicação básica (0.1.0)

A etapa inicial foi validada: o usuário confirmou que os aplicativos abriram no Wear OS e no celular. A versão 0.1 foi autorizada e implementa descoberta do Companion, envio de teste e confirmação correlacionada. Nenhuma integração com Poweramp.

Leia [o guia da versão 0.1](docs/V0_1.md) para os conceitos, arquivos e testes de comunicação. O checkpoint anterior está no commit f0aa9d4.

Validação local da versão 0.1: ambos os APKs compilados, dez testes de protocolo aprovados e lint sem erros (11 avisos no mobile, 6 no wear). As assinaturas coincidem. O Companion atualizado abriu no emulador e sua tela foi conferida. A confirmação entre dispositivos pareados e os testes de reconexão ainda estão pendentes.

- mobile: Poweramp Remote Companion, para o celular.
- wear: Poweramp Remote, para o relógio.

## Diagnóstico inicial em 07/10/2026

Java Temurin 21.0.12.1 e Git encontrados. Android Studio e SDK não encontrados no registro e nos caminhos padrão. ANDROID_HOME, ANDROID_SDK_ROOT e JAVA_HOME não definidos. Gradle e adb não encontrados no PATH. Isso não exclui instalações em caminhos personalizados.

Gradle 9.3.1 executou com sucesso. Após configurar o SDK, a validação revelou que Compose exige compileSdk 37. Esse ajuste foi aplicado aos dois módulos; assembleDebug e lintDebug passaram em ambos, com 0 erros de lint (6 avisos no mobile e 3 no wear). Os APKs foram gerados. A abertura das telas iniciais foi confirmada pelo usuário; a comunicação da versão 0.1 precisa de validação entre dispositivos; consulte [TESTING](docs/TESTING.md).

## Comece aqui

1. Instale Android Studio estável compatível com AGP 9.1.1.
2. Siga [DEVELOPMENT](docs/DEVELOPMENT.md) para instalar SDK 37, abrir a pasta e sincronizar.
3. Execute os comandos e os testes de [TESTING](docs/TESTING.md).

## Configuração fixada

| Ferramenta | Versão |
| --- | --- |
| Android Gradle Plugin | 9.1.1 |
| Gradle Wrapper | 9.3.1 |
| Kotlin integrado ao AGP e plugin Compose | 2.2.10 |
| Compile SDK / target SDK | 37 / 36 |
| Bytecode Java/Kotlin | 17 |
| Compose BOM | 2026.09.00 |
| Activity Compose | 1.13.0 |
| Wear Compose Material 3 | 1.7.1 |
| Android mínimo mobile / wear | API 26 / 30 |
| Play services Wearable | 20.0.1 |
| Lifecycle | 2.11.0 |
| Coroutines | 1.10.2 |

Escolhemos versões estáveis documentadas, sem buscar a atualização de cada ferramenta independentemente. JDK 21 instalado executa Gradle; o código gera bytecode 17. Wear Compose tem versão própria, fora da BOM.

## Estrutura

- settings.gradle.kts: nome, repositórios e inclusão dos dois módulos.
- build.gradle.kts: declaração dos plugins comuns.
- gradle/libs.versions.toml: catálogo central de versões e dependências.
- gradle.properties: memória do Gradle, AndroidX e estilo Kotlin.
- gradlew.bat / gradlew / gradle/wrapper: executam a versão fixada do Gradle.
- mobile/build.gradle.kts e wear/build.gradle.kts: configuração de cada APK.
- mobile/src/main e wear/src/main: código, Manifest e recursos de cada aplicativo.
- docs: explicações, decisões e testes.
- local.properties.example: exemplo do caminho do SDK; local.properties é específico do computador e ignorado pelo Git.

Leia [ARCHITECTURE](docs/ARCHITECTURE.md) para entender as decisões. Nenhuma classe de funcionalidades futuras foi criada.

## Fontes oficiais

- [Compatibilidade AGP 9.1.1](https://developer.android.com/build/releases/agp-9-1-0-release-notes)
- [Kotlin integrado](https://developer.android.com/build/migrate-to-built-in-kotlin)
- [Compose BOM](https://developer.android.com/develop/ui/compose/bom)
- [Activity](https://developer.android.com/jetpack/androidx/releases/activity)
- [Wear Compose 1.7.1 e Material 3](https://developer.android.com/jetpack/androidx/releases/wear-compose)
- [Android Studio](https://developer.android.com/studio)
- [Primeiro aplicativo Wear OS](https://developer.android.com/training/wearables/get-started/creating)

A API oficial do Poweramp será investigada antes da etapa de integração, sem presumir funcionalidades.
