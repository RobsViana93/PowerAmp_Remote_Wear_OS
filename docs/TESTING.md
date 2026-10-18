# Validação das etapas

**Etapa atual: versão 0.1.** Use o [roteiro de comunicação](V0_1.md#teste-no-android-studio), incluindo pareamento e confirmação de ida e volta. As seções da etapa inicial abaixo são histórico.

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
