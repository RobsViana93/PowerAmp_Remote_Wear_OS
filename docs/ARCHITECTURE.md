# Organização e decisões

## Histórico: objetivo da etapa inicial

Preparar dois APKs independentes com interfaces estáticas. Cada módulo é uma parte do projeto que o Gradle compila separadamente. As pastas mobile e wear não significam que o relógio executa o código do celular.

## Arquivos e conceitos

MainActivity.kt: contém a classe MainActivity e a função IdentificationScreen. Uma classe reúne comportamento; em Java seria class MainActivity extends ComponentActivity. Kotlin usa dois-pontos para indicar herança.

Activity: tela de entrada gerenciada pelo Android. onCreate é o método chamado durante a criação. override equivale à intenção de @Override em Java; super.onCreate inicializa a classe base.

Bundle? é um tipo anulável: o ponto de interrogação permite que o parâmetro seja null. Aqui o Android pode não fornecer um estado anterior.

setContent define a interface com Compose. Compose descreve o que deve aparecer na tela. @Composable identifica uma função que participa dessa descrição. IdentificationScreen é privada porque só é usada neste arquivo. MaterialTheme fornece estilos, Text exibe texto e Column organiza elementos verticalmente. Modifier configura tamanho, espaçamento, fundo e rolagem. dp é uma medida independente da densidade dos pixels.

As telas têm fundo preto e textos centralizados. O relógio reserva margens para o formato circular e permite rolagem para fontes grandes. A validação visual no emulador ainda é necessária.

res/values/strings.xml: reúne os textos, acessados por stringResource, evitando espalhar textos pelo código. styles.xml configura o tema da janela Android antes da interface Compose.

AndroidManifest.xml: declara o aplicativo e sua Activity inicial. Somente a Activity do lançador é exportada, pois o sistema precisa abri-la. Nenhuma permissão, serviço ou receiver foi adicionado. O módulo wear exige hardware de relógio e declara que o produto depende do celular (standalone=false), embora a tela desta etapa abra sem comunicação.

build.gradle.kts: instruções de compilação escritas na linguagem Kotlin DSL. plugins adiciona ferramentas; android define SDKs e identidade; dependencies declara bibliotecas. O plugin Compose compila as funções @Composable. Não aplicamos org.jetbrains.kotlin.android, pois AGP 9 já integra Kotlin.

val define uma referência que não pode ser reatribuída, semelhante a final em Java; var permite reatribuição. Em val nome = "Poweramp", Kotlin infere String. Não precisamos de estado mutável nesta tela.

## Identidade dos aplicativos

Os dois módulos usam applicationId com.powerampremote, mas namespaces diferentes: com.powerampremote.mobile e com.powerampremote.wear. applicationId identifica o aplicativo instalado; namespace identifica classes e recursos no código. A futura Data Layer exige identidade e assinatura correspondentes. Como cada APK vai para um dispositivo diferente, essa identidade compartilhada é intencional. Na fase de comunicação, também verificaremos a assinatura.

Não instale o APK mobile no relógio: poderia substituir o aplicativo wear por ter o mesmo applicationId.

## Histórico: escopo da etapa inicial

Sem ViewModel, StateFlow, Coroutines, camadas de repositório ou serviços: não há estado dinâmico nem operações assíncronas do aplicativo nesta etapa. Esses conceitos serão ensinados quando necessários.

Próximo marco: compilar, instalar e observar ambas as telas. Só após isso e autorização explícita começará a versão 0.1, com mensagem de teste e confirmação entre dispositivos.

## Ajuste do SDK de compilação

As bibliotecas Compose resolvidas pela BOM 2026.09.00 exigem compileSdk 37. Ambos os módulos usam esse nível para compilar; targetSdk permanece 36 e minSdk permanece 26 no celular e 30 no relógio. compileSdk disponibiliza APIs durante a compilação, targetSdk declara o nível de comportamento do Android adotado pelo aplicativo e minSdk define o sistema mínimo para instalação. A versão AGP 9.1.1 suporta API 37.

## Arquitetura da versão 0.1

A versão 0.1 foi autorizada. As Activities agora exibem estado dinâmico: o relógio usa CommunicationViewModel com StateFlow e o celular observa CompanionStatus. WearMessageListener recebe testes via MessageClient e responde ao nó de origem. O serviço é exportado para Play services com um filtro de caminho específico. CapabilityClient descobre apenas Companions alcançáveis. Ainda não há Poweramp, DataClient ou Asset. Leia [V0_1.md](V0_1.md) para o contrato e a explicação de cada método.

## Arquitetura da versão 0.2

PowerampController traduz somente os quatro comandos permitidos para broadcasts explícitos ao receiver oficial do player. PowerampEventReceiver recebe eventos de estado e faixa; PowerampRepository mantém o último informe com horário e o publica via DataClient. Os pedidos e suas etapas usam MessageClient, com correlação por nó, UUID e comando. O teste v1 permanece disponível.

O estado persistido não é tratado como consulta atual. A observação de uma alteração exige um evento novo em relação ao snapshot anterior; os broadcasts do Poweramp não oferecem correlação causal com nosso UUID. O Companion não inicia o serviço de reprodução nem abre Activities automaticamente em segundo plano. Há uma alternativa manual Abrir Poweramp no celular. Veja [V0_2.md](V0_2.md) para contrato, limitações e testes.

## Arquitetura da versão 0.3

PowerampMetadataParser adapta eventos de faixa/estado ao modelo de metadados. NowPlayingProtocol mantém o codec e as regras de atualização parcial; o novo campo now_playing_v3 acompanha o snapshot v2 no mesmo DataItem. A publicação captura ambos sob o mesmo monitor. O relógio impede exibir metadados de uma faixa anterior quando o estado já aponta para outra.

O botão Atualizar solicita a consulta oficial POS_SYNC. PowerampPositionSync registra um receiver temporário com ContextCompat, espera até três segundos e confere se a referência da faixa permaneceu igual. Ao fim, remove o receiver. Não há polling, serviço permanente ou cálculo de uma posição estimada. Veja [V0_3.md](V0_3.md).

## Arquitetura da versão 0.4

NowPlayingScreen, na pasta presentation do wear, reúne a apresentação do player e o editor local da posição. Os diagnósticos anteriores continuam acessíveis em Detalhes e teste. VisualProtocol define seek em segundos e os resultados correlacionados; PowerampSeek verifica a referência da faixa, envia o comando oficial e consulta a posição posterior. A barra usa a última posição informada, sem ticker.

AlbumArtPublisher lê a imagem pelo provedor oficial da capa atual, limita leitura e resolução e publica um Asset em um DataItem separado, com ID e horário de mudança da faixa. A publicação é serializada por Mutex. O relógio limpa a capa ao mudar de faixa e confere a associação antes e depois da decodificação. Estado e imagem podem chegar em qualquer ordem; o carregamento também consulta o cache sincronizado. Veja [V0_4.md](V0_4.md).

## Arquitetura da versão 0.5

ControlsProtocol define ações explícitas de modos, leitura e volume, com resposta por UUID/nó/ação. AdditionalControls traduz os modos para a API pública, registra um receiver temporário e lê o último evento sticky. Modos não confirmados continuam classificados como solicitados. O volume é AudioManager.STREAM_MUSIC do celular e exige apenas MODIFY_AUDIO_SETTINGS. Não interfere em toque, alarme ou equalizador.

AdditionalControlsScreen apresenta os controles secundários. repeatOnLifecycle(RESUMED) gerencia consultas espaçadas e estimativa local; PowerManager.isInteractive impede consultas da rotina com a tela apagada. A estimativa começa com uma posição nova em resposta a uma consulta v5 correlacionada e confere a referência da faixa. Usa tempo monotônico local, sem comparar relógios de parede dos dois dispositivos. O cálculo é limitado a 30 segundos e ao fim da música. Não há polling permanente no celular nem mensagem a cada segundo. Veja [V0_5.md](V0_5.md).

## Continuação planejada após aprovação da 0.5

O equalizador tem pesquisa própria em [V0_6.md](V0_6.md), incluindo a limitação de entrega do evento EQU_CHANGED e a distinção entre comando solicitado e estado observado. A biblioteca de álbuns tem pesquisa e critérios em [V0_7.md](V0_7.md): o Companion obterá autorização do Poweramp, consultará o provedor público e enviará páginas limitadas de metadados ao relógio. Paginação, colunas e seleção precisam de comprovação antes da implementação da biblioteca.

## Arquitetura da versão 0.6 — parte 0.6a

EqualizerProtocol define READ/ENABLE/DISABLE, estados UNKNOWN/OFF/ON e resultados com UUID/ação. O caminho v6 e sua capability são separados dos controles anteriores. EqualizerController envia o comando público 53 com apenas equ e observa EQU_CHANGED por até dois segundos, removendo o receiver no finally. O último evento válido fica em SharedPreferences; READ lê esse cache e não consulta o player. Eventos ausentes não viram desligado nem confirmação. Não há receiver permanente, serviço em primeiro plano ou novas permissões.

WearMessageListener mantém até 32 respostas para pedidos repetidos por nó/UUID nesta instância. CommunicationViewModel espera por no máximo 12 segundos e aceita somente resposta do nó selecionado com UUID e ação correspondentes. EqualizerScreen mostra o último estado e sua data, permite selecionar destinos explícitos e informa quando a mudança não foi observada. Sair do aplicativo cancela a espera local; um comando já enviado pode terminar no celular. Presets e bandas ainda não têm controles.

## Arquitetura da versão 0.7 — biblioteca

PowerampLibrary consulta o ContentProvider público com seleção parametrizada e lim=13, entrega 12 itens e mantém a continuação por chave de ordenação e ID. As chaves ficam em um cache de até 128 tokens no celular por até dez minutos, vinculadas ao nó, tipo de lista e álbum. Não há transferência integral da biblioteca nem escrita no provedor. LibraryAccessPanel solicita a autorização por uma Activity pública do Poweramp e comprova acesso por consulta em IO ao retornar.

LibraryProtocol restringe ações e campos, limita textos/mensagens e correlaciona pedidos e respostas v7. O serviço mantém até 32 respostas para repetição de UUID nesta instância. LibraryState separa a página de álbuns da página de faixas; o ViewModel guarda até 64 referências anteriores por lista e descarta respostas de outro pedido/nó/álbum. A espera total é limitada a 35 segundos.

LibraryPlayback conecta ao serviço público de MediaBrowser, obtém a sessão e chama playFromMediaId com o identificador relativo de álbum/faixa. O celular verifica a associação entre os IDs no provedor antes de enviar. A conexão permanece aberta durante a observação e é encerrada no finally. Um evento novo de faixa, o estado atual PLAYING da sessão com atualização posterior ao pedido e a associação ao álbum permitem confirmar a seleção; não se exige reemissão do broadcast STATUS_CHANGED quando apenas a faixa muda. Falta de evidência continua sendo pedido sem confirmação.

LibraryScreen usa LazyColumn para os itens visíveis e rolagem por toque/rotary. Voltar retorna à página de álbuns sem reproduzir; Ver player retorna ao NowPlayingScreen. As consultas automáticas de posição ficam suspensas durante a navegação na Biblioteca. Os listeners e a descoberta das capabilities independentes são iniciados em paralelo. Não há serviço permanente, nova permissão Android ou nova dependência nesta etapa.

## Arquitetura da versão 0.8

A pasta quickaccess do wear contém RemoteTileService e RemoteComplicationService. O primeiro entrega um layout ProtoLayout ao Wear OS, com LaunchAction explícita para a MainActivity; o segundo entrega texto curto ou ícone monocromático ao mostrador, com PendingIntent explícito e imutável para a mesma Activity. Não enviam Play nem consultam a conexão. O player e seu ViewModel continuam responsáveis pelos comandos e pela atualização enquanto a Activity está ativa.

Os serviços são exportados e protegidos pelas permissões de vínculo BIND_TILE_PROVIDER e BIND_COMPLICATION_PROVIDER. Os tipos da Complication são SHORT_TEXT e ICON (MONOCHROMATIC_IMAGE na API Kotlin). Ambos têm atualização periódica desativada; não exibem estado dinâmico que possa ficar antigo. As bibliotecas Tiles 1.6.2, ProtoLayout/Material 1.4.2, Complications Data Source 1.3.0 e Concurrent Futures 1.3.0 ficam restritas ao módulo wear. O guia [V0_8.md](V0_8.md) explica os callbacks, recursos e modificações possíveis.

## Arquitetura da versão 0.9 — presets

PresetProtocol v9 separa LIST e APPLY dos comandos v6 de ligar/desligar. Páginas contêm até dez itens em ordem estrita de ID, nome limitado a 120 caracteres e indicador de modo paramétrico. A continuação usa afterId numérico validado e consulta parametrizada; mensagens têm limite de 5.000 bytes. Os contratos anteriores e os atalhos continuam disponíveis.

PowerampPresets consulta somente o provedor público eq_presets, com lim=11 e CancellationSignal após cinco segundos. Antes de APPLY, confere a existência do ID e envia SET_EQU_PRESET com extra id do tipo long. Um receiver temporário por até dois segundos permite OBSERVED apenas com ID correspondente e evento novo; sem evento, retorna REQUESTED. Não lê o ativo, não altera presets salvos nem mantém serviço permanente.

PresetState mantém página, escolha local, último pedido e resultado. PresetIntent separa escolher de aplicar. O ViewModel correlaciona nó/UUID/pedido, limita a espera a vinte segundos, mantém as 64 referências anteriores mais recentes e limpa o estado ao trocar de celular. As atualizações automáticas de posição são suspensas durante a tela. PresetScreen usa LazyColumn e apresenta o último pedido sem tratá-lo como preset ativo. Veja [V0_9.md](V0_9.md) para conceitos e testes.
