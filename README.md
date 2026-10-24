# Poweramp Remote for Wear OS

Aplicativo em Kotlin para controlar o Poweramp do celular por um relógio Wear OS. O áudio permanece no celular ou nos fones conectados a ele. Desenvolvido e testado fisicamente com Galaxy S23+ e Galaxy Watch 7.

## Compatibilidade

- Relógio com Wear OS 3 ou superior (Android API 30 ou superior); não depende da marca Samsung.
- Google Play Services com a comunicação Wear Data Layer disponível.
- Pareamento com um celular Android que tenha o Companion e o Poweramp instalados. A comunicação usa a [API padrão do Google para Wear OS](https://developer.android.com/training/wearables/data/overview).
- Outros modelos Wear OS ainda precisam de validação física. Esta versão não atende Apple Watch, relógios Samsung antigos com Tizen ou outros sistemas de relógio.

## Etapa atual: estabilização (1.0.0)

A versão 1.0.0 consolida controles, informações/capa/posição, modos e volume, álbuns/faixas, equalizador com presets e atalhos. Corrige o vínculo de pedidos/respostas ao celular selecionado e limita as consultas automáticas à tela do player realmente visível. O [guia da 1.0](docs/V1_0.md) explica as mudanças, os arquivos, os limites e o roteiro de aprovação no Galaxy S23+ e Watch 7. Os resultados executados ficam em [TESTING.md](docs/TESTING.md).

Os APKs debug mantêm a assinatura anterior para atualização pessoal com install -r. A instalação física, adiada pelo usuário até esta etapa, segue [INSTALL_PHYSICAL.md](docs/INSTALL_PHYSICAL.md). Bandas/preamp e navegação por artistas/playlists/gêneros permanecem como melhorias futuras; não integram esta entrega. A aprovação física da 1.0 ainda deve ser registrada após os testes.

## Histórico: seleção de presets (0.9.0)

O usuário testou a 0.8 no Android Emulator e autorizou continuar na ordem combinada. A instalação das novas versões nos aparelhos físicos fica para a 1.0. A 0.9.0 retoma a parte 0.6b: lista presets públicos do Poweramp, permite escolher um e aplicar explicitamente pelo relógio. O [guia da 0.9](docs/V0_9.md) explica o funcionamento, os arquivos e como modificar a implementação. O preset ativo não é consultado; a interface distingue pedido enviado de mudança observada.

A sequência sugerida na 0.9 incluía bandas (0.6c) e outras categorias da biblioteca antes da estabilização. Em seguida, o usuário pediu avançar à 1.0; esses complementos permanecem para versões futuras, após comprovação na API pública e validação própria.

## Histórico: acesso rápido (0.8.0)

A 0.7 foi aprovada pelo usuário no Galaxy S23+ e no Watch 7. A versão 0.8.0 acrescenta um Tile com Abrir player e uma Complication de ícone/texto curto para mostradores compatíveis. Ambos abrem o aplicativo sem iniciar música automaticamente e sem exigir reprodução ativa. O [guia da etapa 0.8](docs/V0_8.md) explica os arquivos, os conceitos Kotlin/Java, como modificar os atalhos e como adicioná-los ao relógio. Os resultados executados e as pendências ficam em [TESTING.md](docs/TESTING.md).

## Histórico: biblioteca de álbuns (0.7.0)

O usuário confirmou os testes principais da 0.6a e priorizou a [etapa 0.7](docs/V0_7.md). A versão 0.7.0 acrescenta Biblioteca no relógio: álbuns com artista, faixas, páginas e reprodução de álbum ou faixa pelo Poweramp do celular. O Companion tem Autorizar biblioteca e Verificar acesso. O áudio permanece no celular.

Para instalar os dois APKs no Galaxy S23+ e Galaxy Watch 7 e usar sem o computador, siga [INSTALL_PHYSICAL.md](docs/INSTALL_PHYSICAL.md). A instalação é direta para uso pessoal, sem publicação na Play Store. Os resultados locais e as verificações físicas ainda pendentes estão em [TESTING.md](docs/TESTING.md).

## Histórico: equalizador — primeira parte (0.6.0)

O usuário aprovou a versão 0.5 e autorizou a implementação do [equalizador — etapa 0.6](docs/V0_6.md). A versão 0.6.0 acrescenta a primeira parte: tela Equalizador no relógio, Ligar/Desligar e leitura do último evento recebido. O comando atua nas bandas do Poweramp; preserva Tone, preset e valores das bandas. A interface distingue mudança observada de comando sem confirmação. Atualize os dois APKs e siga o guia da etapa.

Presets e edição das bandas da 0.6 foram adiados para priorizar a biblioteca solicitada pelo usuário. O controle Ligar/Desligar permanece disponível. Os resultados efetivamente executados da 0.6 ficam em [TESTING.md](docs/TESTING.md).

O relato de aprovação não especifica os dispositivos nem cada cenário executado. As pendências descritas no histórico abaixo correspondem à verificação feita pelo Codex antes dessa aprovação.

## Histórico: controles adicionais (0.5.0)

O usuário aprovou os testes da versão 0.4 e autorizou a próxima etapa. A versão 0.5 acrescenta shuffle, repeat, volume de mídia do celular e consultas automáticas enquanto a tela do relógio está ativa. A posição estimada aparece com ≈ e tem limite de avanço. Consulte [o guia da versão 0.5](docs/V0_5.md) e atualize os dois aplicativos.

Validação da versão 0.5: APKs compilados, 92 testes locais aprovados e lint sem erros (16 avisos no mobile e 18 no wear). Shuffle foi verificado no Poweramp real. Após uma ocorrência de tela preta, a versão 0.4 também apresentou o problema na mesma sessão. Reiniciar apenas o Android do relógio recuperou a tela; a mesma 0.5 foi reinstalada e exibiu navegação e avanço automático da posição. Repetição, volume e os demais cenários continuam pendentes. Detalhes e procedimento de recuperação estão em [TESTING.md](docs/TESTING.md#tela-preta-no-relógio--comparação-e-recuperação).

O usuário aprovou os testes dos botões da versão 0.3 e autorizou a versão 0.4. A etapa atual acrescenta capa por Asset, barra da última posição informada, ajuste confirmado da posição e controles agrupados na tela circular. Consulte [o guia da versão 0.4](docs/V0_4.md) para conceitos, limitações e testes. Atualize os dois aplicativos.

Validação da versão 0.4: APKs compilados, 74 testes aprovados e lint sem erros (16 avisos por módulo). Nos emuladores com Poweramp real, foram conferidos capa recebida, remoção da capa ao trocar para faixa sem imagem, Próxima pelo novo layout, seek de 0:04 para 0:24 e cancelamento do editor. O usuário confirmou os testes e autorizou a versão 0.5. Uma ocorrência de falta de resposta da interface do Poweramp foi registrada em [TESTING.md](docs/TESTING.md).

A versão 0.2 foi aprovada pelo usuário após testes dos quatro botões com músicas no celular emulado. A versão 0.3 acrescenta título, artista, álbum, duração e posição informada. O botão Atualizar pode solicitar a posição pela API oficial POS_SYNC, sem polling contínuo. Consulte [o guia da versão 0.3](docs/V0_3.md).

Validação da versão 0.3: APKs compilados, 60 testes aprovados e lint sem erros (14 avisos por módulo). Com Poweramp real nos emuladores, foram verificados metadados nos dois aplicativos, troca de faixa sem manter tags antigas e consulta de posição durante a reprodução. A posição permanece no último valor informado até um novo evento ou toque em Atualizar. O usuário confirmou os testes dos botões e autorizou a versão 0.4.

A versão 0.1 foi aprovada pelo usuário após o teste de ida e volta nos emuladores. O commit dc2d2b9 registra sua aprovação e a autorização da próxima etapa.

A versão 0.2 adiciona Play, Pause, Próxima e Anterior pela API pública do Poweramp, eventos explícitos e sincronização do último estado informado. A validação de reprodução no S23+ e Watch 7 permanece necessária. Leia [o guia da versão 0.2](docs/V0_2.md).

Validação local da versão 0.2: APKs compilados, 30 testes aprovados e lint sem erros (12 avisos no mobile e 14 no wear). Inicialmente foi testada a resposta de Poweramp ausente; depois o usuário instalou/preparou o player com músicas no emulador e confirmou os quatro controles funcionando. Testes físicos e de todos os estados de processo continuam separados.

Leia [o guia da versão 0.1](docs/V0_1.md) para os conceitos, arquivos e testes de comunicação. O checkpoint anterior está no commit f0aa9d4.

Validação da versão 0.1: ambos os APKs compilados, dez testes de protocolo aprovados e lint sem erros. As assinaturas coincidem. A troca de mensagem e confirmação nos emuladores foi executada com sucesso em 18/10/2026. Os cenários de reconexão e aparelhos físicos permanecem separados no roteiro de testes.

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
| Wear Tiles / ProtoLayout | 1.6.2 / 1.4.2 |
| Complications Data Source / Concurrent Futures | 1.3.0 / 1.3.0 |
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
