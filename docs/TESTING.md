# Validação das etapas

**Etapa atual: versão 0.9.** Use o [roteiro de presets](V0_9.md#fluxo-de-uso-no-emulador). A versão 0.7 foi aprovada pelo usuário no Galaxy S23+ e Watch 7; a 0.8 foi testada por ele no Android Emulator. Por sua preferência, as próximas atualizações físicas ficam para a 1.0. As seções anteriores permanecem como histórico; suas pendências não indicam que os testes tenham sido executados posteriormente.

## Estado

Java foi executado e retornou Temurin 21.0.12.1. O comando gradlew.bat --version foi executado com sucesso: Gradle 9.3.1, Launcher JVM 21.0.12.1. O Kotlin 2.2.21 exibido nesse comando é o Kotlin interno do Gradle, não a versão do compilador do aplicativo. Na inspeção inicial, SDK e Studio não foram encontrados nos locais inspecionados. Na validação posterior, o SDK já estava configurado em local.properties, com as plataformas 36 e 37.0 instaladas. A compilação dos dois módulos e o lint passaram após ajustar compileSdk para 37. O usuário confirmou que as telas iniciais abriram no Wear OS e no celular. Sem teste de comunicação ou Poweramp nesta etapa.

## Compilar

Após instalar o SDK e sincronizar, na raiz do projeto:

~~~powershell
.\gradlew.bat :mobile:assembleDebug :wear:assembleDebug
.\gradlew.bat :mobile:lintDebug :wear:lintDebug
~~~

Resultado esperado: BUILD SUCCESSFUL e estes arquivos:

- mobile/build/outputs/apk/debug/mobile-debug.apk
- wear/build/outputs/apk/debug/wear-debug.apk

Ter arquivos Kotlin e Gradle não comprova que os APKs compilam. Só registre sucesso após executar as tarefas.

## Emulador Wear OS

1. Android Studio > Tools > Device Manager > Create Virtual Device.
2. Escolha a categoria Wear OS e um perfil circular, por exemplo Wear OS Small Round.
3. Baixe uma imagem estável Wear OS oferecida para esse perfil (API 30 ou superior). O SDK de compilação 37 não exige que o emulador rode API 37.
4. Conclua a criação e inicie o emulador. Se ele não iniciar, confira virtualização na BIOS e os requisitos de aceleração do Android Emulator.
5. Selecione a configuração do módulo wear e o emulador Wear OS na barra de execução.
6. Clique Run. A tela deve mostrar Poweramp Remote, Aplicativo do relógio e Preparação inicial.

Não é necessário parear um celular para esta tela estática.

## Emulador de celular

Crie um perfil Phone com imagem Android API 26 ou superior. Selecione mobile e esse emulador; clique Run. A tela deve mostrar Poweramp Remote Companion, Aplicativo do celular e Preparação inicial.

## Checklist visual

- Ambos abrem sem encerrar inesperadamente.
- Os textos ficam legíveis e não cortados.
- No relógio, teste tela circular pequena e fonte aumentada; role se necessário.
- No celular, teste fonte aumentada e rotação.
- Reabra cada aplicativo após fechá-lo.

## ADB e logs

Depois de instalar Platform-Tools, ajuste o caminho abaixo:

~~~powershell
$adbPath = 'C:\Users\rober\AppData\Local\Android\Sdk\platform-tools\adb.exe'
& $adbPath devices
~~~

Com dois dispositivos ativos, sempre selecione o número SERIAL exibido:

~~~powershell
& $adbPath -s SERIAL install -r .\wear\build\outputs\apk\debug\wear-debug.apk
& $adbPath -s SERIAL shell am start -n com.powerampremote/com.powerampremote.wear.MainActivity
~~~

Use o serial do relógio. Para o celular, use mobile-debug.apk e com.powerampremote/com.powerampremote.mobile.MainActivity.

No Android Studio, abra Logcat, selecione o dispositivo e filtre package:com.powerampremote. Procure FATAL EXCEPTION em caso de fechamento. Guarde a mensagem completa e a primeira causa (Caused by).

## Dispositivos físicos, quando estiverem disponíveis

No S23+, ative Opções do desenvolvedor e Depuração USB, conecte um cabo de dados e aceite a chave RSA. No Watch 7, ative Opções do desenvolvedor e depuração sem fio; use a tela Pair Devices Using Wi-Fi do Studio ou adb pair IP:PORTA seguido de adb connect IP:PORTA. As portas de pareamento e conexão são diferentes e podem mudar. Não presuma a porta 5555. Consulte [depuração sem fio oficial](https://developer.android.com/studio/run/device#wireless).

Escolha cada dispositivo correto para executar seu módulo. Os emuladores verificam instalação e interface; as futuras etapas de Data Layer e Poweramp também precisarão dos aparelhos físicos.

## Critério de conclusão

Esta etapa só estará validada após ambos os APKs compilarem e as telas funcionarem. Depois disso, aguardar autorização antes de iniciar a versão 0.1.

## Resultado da tentativa de compilação em 07/10/2026

Comando executado: gradlew.bat :mobile:assembleDebug :wear:assembleDebug --console=plain. Resultado: BUILD FAILED, código de saída 1, após 1m26s. Causa reportada: SDK location not found, ao determinar dependências de :mobile:compileDebugJavaWithJavac. O Gradle carregou a configuração, mas a compilação Kotlin, a resolução completa das bibliotecas de interface e os APKs não foram validados. Não há evidência de sucesso de nenhum módulo.

Os seis arquivos XML foram lidos pelo parser XML do PowerShell sem erro de sintaxe. Isso não substitui a validação Android pelo SDK.

Instale o SDK seguindo DEVELOPMENT.md, configure local.properties e execute novamente os comandos desta página. Não foi instalado um SDK automaticamente nem foram aceitas licenças em nome do usuário.

## Como compartilhar um erro do Gradle

Copie desde FAILURE: Build failed with an exception, incluindo What went wrong e Caused by quando houver. Para salvar o diagnóstico em um arquivo na raiz do projeto:

~~~powershell
.\gradlew.bat :mobile:assembleDebug :wear:assembleDebug --console=plain --stacktrace *> erro-gradle.txt
~~~

Para investigar lint, substitua as tarefas por :mobile:lintDebug :wear:lintDebug. Informe o comando utilizado e compartilhe o trecho relevante ou indique o arquivo ao assistente.

## Revalidação após configuração do SDK em 07/10/2026

O erro reproduzido foi :mobile:checkDebugAarMetadata: nove bibliotecas Compose exigiam API 37, enquanto compileSdk estava em 36. A configuração inicial foi corrigida em mobile/build.gradle.kts e wear/build.gradle.kts, usando compileSdk 37; targetSdk e minSdk permaneceram iguais.

Comando executado:

~~~powershell
.\gradlew.bat :mobile:assembleDebug :wear:assembleDebug :mobile:lintDebug :wear:lintDebug --console=plain
~~~

Resultado: BUILD SUCCESSFUL em 4m32s, código de saída 0. Ambos os APKs foram conferidos em build/outputs/apk/debug. Lint mobile: 0 erros e 6 avisos; lint wear: 0 erros e 3 avisos.

Os avisos abordam targetSdk anterior ao mais recente, versões mais novas de ferramentas, regras modernas de backup, ícones ainda não definidos e taskAffinity da Activity Wear para a lista de recentes. Não foram suprimidos; não impedem esta compilação. A tarefa de empacotamento também informou que manteve os símbolos de libandroidx.graphics.path.so.

Relatórios completos: mobile/build/reports/lint-results-debug.html e wear/build/reports/lint-results-debug.html.

Esta validação confirma compilação e análise estática. Ainda é necessário abrir os aplicativos nos emuladores e verificar as telas, conforme o checklist acima. A versão 0.1 foi autorizada posteriormente; seu roteiro está em [V0_1.md](V0_1.md).

## Comunicação básica — versão 0.1

A implementação e o roteiro estão em [V0_1.md](V0_1.md). A confirmação de abertura das telas anteriores não valida esta nova comunicação. Use dispositivos pareados e os dois APKs atualizados. Registre separadamente compilação, testes locais, lint e teste real de ida e volta.

## Verificação executada da versão 0.1 em 07/10/2026

- Checkpoint anterior: commit f0aa9d4, feito antes da implementação desta versão.
- Compilação dos APKs e lint dos dois módulos: BUILD SUCCESSFUL. Após ajustar o contraste das telas, a validação conjunta foi repetida e terminou com código 0 em 1m26s.
- Testes locais: cinco por módulo, dez ao todo, sem falhas ou erros. Os relatórios XML foram conferidos.
- Lint: zero erros; onze avisos no mobile e seis no wear. Há avisos sobre versões, ícones, backup, recursos não usados e identificação de texto; a capability estática é consumida pelo Play services, embora o lint a classifique como não usada.
- Paridade das duas cópias de TestProtocol: conferida, diferindo apenas pelo package.
- Assinaturas dos dois APKs: verificadas com apksigner, com o mesmo certificado debug.
- git diff --check: sem erros de espaços em branco.
- Companion instalado no emulator-5554. A primeira abertura excedeu o prazo do ADB; depois a Activity ficou retomada, sem erro AndroidRuntime na saída consultada. A captura revelou baixo contraste no título, corrigido em ambos os módulos. Depois da atualização, a abertura retornou Status: ok e a captura final foi inspecionada: título, descrição e Aguardando teste do relógio legíveis, com barras do sistema adequadas. Captura local: mobile/build/reports/companion-v01.png.
- Comunicação real, timeout em uma rota real, reconexão e recebimento com a tela do Companion fechada: ainda não executados. Somente um emulador de celular estava conectado; não havia relógio pareado disponível para o teste.

Não considerar os testes locais como prova de funcionamento da Data Layer entre os aparelhos. A versão 0.2 aguarda validação desta etapa e nova autorização.

## Teste de ida e volta nos emuladores em 18/10/2026

- Dispositivos conectados: celular emulator-5554 e Wear OS emulator-5556.
- Ao investigar o botão Testar, o relógio estava em modo de espera: dumpsys power informou mWakefulness=Dozing.
- Após acordar o relógio e abrir o aplicativo, a inspeção da interface confirmou Companion acessível e o botão Testar com enabled=true e clickable=true.
- Um toque no botão, seguido de rolagem para visualizar o resultado, exibiu: Confirmado: o celular recebeu o teste. Isso comprova uma troca de mensagem e confirmação entre os emuladores nessa execução.
- Nenhum código foi alterado. Para repetir manualmente, acorde o emulador pelo botão de energia/lateral, abra o aplicativo e toque Testar com a tela ativa. Arraste para cima para ler o resultado abaixo do botão.
- Permanecem pendentes os testes de desconexão/reconexão, ausência de confirmação e execução nos aparelhos físicos. Esta execução não comprova todos esses cenários.

Em 18/10/2026, o usuário confirmou o teste aprovado e as alterações válidas, e autorizou o início da etapa seguinte. Os cenários adicionais acima permanecem identificados como pendentes.

## Versão 0.2 — validação executada em 18/10/2026

- Aprovação da versão 0.1 salva no commit dc2d2b9 antes da implementação seguinte.
- API oficial consultada: maxmpz/powerampapi, PowerampAPI.java, Manifest do exemplo e APIReceiver.java. Implementação limitada aos quatro comandos e eventos de estado/faixa.
- Comando executado: gradlew.bat :mobile:assembleDebug :wear:assembleDebug :mobile:testDebugUnitTest :wear:testDebugUnitTest :mobile:lintDebug :wear:lintDebug --console=plain.
- Resultado final: BUILD SUCCESSFUL em 48s, código de saída 0. Foram 102 tarefas, 30 executadas e 72 atualizadas.
- Relatórios JUnit conferidos: 10 testes de PlayerProtocol e 5 de TestProtocol por módulo; total de 30 testes, zero falhas e zero erros.
- Lint: zero erros, 12 avisos no mobile e 14 no wear. Relatórios em mobile/build/reports/lint-results-debug.html e wear/build/reports/lint-results-debug.html.
- APKs instalados nos dois emuladores com Success. Abertura das Activities retornou Status: ok em ambos.
- Verificação de pacote no celular emulado não encontrou com.maxmpz.audioplayer. Pedido Play pelo relógio retornou Poweramp não instalado no celular, com estado ainda desconhecido.
- Teste v1 repetido depois da atualização: Confirmado: o celular recebeu o teste.
- Captura dos controles inspecionada em wear/build/reports/player-v02.png. A tela exige rolagem, e o emulador deve estar acordado para receber o toque.
- git diff --check executado sem erros.

Na execução inicial acima, não foram testados reprodução em Poweramp real, eventos reais de estado/faixa, sincronização disparada por esses eventos, os três estados de processo e restrições da Samsung. Posteriormente, o usuário instalou o Poweramp, copiou músicas para o celular emulado e confirmou todos os quatro botões funcionando, autorizando a versão 0.3. Use a [matriz física da versão 0.2](V0_2.md#teste-no-s23-e-watch-7) para os cenários ainda pendentes.

## Versão 0.3 — validação executada em 18/10/2026

- Comando final: `gradlew.bat :mobile:assembleDebug :wear:assembleDebug :mobile:testDebugUnitTest :wear:testDebugUnitTest :mobile:lintDebug :wear:lintDebug --console=plain`.
- Resultado: BUILD SUCCESSFUL em 1m02s; 102 tarefas, 21 executadas e 81 atualizadas.
- Relatórios JUnit: 15 testes de NowPlayingProtocol, 10 de PlayerProtocol e 5 de TestProtocol em cada módulo; 60 testes ao todo, zero falhas e erros. Incluem Unicode, dados inválidos, unidades, ausência de campos e ordenação independente de metadados e posição.
- Lint: zero erros e 14 avisos por módulo. O registro dinâmico do receptor de posição usa ContextCompat com RECEIVER_EXPORTED, necessário para receber o evento do Poweramp.
- APKs finais instalados com Success; Activities abertas com Status: ok nos emuladores emulator-5554 e emulator-5556.
- Integração com Poweramp real, build-1031-bundle-play: Companion e relógio mostraram Sub Specie Aeternitatis, AD NAUSEAM, Live at Roadburn [24bit] e duração informada 14:39. A interface nativa arredondou a duração para 14:40.
- A troca para Teste-01 pelo botão Próxima do próprio Poweramp atualizou automaticamente o relógio com o Companion em segundo plano. Artista e álbum passaram a não informados, sem preservar as tags anteriores. Esta execução não comprovou troca de metadados usando Próxima no relógio.
- Durante a reprodução de Teste-01, Atualizar no relógio retornou O celular recebeu a posição do Poweramp e exibiu 0:27 / 1:00, com último informe reproduzindo. O valor é uma posição reportada e permanece estático entre informes. Uma leitura posterior do player já indicava 0:37.
- Telas inspecionadas nas capturas locais mobile/build/reports/companion-v03.png e wear/build/reports/player-v03.png. A tela circular exige rolagem e deve estar acordada para receber toques.
- Ao encerrar a verificação foi enviado o comando oficial de pausa ao Poweramp.

Permanecem pendentes a validação do usuário da versão 0.3, testes físicos no S23+/Watch 7, matriz completa de processos, reconexão e restrições de bateria. Os testes locais não substituem esses cenários. Siga [V0_3.md](V0_3.md) para repetir a verificação nos emuladores.

Posteriormente, o usuário confirmou que todos os botões funcionaram na comunicação com o Poweramp e autorizou a versão 0.4. Essa aprovação não comprova automaticamente a matriz física, reconexão e restrições de bateria.

## Versão 0.4 — validação executada em 18/10/2026

- Comando final: `gradlew.bat :mobile:assembleDebug :wear:assembleDebug :mobile:testDebugUnitTest :wear:testDebugUnitTest :mobile:lintDebug :wear:lintDebug --console=plain`.
- Resultado final: BUILD SUCCESSFUL em 47s; 102 tarefas, 18 executadas e 84 atualizadas. As verificações anteriores também passaram; a execução final inclui a proteção do realId contra eventos de metadados antigos.
- Relatórios JUnit conferidos: 37 testes por módulo, 74 ao todo, sem falhas ou erros. São 15 de metadados, 10 de comandos v2, 5 do teste v1 e 7 do novo protocolo visual. Os novos casos verificam codecs, campos inválidos, duração desconhecida, fim da faixa, referência antiga e progresso limitado.
- Lint: zero erros e 16 avisos por módulo. Relatórios locais: mobile/build/reports/lint-results-debug.html e wear/build/reports/lint-results-debug.html. Os avisos incluem recomendações de versões, recursos, ícones, backup e uso de extensões Kotlin; não foram suprimidos para obter esse resultado.
- Instalação dos dois APKs nos emuladores retornou Success. As Activities abriram com Status: ok. Capturas foram inspecionadas e motivaram redução da capa/espaçamentos para mostrar os três controles na tela inicial e correção do contraste dos ícones.
- Seek real: Teste-01 estava pausada em 0:04 de 1:00. No relógio, foram usados Ajustar posição, dois incrementos de +10s e Confirmar posição. O relógio exibiu Poweramp informou a posição solicitada e 0:24 / 1:00. A interface nativa do Poweramp confirmou 0:24 após sua recuperação descrita abaixo.
- Capa real: a leitura inicial apenas da raiz do provedor retornou FileNotFoundException. O helper oficial foi consultado e a implementação corrigida para files/realId, sem presumir equivalência com id. Um novo evento nativo de faixa forneceu o identificador. A capa de Live at Roadburn chegou pelo Asset e foi conferida visualmente no relógio com Sub Specie Aeternitatis / AD NAUSEAM.
- Próxima no novo layout do relógio mudou para Teste-01; a imagem anterior desapareceu e surgiu o indicador Sem capa disponível. O Companion registrou a nova faixa com realId 5. Na faixa com capa havia registrado realId 4.
- Cancelamento: com Teste-01 em 0:00, foi aberto o editor, selecionado +10s e tocado Cancelar. O editor fechou; posição e horário persistidos continuaram iguais, sem novo seek.
- Detalhes e teste abriu a tela de diagnósticos com Voltar e Companion acessível. A troca de mensagens v1 não foi repetida nesta execução.
- Evidências locais ignoradas pelo Git: wear/build/reports/player-v04.png, wear/build/reports/player-v04-cover.png e mobile/build/reports/poweramp-v04-cover.png. As capturas do Poweramp são evidências do teste; não são recursos visuais distribuídos pelo aplicativo.

Durante a investigação, o Android apresentou Poweramp isn't responding. O log am_anr registrou timeout de foco da interface do Poweramp; também havia um timeout separado do launcher. A opção Aguardar permitiu ler a posição 0:24, mas o aviso reapareceu. Após encerrar pelo diálogo e reabrir o Poweramp, a interface respondeu e os testes de biblioteca/capa prosseguiram. A causa não foi estabelecida; não atribuir o aviso ao Remote nem considerar esse cenário estabilizado. Registrar novamente se ocorrer na validação do usuário ou nos aparelhos físicos.

Permanecem pendentes a aprovação do usuário da versão 0.4, fonte ampliada, matriz completa de desconexão/reconexão e processos, troca concorrente durante seek em integração e testes físicos no S23+/Watch 7. O teste local de referência antiga não substitui a simulação dessa corrida nos aparelhos. O roteiro está em [V0_4.md](V0_4.md).

Posteriormente, o usuário confirmou que os testes estão de acordo e autorizou a próxima etapa. Essa aprovação da versão 0.4 não comprova os cenários físicos e de processo ainda não documentados como executados.

## Versão 0.5 — validação executada em 18/10/2026

- API consultada: PowerampAPI.java (SHUFFLE=9, REPEAT=8, valores dos modos e limites do broadcast PLAYING_MODE_CHANGED); AudioManager oficial para STREAM_MUSIC e volume fixo; documentação Android de ciclo de vida e coroutines.
- Comando final: `gradlew.bat :mobile:assembleDebug :wear:assembleDebug :mobile:testDebugUnitTest :wear:testDebugUnitTest :mobile:lintDebug :wear:lintDebug --console=plain`.
- Resultado: BUILD SUCCESSFUL em 2m; 102 tarefas, 31 executadas e 71 atualizadas. Após essa execução, somente comentários e documentação foram ajustados.
- Relatórios JUnit conferidos: 46 testes por módulo, 92 ao todo, sem falhas ou erros. Por módulo: 9 de ControlsProtocol, 15 de NowPlayingProtocol, 10 de PlayerProtocol, 5 de TestProtocol e 7 de VisualProtocol. Os novos casos incluem posição correlacionada sem depender de relógios alinhados, comandos/resultados inválidos, índices de volume, duração desconhecida, limite de 30 segundos e proteção contra overflow na estimativa.
- Lint: zero erros, 16 avisos no mobile e 18 no wear. Os relatórios HTML/XML ficam em build/reports de cada módulo. Os avisos não foram suprimidos.
- APKs finais instalados com Success nos emuladores emulator-5554 e emulator-5556. Aberturas retornaram Status: ok. O relógio descobriu o Companion e mostrou faixa, artista, capa e posição. O Poweramp nativo também abriu no celular.
- A página Mais controles foi aberta e inspecionada visualmente na implementação inicial. O primeiro informe mostrou aleatório e repetição desativados. Após tocar Misturar músicas, o texto passou a Aleatório informado: músicas da categoria. Depois de Desativar aleatório, foi novamente observado desativado. Esses testes antecederam o ajuste final do payload de posição; a implementação dos comandos de modos permaneceu a mesma.
- Volume de mídia do emulador lido por dumpsys audio antes e ao fim: mínimo 0, máximo 15 e nível 5, saída speaker. Os botões de volume não chegaram a ser acionados com sucesso na automação; não registrar alteração/restauração de volume como teste aprovado.
- As tentativas de automação seguintes não localizaram os botões esperados. A investigação mostrou capturas inteiramente pretas do relógio, apesar de a hierarquia ainda conter os controles. O mesmo ocorreu no launcher após o botão lateral. dumpsys informou display ON, teatro desativado e Activity do Remote retomada; a causa não foi estabelecida. Reabrir apenas o Remote por force-stop/am start não recuperou as capturas. Não atribuir essa ocorrência ao aplicativo sem evidência adicional.
- Captura inicial inspecionada: wear/build/reports/controls-v05.png. Capturas pretas e hierarquias de diagnóstico também ficaram em build/reports ou no armazenamento temporário dos emuladores, fora do Git.
- git diff --check executado sem erros; paridade dos protocolos/testes v5 conferida, diferindo apenas pelo package.

Permanecem pendentes: testes manuais de repeat, volume, avanço estimado e consultas periódicas no APK final; interrupção ao apagar/sair da tela; suspensão após falhas/reconexão; rotas Bluetooth/volume fixo; e validação física no S23+/Watch 7. Testes puros de cálculo e inspeção do código não comprovam o ciclo de vida nem a integração real desses cenários. O roteiro está em [V0_5.md](V0_5.md).

## Tela preta no relógio — comparação e recuperação

Após o usuário confirmar que somente o relógio estava preto depois do commit 6e3219c, a comparação foi executada no emulator-5556 em 18/10/2026, horário de São Paulo:

1. A versão 0.4 do commit 35dbbcc foi extraída em wear/build/regression-v04, sem mudar o checkout. Compilação isolada de :wear:assembleDebug: BUILD SUCCESSFUL em 1m57s.
2. O APK 0.4 foi instalado com adb install -r -d, preservando os dados. dumpsys package confirmou versionCode=5 e versionName=0.4.0. A captura continuou inteiramente preta. Portanto, apenas reinstalar a versão anterior não recuperou essa sessão.
3. Foi executado adb -s emulator-5556 reboot. Somente o Android do relógio foi reiniciado. Aplicativos, dados e pareamento foram preservados; o celular permaneceu ligado.
4. Após sys.boot_completed=1, a 0.4 abriu e voltou a produzir uma captura colorida com título, artista, capa e controles.
5. O APK 0.5 de 6e3219c foi reinstalado, sem alteração na implementação. Abertura retornou Status: ok e a captura voltou a conter os mesmos controles e metadados. O estado Celular conectado reapareceu.
6. Play e rolagem voltaram a responder. Durante a reprodução de Wizard Party, uma captura mostrou ≈ 1:22 / 2:18 e Último informe: reproduzindo. Mais controles também abriu e mostrou Aleatório informado: desativado. Ao fim, a interface informou pausado.

Evidências locais: wear/build/reports/regression-v04.png (preta), regression-v04-reboot.png (0.4 após reinício), regression-v05-reboot.png e regression-current.png (0.5, incluindo avanço estimado). As duas capturas após reinício continham pixels coloridos, conferidos também por leitura dos pixels do PNG. Os arquivos ficam fora do Git.

O reinício recuperou o estado observado, mas a causa específica da tela preta não foi estabelecida. A sequência temporal relatada pelo usuário é válida; este teste não permite atribuir a origem ao commit nem garantir que o problema não volte. Não considerar os testes unitários como validação visual da tela. Nenhum código foi alterado nesta recuperação; o commit seguinte registra somente o diagnóstico e o procedimento.

Para repetir a recuperação neste ambiente:

~~~powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb devices
# Confira o serial do relógio; nesta investigação foi emulator-5556.
& $adb -s emulator-5556 reboot
~~~

Aguarde o relógio concluir a inicialização, acorde a tela pelo botão lateral e abra o Remote. A reinicialização preserva o armazenamento dos aplicativos.

Reprodução, navegação e avanço estimado foram observados após a recuperação. Permanecem pendentes a matriz completa de interrupção/retomada da atualização automática, repeat, volume, reconexão e dispositivos físicos. Não houve nova execução dos testes locais/lint: a implementação permaneceu a de 6e3219c, com os 92 testes anteriormente aprovados.

## Aprovação da versão 0.5 e pesquisa das etapas seguintes

Após os testes de recuperação descritos acima, o usuário informou que realizou os devidos testes e autorizou a próxima etapa. A versão 0.5 fica aprovada conforme esse relato. Não foram especificados os aparelhos nem a execução individual de cada cenário; a aprovação não substitui evidências de validação física.

A atualização seguinte contém pesquisa documental e planejamento em V0_6.md (equalizador) e V0_7.md (álbuns). Não há alteração de código, APK ou nova execução de Gradle nesta rodada. A consulta à biblioteca, o fluxo de autorização do Poweramp e os comandos de equalizador ainda precisam de teste integrado. Os resultados anteriores de compilação, 92 testes e lint continuam sendo os da implementação 0.5.

## Versão 0.6.0 — primeira parte do equalizador (0.6a)

Escopo: Ligar/Desligar as bandas do equalizador pelo relógio e ler o último evento capturado durante um pedido. Presets, edição de bandas e biblioteca não fazem parte deste APK. O guia está em [V0_6.md](V0_6.md).

Validação de código executada:

- Comando conjunto de assembleDebug, testDebugUnitTest e lintDebug nos dois módulos: BUILD SUCCESSFUL em 4m27s.
- Após reforçar a checagem de eventos inválidos, mobile:assembleDebug, mobile:testDebugUnitTest e mobile:lintDebug passaram novamente em 1m08s.
- Após compactar a apresentação do equalizador, wear:assembleDebug, wear:testDebugUnitTest e wear:lintDebug passaram novamente em 1m42s.
- Relatórios JUnit: 52 testes por módulo, total 104, sem falhas ou erros. São 12 testes novos nos dois módulos para mensagens e regras de atualização do equalizador.
- Lint: zero erros, 17 avisos no mobile e 19 no wear. Os avisos adicionais são uma sugestão de SharedPreferences.edit KTX e o corretor inglês interpretando a palavra portuguesa Comando como erro de grafia.
- Contratos v6 dos dois módulos conferidos quanto à paridade; git diff --check sem erros.

Integração executada em emulator-5554 (celular) e emulator-5556 (relógio), com o Poweramp instalado e biblioteca do usuário:

1. O comando público 53 foi explorado antes de alterar o código. equ=true mostrou as bandas e curva ativas; equ=false as desativou. O estado inicial, guardado em eq-before.xml, indicava NO DVC EQ 10 TON LMT: Equ, Tone e Limit ativos.
2. Mobile e wear 0.6.0 foram instalados preservando dados. A instalação/primeira abertura foi lenta e o relógio mostrou conexão não confirmada; após Atualizar, o Companion foi encontrado e os metadados existentes reapareceram. Não houve a tela inteiramente preta da investigação anterior.
3. A tela Equalizador foi aberta pelo botão do player. O estado inicial foi Estado ainda não informado. Ligar e Desligar responderam com Comando solicitado, sem confirmação. Os registros do Poweramp mostraram SET_EQU_ENABLED, emitido a partir do Companion.
4. Com a tela do Poweramp já aberta no celular, Ligar pelo relógio fez a identificação passar a NO DVC EQ 10 TON LMT; Desligar a fez voltar a NO DVC TON LMT. Tone continuou ativo, Bass/Treble ficaram em 0% e os valores visíveis das bandas não foram editados. Uma primeira comparação após alternar a tela do celular não mostrou a ativação; a repetição com o Poweramp já visível comprovou os dois destinos. Não tratar aquela primeira comparação como aprovação.
5. O Companion não observou EQU_CHANGED nesses pedidos, inclusive na tentativa iniciada com sua Activity visível. O relógio manteve o estado desconhecido e informou a ausência de confirmação. Isso valida o caminho de pedido sem evento; não comprova o caminho de mudança observada. Não foi inventado estado a partir do botão tocado.
6. Ligar foi enviado novamente pelo relógio, e a tela do Poweramp confirmou NO DVC EQ 10 TON LMT, restaurando o estado inicial. Tone e Limit permaneceram ativos.
7. A primeira inspeção visual mostrou o botão Voltar ocupando espaço no topo. Ele foi mantido ao fim da página e pelo gesto/botão Voltar do sistema; título/estado foram compactados para facilitar o acesso aos controles.
8. O APK final do relógio foi reinstalado e inspecionado: título, estado desconhecido e os dois botões aparecem juntos na tela circular, sem rolagem inicial. Ler último informe foi acionado; a identificação NO DVC EQ 10 TON LMT continuou no Poweramp. Voltar pelo sistema retornou à tela do player com Celular conectado e os metadados/capa existentes.
9. Após o retorno, Play foi acionado pelo relógio. A hierarquia mostrou Pause e posição estimada 0:18 / 7:50. Pause foi acionado nesse controle, e o mini player do Poweramp passou a Play, deixando a reprodução pausada. A primeira captura da hierarquia do celular durante a reprodução falhou com could not get idle state; não houve evidência de crash, e a captura seguinte após pausar funcionou. Essa verificação não substitui toda a matriz de regressão.

Capturas e hierarquias locais ficam em build/reports e /sdcard dos emuladores, fora do Git: mobile/build/reports/eq-on.png, eq-off.png; wear/build/reports/eq-watch-screen.png, eq-result.png e eq-final-screen.png. Os arquivos do sistema /sdcard/eq-verify-on.xml, eq-verify-off.xml e eq-restored.xml registram a comparação do player.

Permanecem pendentes: confirmação por evento real, leitura após um evento real capturado, Poweramp ausente, desconexão/reconexão durante pedido, interrupção da espera, matriz completa de regressão dos controles anteriores e validação física. Os testes unitários cobrem eventos antigos, futuros e ausentes; não comprovam a entrega do broadcast no Android. Os APKs usam versionCode 7/versionName 0.6.0.

## Versão 0.7.0 — biblioteca e preparação para aparelhos físicos

O usuário confirmou os testes principais da 0.6a, autorizou a 0.7 e confirmou Galaxy S23+ e Galaxy Watch 7 como aparelhos de uso. Só emulator-5554 e emulator-5556 estavam conectados ao computador. O guia de instalação física está em [INSTALL_PHYSICAL.md](INSTALL_PHYSICAL.md); os recursos e o roteiro completo estão em [V0_7.md](V0_7.md).

Validação de código executada:

- Build conjunto inicial de assembleDebug, testDebugUnitTest e lintDebug dos dois módulos: BUILD SUCCESSFUL em 4m04s, com 120 testes naquela revisão.
- Após corrigir o transporte de reprodução e a confirmação, restaurado PAGE_SIZE=12 nos dois protocolos. Build conjunto final: BUILD SUCCESSFUL em 3m06s.
- Relatórios finais: 61 testes por módulo, total 122, sem falhas/erros. A biblioteca acrescenta nove testes por módulo, incluindo limites de páginas, textos Unicode, álbuns homônimos, IDs/cursors inválidos e evidência de seleção sem exigir um novo broadcast de estado.
- Lint final: zero erros; 18 avisos no mobile e 19 no wear. Nenhuma dependência ou permissão Android foi acrescentada.
- Após o ajuste de rolagem para exibir erros no topo, assembleDebug, testDebugUnitTest e lintDebug do wear passaram novamente em 1m14s. Houve aviso de versões diferentes do XML do SDK nas ferramentas locais; o build terminou com sucesso. O APK do wear foi reinstalado e sua assinatura foi conferida novamente.
- APKs com versionCode=8 e versionName=0.7.0. apksigner verify --print-certs validou os dois e mostrou o mesmo certificado SHA-256: 775cf8cb727b990691d073c8daf47e5fb387d78468f663e730ff33305e26d3cb.
- APKs de entrega: mobile/build/outputs/apk/debug/mobile-debug.apk e wear/build/outputs/apk/debug/wear-debug.apk. São builds debug para instalação direta pessoal; não foram publicados.

Pesquisa e integração executadas no Poweramp build-1031-bundle-play:

1. A consulta inicial no Companion retornou página válida de álbuns e artistas. O acesso já estava disponível nesse ambiente; Autorizar biblioteca voltou ao Companion sem apresentar outra pergunta. Não considerar recusa/revogação de acesso como testes executados.
2. A consulta pública de diagnóstico confirmou três álbuns: Curta'n Wall - Castle Crusher! (1002), Live at Roadburn [24bit] (1001) e Unknown album (1000). O álbum 1002 tinha Introduction to the Program (3, número 1) e Wizard Party (2, número 2). O artista do álbum foi resolvido pelo provedor público album_artists.
3. A tela Biblioteca foi inspecionada no relógio: nomes e artistas legíveis, entrada no álbum sem reprodução automática e acesso às faixas. O protocolo padrão entrega até 12 entradas. Para exercitar a continuação na biblioteca pequena, foram gerados APKs temporários com PAGE_SIZE=1, usados somente nesse ensaio; não se executou a suíte de testes com esse limite temporário.
4. No ensaio de uma entrada, Próxima página levou de Curta'n Wall a Live at Roadburn e Página anterior voltou ao primeiro álbum. Nas faixas, Atualizar lista e Próxima página levaram de Introduction to the Program a Wizard Party. O limite de 12 foi restaurado, compilado, testado e reinstalado antes da verificação de entrega.
5. Reinstalar o Companion invalidou referências de continuação já guardadas no relógio; a tentativa seguinte exigiu atualizar a lista, exercitando a referência expirada. A mensagem podia ficar acima da área visível se a lista estivesse rolada até o fim. Foi acrescentado retorno ao topo ao entrar/sair do estado de erro; a variante final passou novamente por assemble, testes e lint do wear. A inspeção manual negativa desse último ajuste ainda permanece no roteiro.
6. Na primeira sessão do player, OPEN_TO_PLAY por URI foi recebido e ignorado com no pipeline nos registros do Poweramp, tanto pelo aplicativo quanto por comandos diretos de diagnóstico. playFromUri também não alterou a faixa. Reiniciar somente o processo do Poweramp por force-stop e abrir sua Activity padrão recuperou a seleção pela URI; músicas, dados e configurações foram preservados. A causa do estado anterior não foi determinada.
7. A pesquisa do MediaBrowser confirmou a raiz /, a categoria /albums e os IDs reproduzíveis relativos /albums/1002/files/3 e /albums/1002/files/2. A implementação final usa playFromMediaId e removeu as assinaturas/prints temporários de diagnóstico. Ela não abre uma Activity a partir do serviço do Companion e não depende da ferramenta content do ADB.
8. Reproduzir álbum pelo relógio iniciou Introduction to the Program, com o Poweramp em segundo plano. A confirmação inicialmente não aparecia: o broadcast de estado continuava antigo quando apenas a faixa mudava. A regra foi corrigida para combinar evento novo de faixa, estado PLAYING atual da sessão, atualização monotônica posterior ao pedido e verificação de associação ao álbum. Essa regressão ganhou teste puro nos dois módulos.
9. No ensaio de paginação, tocar Wizard Party no relógio iniciou essa faixa no celular, com sessão PLAYING e metadata correspondente. O relógio informou Reprodução observada no Poweramp. Captura: wear/build/reports/library-confirmed-small.png.
10. Nos APKs finais com PAGE_SIZE=12, o relógio abriu Live at Roadburn [24bit] e mostrou as duas faixas. Com a tela do celular apagada (dumpsys power: mWakefulness=Asleep), tocar Sub Specie Aeternitatis iniciou essa faixa, metadata de AD NAUSEAM/Live at Roadburn e sessão PLAYING. O relógio informou Reprodução observada. Captura: wear/build/reports/library-final-confirmed.png.
11. A sessão permaneceu em reprodução na conferência seguinte. Ver player retornou aos controles, e Pause foi acionado pelo relógio. A leitura final confirmou PAUSED na faixa Sub Specie Aeternitatis, posição 247044 ms (4:07). Isso também demonstra avanço posterior ao encerramento da conexão temporária do navegador. O som/rotas reais de um S23+ não foram avaliados.

Durante a investigação, houve pausas em sessões anteriores, e os horários dos registros dos emuladores saltaram de aproximadamente 02h para 10h entre ensaios. A comparação posterior por comando direto manteve PLAYING nas três leituras seguintes, espaçadas em dez segundos; a verificação final pelo aplicativo avançou até a pausa manual em 4:07. Não há evidência suficiente para atribuir as pausas anteriores ao encerramento do MediaBrowser ou estabelecer uma causa única. Não usar esse ensaio como medição de bateria ou garantia de reprodução prolongada nos aparelhos físicos.

As capturas/hierarquias ficam em build/reports e /sdcard dos emuladores, fora do Git. A tentativa de executar content por run-as foi negada pelo Android por falta de ACCESS_CONTENT_PROVIDERS_EXTERNALLY; consultas dentro do Companion funcionaram. Consultas de diagnóstico posteriores usaram a ferramenta content como shell e somente os URIs públicos, sem abrir o banco privado nem alterar a biblioteca.

### Aprovação nos aparelhos físicos — 19/10/2026

O usuário concluiu a instalação e confirmou os testes no Galaxy S23+ e no Galaxy Watch 7: “Tudo funcionando perfeitamente.” A etapa 0.7 está aprovada pelo usuário para uso pessoal. A validação física foi relatada pelo usuário, sem execução ou observação direta pelo agente.

Continuam sem confirmação individual: autorização negada/revogada, biblioteca vazia, remoção real de álbum/faixa durante uma consulta, desconexão/reconexão no meio do pedido, retorno após espera interrompida, bibliotecas grandes e alteração de tags entre páginas, rolagem rotary no relógio físico, fones/Bluetooth, bateria e reprodução prolongada. A aprovação geral não comprova cada um desses cenários.

## Versão 0.8.0 — Tile e Complication — 24/10/2026

O usuário autorizou a etapa de acesso rápido e pediu uma explicação para aprender e modificar o código futuramente. O guia [V0_8.md](V0_8.md) explica os serviços, métodos, Manifest, recursos, conceitos Kotlin/Java e exercícios de alteração. O Tile oferece Abrir player; a Complication oferece texto curto ou imagem monocromática. Ambos abrem a MainActivity sem enviar Play e sem solicitar atualização periódica.

Validação executada:

- A primeira compilação apontou Futures não disponível: Tiles fornece ListenableFuture, mas não a biblioteca Guava completa. O retorno foi ajustado para CallbackToFutureAdapter da biblioteca AndroidX Concurrent Futures 1.3.0.
- O comando conjunto assembleDebug, testDebugUnitTest e lintDebug nos dois módulos terminou com BUILD SUCCESSFUL em 4m27s.
- Foram executados os 122 testes existentes: 61 por módulo, sem falhas ou erros. Eles verificam os protocolos e regras anteriores; não exercitam a renderização nem o toque dos novos atalhos.
- Dois avisos novos WearRecents indicaram evitar NEW_TASK e CLEAR_TOP. As flags foram retiradas do Intent da Complication. Depois, assembleDebug, testDebugUnitTest e lintDebug do wear passaram novamente em 38s.
- Lint final: zero erros, 18 avisos no mobile e 19 no wear, sem aumento em relação à 0.7. Persistiu o aviso não fatal de versões diferentes do XML do SDK nas ferramentas locais.
- APKs com versionCode 9 e versionName 0.8.0. apksigner verificou ambos; o certificado SHA-256 permanece 775cf8cb727b990691d073c8daf47e5fb387d78468f663e730ff33305e26d3cb. Atualização com install -r e assinatura compatível preserva os dados.
- Nenhum dispositivo estava conectado inicialmente. Foi iniciado o AVD Wear_OS_Small_Round existente, sem apagar dados e sem salvar novo snapshot. Nesta sessão ele recebeu o serial emulator-5554, diferente da sessão da 0.7; não presumir que esse serial identifica sempre o celular.
- O APK final do wear foi instalado com Success. O Package Manager reconheceu RemoteTileService pela ação BIND_TILE_PROVIDER e RemoteComplicationService pela ação ACTION_COMPLICATION_UPDATE_REQUEST. A abertura explícita de MainActivity retornou Status: ok, com inicialização a frio. A captura wear/build/reports/qa-player.png foi inspecionada: player renderizado, sem capa/metadados e com Sem conexão confirmada, esperado sem celular conectado nesta sessão. Não houve erro AndroidRuntime no trecho consultado; isso não comprova ausência de erros em todos os cenários.

A inspeção dos editores do sistema não foi conclusiva. Foram identificados o mostrador Perfunctory, sua personalização e a área de widgets/carrossel; os editores retornavam ao mostrador entre tentativas, e gestos não avançaram consistentemente. Não foi comprovada a inclusão, a renderização ou a ação de toque do Tile/Complication pelo sistema. Essa limitação de verificação não foi atribuída a uma causa específica nem tratada como aprovação. Os serviços reconhecidos e a abertura explícita da Activity não substituem os testes dos atalhos reais.

Durante a inspeção, screen_off_timeout foi alterado temporariamente de 2147483647 para 600000 e stay_on_while_plugged_in de 0 para o modo ligado. Ambos foram restaurados, e a leitura final confirmou 2147483647 e 0. A imagem do emulador informa API 37. Não houve alteração de configuração dos aparelhos físicos, instalação no S23+/Watch 7 nesta sessão ou comando de reprodução.

Pendentes para aprovação da 0.8: adicionar o Tile e a Complication no Watch 7, conferir aparência em espaços de texto curto e ícone, tocar cada atalho com Poweramp pausado/parado e comprovar que não inicia música, repetir após sair do aplicativo, testar retorno após Biblioteca aberta, falta de conexão/reconexão e regressão dos controles/biblioteca. Verificar também o uso por Bluetooth com Wi-Fi desligado. A aprovação física anterior corresponde à 0.7, não a estes novos recursos.

### Retorno do usuário sobre a 0.8

O usuário relatou ter testado a versão 0.8 no Android Emulator e autorizou prosseguir na ordem proposta. Não detalhou cada cenário. Por preferência dele, a atualização dos aparelhos físicos fica para a 1.0. A aprovação relatada no emulador não substitui os cenários físicos acima.

## Versão 0.9.0 — presets, retomada da 0.6b — 24/10/2026

O escopo desta entrega é listar presets e selecionar explicitamente um pelo relógio. A numeração avança para 0.9.0 após a 0.8; não retorna a 0.6. Bandas e outras categorias da biblioteca permanecem para entregas seguintes. O guia [V0_9.md](V0_9.md) contém o roteiro de emulador e o material de aprendizado. A atualização física continua adiada até a 1.0 por preferência do usuário.

Validação de código:

- Comando conjunto assembleDebug, testDebugUnitTest e lintDebug dos dois módulos: BUILD SUCCESSFUL em 4m29s.
- Relatórios JUnit: 69 testes por módulo, total 138, sem falhas ou erros. Oito testes novos em cada módulo cobrem IDs, separação entre cursor e seleção, nomes Unicode/homônimos, páginas ordenadas, limites, dados truncados/adicionais, estágios inválidos e eventos antigos/futuros/com outro ID.
- Após os ajustes do histórico de paginação, a validação final assembleDebug, testDebugUnitTest e lintDebug do wear passou em 59s. O histórico retém as 64 referências anteriores mais recentes sem impedir continuar avançando.
- Lint final: zero erros, 19 avisos em cada módulo. O aviso novo do mobile é a sugestão de usar String.toUri em vez de Uri.parse. Permaneceu o aviso não fatal de XML do SDK nas ferramentas locais.
- Protocolos mobile/wear conferidos quanto à paridade. Nenhuma dependência ou permissão Android acrescentada.
- APKs versionCode=10/versionName=0.9.0. apksigner validou ambos com o mesmo certificado SHA-256 anterior: 775cf8cb727b990691d073c8daf47e5fb387d78468f663e730ff33305e26d3cb.

Pesquisa e integração executadas no Poweramp build-1031-bundle-play (versionCode 1031004):

1. Foi usado emulator-5554 como relógio e emulator-5556 como celular, conforme a identificação real do ADB nesta sessão. O celular virtual iniciou offline e não respondeu à tentativa de encerramento por console; somente o processo iniciado nesta tarefa foi encerrado e reiniciado sem carregar/salvar snapshot, sem apagar dados. Depois do boot, o Poweramp e as músicas existentes estavam presentes. A causa do estado offline não foi determinada.
2. A consulta de leitura ao provedor público eq_presets com projeção eq_presets._id, eq_presets.name e parametric retornou IDs e nomes reais. IDs 1 a 10 incluíram Bass, Classical, Flat e Rock; a continuação depois de 10 retornou Live (11) até presets paramétricos como Bass & Treble (20) e Punchy Bass (21). Não foram lidos bancos privados nem blobs de presets.
3. Antes da seleção, a interface do Poweramp mostrava o preset gráfico padrão sem nome (botão Preset), NO DVC EQ 10 TON LMT, Bass/Treble em 0% e reprodução pausada. O comando público 50 com ID long 8 fez o botão mostrar Rock, permanecendo pausado.
4. Ambos os APKs 0.9 foram instalados nos emuladores com Success. No relógio, o player mostrou Celular conectado, capa, Sub Specie Aeternitatis / AD NAUSEAM e posição 4:07 / 14:39. Equalizador > Escolher preset carregou a lista do provedor por mensagens v9, começando por Bass / Gráfico.
5. Tocar Bass exibiu Escolhido: Bass e a ação separada Aplicar escolhido. A captura da tela do celular antes de aplicar mostrava o botão Preset, não Bass; selecionar localmente não foi tratado como comando de aplicação. Não se atribuiu a ausência de Rock naquele instante a uma causa específica.
6. Aplicar escolhido pelo relógio resultou em Mudança do preset observada no Poweramp. A interface do celular mostrou Bass como preset e continuou com Play, indicando reprodução pausada. A confirmação nesta tentativa exigiu ID correspondente e evento novo; não é garantia de entrega do evento em qualquer condição de segundo plano. A captura wear/build/reports/preset-result.png foi inspecionada: mensagem e escolha legíveis na tela circular.
7. O APK final do wear foi reinstalado. Próxima página levou à página iniciada por Live; Página anterior voltou à página iniciada por Bass. A captura wear/build/reports/preset-final-page.png foi inspecionada, com título, orientação, aviso sobre o ativo e primeiro item legíveis.
8. Ao terminar, foi selecionada novamente a opção Graphic / Default Preset no seletor do Poweramp. Sua linha estava inicialmente sob o cabeçalho fixo da lista; foi preciso rolar para expô-la antes do toque. A leitura posterior confirmou botão Preset, NO DVC EQ 10 TON LMT, Bass/Treble 0% e Play. Não foi usada a opção Restore Defaults do menu nem apagado preset algum.
9. A captura mobile/build/reports/preset-restored-final.png foi inspecionada: preset padrão, preamp 0.0, bandas visíveis de 31 a 4K em 0.0, curva horizontal e reprodução pausada. Voltar duas vezes no relógio retornou de Presets ao Equalizador e ao player, com Celular conectado, capa e os mesmos metadados/posição. Essa inspeção não substitui a regressão completa de cada controle.

Capturas e hierarquias ficam em build/reports e /sdcard dos emuladores, fora do Git. A lista mostra IDs existentes e modos, mas não identifica o ativo. O último pedido exibido no relógio não é uma leitura do estado atual do equalizador.

Permanecem pendentes: seleção de preset paramétrico/AutoEq, biblioteca de presets vazia, autorização negada/revogada, remoção de preset entre listar/aplicar, ausência do Poweramp, falha/reconexão no meio do pedido, repetição/cancelamento do pedido no Android, alteração automática posterior, coleção grande com mais de 64 páginas de histórico e regressão completa dos controles/atalhos. Os testes puros de eventos sem confirmação não substituem esses cenários no Android. Validação física fica para a 1.0, conforme combinado.
