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
