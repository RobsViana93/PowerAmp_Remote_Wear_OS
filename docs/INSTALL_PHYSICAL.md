# Instalar e usar no Galaxy S23+ e Galaxy Watch 7

O build atual é 1.0.0, preparado para a atualização pessoal no Galaxy S23+ e Watch 7. Instale o Companion no celular e o Remote no relógio. O Poweramp e as músicas ficam no celular. Não é necessário publicar o projeto nem fazer login na Play Store para instalar estes APKs pelo ADB. O [guia da 1.0](V1_0.md) contém o roteiro de aprovação; [V0_8.md](V0_8.md) mostra como adicionar o Tile e a Complication, e [V0_9.md](V0_9.md) explica os presets.

O usuário aprovou a 0.7 no Galaxy S23+ e Watch 7 e relatou teste da 0.8 no emulador. A atualização física foi adiada até esta etapa. Os testes executados estão em [TESTING.md](TESTING.md); a aprovação física da 1.0 continua dependente do roteiro no seu par de aparelhos.

Se estiver usando o ZIP de entrega, extraia-o inteiro e leia LEIA-ME.txt. Os APKs já estão compilados; comece em Conectar o S23+, executando os comandos na pasta extraída. Os caminhos dos APKs são iguais aos do projeto. Não é necessário executar Gradle no pacote.

## Preparar os APKs

No terminal PowerShell, dentro da pasta Poweramp_Remote:

```powershell
.\gradlew.bat :mobile:assembleDebug :wear:assembleDebug
$adbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adbPath devices -l
```

Os arquivos são:

- Celular: mobile/build/outputs/apk/debug/mobile-debug.apk.
- Relógio: wear/build/outputs/apk/debug/wear-debug.apk.

Ambos usam o mesmo applicationId e devem ser compilados com a mesma chave de assinatura. O build debug deste projeto já atende a isso neste computador. Para futuras atualizações, use a mesma instalação/chave; install -r preserva os dados quando a assinatura é compatível.

## Conectar o S23+

Ative as opções do desenvolvedor no celular, tocando sete vezes no Número da versão em Configurações > Sobre o telefone > Informações do software. Ative Depuração USB, conecte ao computador com cabo de dados e aceite a autorização mostrada pelo Android. Confira o S23+ na saída de adb devices -l; o estado deve ser device. Esses passos seguem a [orientação do Android para aparelhos físicos](https://developer.android.com/studio/run/device).

Copie o serial do celular, sem escolher emulator-5554 ou emulator-5556:

Read-Host mostra uma pergunta e aguarda a resposta na linha seguinte. Primeiro execute a atribuição abaixo; depois digite somente o serial quando solicitado e pressione Enter. Uma alternativa é atribuir diretamente, por exemplo: $phoneSerial = 'SERIAL_DO_SEU_CELULAR', substituindo o texto pelo serial real. As variáveis valem somente no terminal atual; em um terminal novo, defina $adbPath novamente.

```powershell
$adbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adbPath devices -l
$phoneSerial = Read-Host 'Cole o serial do S23+ mostrado pelo adb devices -l'
& $adbPath -s $phoneSerial install -r .\mobile\build\outputs\apk\debug\mobile-debug.apk
```

Espere a mensagem Success. Alternativamente, no Android Studio, selecione o módulo mobile, escolha o S23+ na lista de dispositivos e use Run.

## Conectar o Watch 7 por Wi-Fi

Deixe o relógio e o computador na mesma rede Wi-Fi. No relógio, abra Configurações > Sobre o relógio > Informações do software e toque repetidamente em Versão do software para habilitar as opções do desenvolvedor; a orientação da Samsung indica cinco toques. Depois ative Depuração ADB e Depuração sem fio nas opções do desenvolvedor. Os nomes podem variar com a versão da One UI. Consulte o [guia da Samsung](https://developer.samsung.com/health/sensor/guide/connect-watch.html).

Em Depuração sem fio, escolha Parear novo dispositivo. No terminal, informe o IP:porta mostrado nessa tela:

A tela do relógio mostra dois dados diferentes: endereço IP:porta (por exemplo, 192.168.18.230:36311) e código de pareamento de seis dígitos. O endereço usa dois-pontos antes da porta; não use 192.168.18.230.36311. O exemplo não é fixo: copie os valores atuais do seu relógio. Mantenha a tela de pareamento aberta durante o procedimento.

```powershell
$adbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$watchPairAddress = Read-Host 'IP:porta da tela Parear novo dispositivo do Watch 7'
& $adbPath pair $watchPairAddress
```

Na pergunta de Read-Host, informe somente o IP:porta, nunca o código de seis dígitos. Depois execute adb pair. Somente quando aparecer Enter pairing code, digite o código exibido no relógio. Volte à tela principal de Depuração sem fio e copie o IP:porta de conexão. A porta de conexão costuma ser diferente da porta de pareamento, conforme o [guia oficial do Wear OS](https://developer.android.com/training/wearables/get-started/debug-wifi).

```powershell
$watchSerial = Read-Host 'IP:porta de conexão da tela principal de Depuração sem fio'
& $adbPath connect $watchSerial
& $adbPath devices -l
& $adbPath -s $watchSerial install -r .\wear\build\outputs\apk\debug\wear-debug.apk
```

Espere Success. O Watch 7 conectado também pode ser escolhido no Android Studio para executar o módulo wear. O pareamento ADB com o computador serve para instalar/testar; o relógio deve continuar pareado normalmente com o S23+ pelo Galaxy Wearable para a comunicação do aplicativo.

## Primeiro uso da biblioteca real

1. No S23+, abra o Poweramp e confira se seus álbuns já aparecem na biblioteca dele.
2. Abra Poweramp Remote Companion. Role até Biblioteca de álbuns, toque Autorizar biblioteca e permita o acesso se houver um pedido. Retorne ao Companion e toque Verificar acesso. Deve aparecer Acesso à biblioteca disponível.
3. Faça um ciclo Play/Pause no Poweramp após instalar, para ativar novamente os eventos usados pelo Remote.
4. No Watch 7, abra Poweramp Remote. Se necessário, toque Atualizar no player e aguarde Celular conectado. Role até Biblioteca.
5. Escolha um álbum e toque Reproduzir álbum ou uma faixa. O som deve sair pela rota de áudio atual do S23+, inclusive seus fones se estiverem conectados. Ver player retorna aos controles de reprodução.
6. Confira álbuns, faixas e controles básicos antes do uso habitual. O roteiro completo está em [V0_7.md](V0_7.md).

Depois da instalação, o aplicativo continua instalado e funciona sem o computador. Pode desconectar o cabo e desligar a depuração sem fio/ADB no relógio. Isso encerra a conexão de desenvolvimento, não o pareamento normal entre relógio e celular. Continue com os dois aplicativos instalados e os aparelhos conectados entre si.

## Se algo não funcionar

- **unauthorized no ADB:** desbloqueie o aparelho e aceite a autorização de depuração.
- **Falha em adb connect:** confira se usou a porta de conexão, se ela mudou ao reativar a depuração e se a rede permite comunicação entre computador e relógio.
- **INSTALL_FAILED_UPDATE_INCOMPATIBLE:** o Remote anterior foi assinado por outra chave. Prefira recompilar com a mesma chave. Caso decida reinstalar, remova somente o Poweramp Remote/Companion correspondente e autorize a biblioteca novamente; isso apaga os dados locais do Remote. Preserve o Poweramp e sua biblioteca.
- **Companion precisa ser atualizado:** confira se instalou mobile e wear do mesmo build; o build atual é 1.0.0.
- **protocol fault ao parear:** confira o conteúdo de $watchPairAddress. Deve ser um IP:porta, não o código de seis dígitos. Abra Parear novo dispositivo novamente e use o endereço e código atuais da mesma tela.
- **cannot resolve host:** confira o separador antes da porta. Use IP:porta com dois-pontos e sem espaços.
- **Sem acesso à biblioteca:** abra o Companion no S23+, use Autorizar biblioteca e Verificar acesso; depois Atualizar lista no relógio. Não se trata de permissão para copiar músicas ao relógio.
- **Lista antiga depois de mudar músicas/tags:** atualize a biblioteca no Poweramp e use Atualizar lista no Remote.
- **Capa indisponível:** use Atualizar no player do relógio. No teste da 1.0, essa ação recuperou a imagem após uma ausência transitória; arquivos sem capa podem continuar sem imagem.
- **Sem conexão no Remote:** confira o pareamento no Galaxy Wearable, mantenha os dois aparelhos próximos, abra o Companion e use Atualizar no relógio. A lista não funciona offline nesta versão.

Não foram configuradas exceções de bateria no celular nesta etapa. Caso surja uma falha específica com a tela desligada, registre o cenário e a mensagem antes de alterar configurações do sistema.
