# Validação das etapas

**Etapa atual: versão 0.5.** Use o [roteiro de modos, volume e atualização automática](V0_5.md#teste-nos-emuladores). O usuário confirmou os testes da versão 0.4 e autorizou a próxima etapa. A matriz física da versão 0.2 continua útil para os cenários de conexão e processo ainda não documentados como executados. As seções anteriores permanecem como histórico.

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
