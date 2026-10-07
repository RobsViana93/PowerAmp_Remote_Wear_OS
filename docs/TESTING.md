# Validação da etapa inicial

## Estado

Java foi executado e retornou Temurin 21.0.12.1. O comando gradlew.bat --version foi executado com sucesso: Gradle 9.3.1, Launcher JVM 21.0.12.1. O Kotlin 2.2.21 exibido nesse comando é o Kotlin interno do Gradle, não a versão do compilador do aplicativo. SDK e Studio não foram encontrados nos locais inspecionados. As interfaces ainda não foram executadas. Sem teste de comunicação ou Poweramp nesta etapa.

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
3. Baixe uma imagem estável Wear OS oferecida para esse perfil (API 30 ou superior). O SDK de compilação 36 não exige que o emulador rode API 36.
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
