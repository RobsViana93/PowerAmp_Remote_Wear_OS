# Preparação do ambiente Windows

## Ferramentas

Java/JDK: executa Gradle e fornece ferramentas de compilação Java. Kotlin: linguagem dos aplicativos. Gradle: organiza tarefas, baixa dependências e produz APKs. Android SDK: APIs Android, compilador de recursos, adb e emulador. Android Studio: editor e interface para essas ferramentas.

Kotlin não precisa ser instalado separadamente. Gradle global também não: o Wrapper baixa a versão fixada.

## Instalar e abrir

1. Baixe o Android Studio estável do [site oficial](https://developer.android.com/studio), usando uma versão que suporte AGP 9.1.1. Instale Android SDK e Android Virtual Device no assistente.
2. No SDK Manager, instale Android SDK Platform 36. Em SDK Tools: Android SDK Build-Tools 36.0.0, Android SDK Platform-Tools, Android SDK Command-line Tools (latest) e Android Emulator. O NDK não é necessário.
3. Anote o Android SDK Location exibido. Se o caminho for diferente do exemplo, use o seu caminho.
4. Em File > Open, abra C:\Users\rober\Poweramp_Remote. Já criamos o projeto; não é necessário executar o assistente New Project.
5. Em Settings > Build, Execution, Deployment > Build Tools > Gradle, use a distribuição do Wrapper e um Gradle JDK compatível (o JDK 21 instalado, ou JDK 17/21 fornecido/configurado no Studio).
6. Aceite as licenças solicitadas pelo SDK Manager e execute Sync Project with Gradle Files. O primeiro download exige internet.
7. Se o Studio não criar local.properties, copie local.properties.example para local.properties e ajuste sdk.dir. Use barras / no caminho.

Pelo assistente, a alternativa seria criar Empty Activity (Compose/Kotlin), nomear o módulo mobile, e depois File > New > New Module > Wear OS > Empty Wear App, nomeando wear. Essa alternativa é explicativa; não a execute sobre este projeto, pois geraria arquivos duplicados.

## Verificar no terminal PowerShell

Execute na pasta do projeto:

~~~powershell
java -version
javac -version
git --version
.\gradlew.bat --version
~~~

O resultado esperado do Wrapper é Gradle 9.3.1. JAVA_HOME não é obrigatório se java está no PATH, mas o Gradle do terminal e do Android Studio devem usar JDKs compatíveis.

Para verificar o SDK (ajuste o caminho):

~~~powershell
$sdkPath = 'C:\Users\rober\AppData\Local\Android\Sdk'
Test-Path "$sdkPath\platforms\android-36\android.jar"
Test-Path "$sdkPath\build-tools\36.0.0"
& "$sdkPath\platform-tools\adb.exe" version
~~~

## Controle de versão

O repositório Git local foi inicializado. O .gitignore exclui caminhos locais, caches e APKs. Revise git status antes de salvar mudanças. Para registrar esta etapa, depois de revisar:

~~~powershell
git add .
git commit -m "Estrutura inicial mobile e wear"
~~~

## Problemas comuns

- SDK location not found: confira local.properties e o SDK Location no Studio.
- Licenses not accepted: abra SDK Manager e aceite as licenças dos pacotes necessários.
- Plugin/dependency could not be resolved: confira internet, proxy e modo Offline do Gradle.
- Unsupported class file / Java incompatível: confira o Gradle JDK no Studio e java -version no terminal.
- Falta de memória: feche emuladores extras; org.gradle.jvmargs está configurado com 2 GB.
- Não aparece wear na seleção de execução: sincronize e crie uma configuração Android App com módulo wear.
