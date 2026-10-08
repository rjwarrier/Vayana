<div align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="core/resources/src/main/res/drawable-nodpi/vayana_share_dark_reader.webp">
    <source media="(prefers-color-scheme: light)" srcset="core/resources/src/main/res/drawable-nodpi/vayana_share_light.webp">
    <img alt="Vayana: um leitor de livros digitais para Android. Leia sem interrupções." src="core/resources/src/main/res/drawable-nodpi/vayana_share_light.webp" width="820">
  </picture>

  <h1>Vayana</h1>

  <p><strong>Leia sem interrupções.</strong></p>
  <p>Um leitor de <a href="docs/USER_GUIDE.pt.md#reading-epub-books">EPUB</a> e <a href="docs/USER_GUIDE.pt.md#reading-pdf-books">PDF</a> para Android, tranquilo e com prioridade ao armazenamento local, feito para telas convencionais e <a href="docs/USER_GUIDE.pt.md#e-ink-and-accessibility">dispositivos E‑Ink</a>.</p>

  <p>
    <a href="https://github.com/rjwarrier/Vayana/releases/latest"><strong>Baixar a versão mais recente</strong></a> ·
    <a href="https://ranjithj.in/vayana/"><strong>Site do Vayana</strong></a> ·
    <a href="https://github.com/rjwarrier/Vayana/releases/tag/v0.87">Notas da versão</a> ·
    <a href="docs/USER_GUIDE.pt.md">Guia do usuário</a> ·
    <a href="docs/FEATURES.md">Documentação de recursos</a> ·
    <a href="docs/GITHUB_SYNC_SETUP.md">Configurar a sincronização com o GitHub</a>
  </p>

  <p>
    <img alt="Versão v0.87" src="https://img.shields.io/badge/release-v0.87-00695c?style=flat-square">
    <img alt="Android 8.0 ou mais recente" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white">
    <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.3-7F52FF?style=flat-square&logo=kotlin&logoColor=white">
    <img alt="Material 3" src="https://img.shields.io/badge/Material-3-6750A4?style=flat-square&logo=materialdesign&logoColor=white">
  </p>

  <p><a href="https://www.buymeacoffee.com/ranjithj"><img alt="Pague-me um café" src="https://img.buymeacoffee.com/button-api/?text=Buy%20me%20a%20coffee&emoji=&slug=ranjithj&button_colour=FFDD00&font_colour=000000&font_family=Bree&outline_colour=000000&coffee_colour=ffffff" height="44"></a></p>
</div>

[English](README.md) · [Español](README.es.md) · **Português (Brasil)** · [Français](README.fr.md) · [Deutsch](README.de.md)

<a id="why-vayana"></a>

## Por que Vayana?

A maioria dos aplicativos de leitura se limita a exibir um livro. O Vayana trata a leitura como uma prática conectada: organize o que deseja ler, mantenha o foco no texto, guarde passagens importantes, aprenda palavras novas, revisite o que importa e leve seu progresso com segurança entre dispositivos.

O Vayana faz isso sem exigir uma conta Vayana ou exibir anúncios durante a leitura. Sua biblioteca é local por padrão. Os recursos opcionais on-line, como o [enriquecimento com Goodreads](docs/USER_GUIDE.pt.md#book-details-and-reading-status) e a [sincronização com o GitHub gerenciada por você](docs/USER_GUIDE.pt.md#github-sync), permanecem sob seu controle.

<a id="latest-source-updates"></a>

## Atualizações mais recentes do código-fonte

O código atual inclui um [aplicativo complementar para Wear OS](docs/WEAR_OS.md) para leitura de livros físicos: cronômetros off-line, acompanhamento de páginas, controles compartilhados entre celular e relógio, metas de leitura, um bloco e uma complicação para o mostrador. Correções recentes de sincronização preservam o tempo de leitura entre dispositivos e tratam com segurança os recuos do relógio do sistema. Veja as [mudanças desde a v0.87](docs/releases/UNRELEASED.md).

A versão publicada **v0.87** contém atualmente apenas o APK para celular. Compile os aplicativos atuais de celular e relógio para usar os recursos complementares; veja a [configuração do Wear OS](docs/WEAR_OS.md#install-and-build). O aplicativo do relógio registra sessões de leitura de livros físicos; ele não exibe livros EPUB ou PDF.

<a id="highlights-in-087"></a>

## Destaques da versão 0.87

- Leia livros [**EPUB**](docs/USER_GUIDE.pt.md#reading-epub-books) e [**PDF**](docs/USER_GUIDE.pt.md#reading-pdf-books), com sumários PDF, seleção de texto, anotações, consulta ao dicionário, zoom e controles de exibição por livro.
- Encontre e baixe livros em domínio público pelo [**Project Gutenberg**](docs/USER_GUIDE.pt.md#download-free-books-from-project-gutenberg) ou conecte [**catálogos OPDS**](docs/USER_GUIDE.pt.md#connect-an-opds-catalog), como Calibre, Calibre-Web e Standard Ebooks.
- Mantenha uma única biblioteca para livros digitais, [sem arquivo digital, físicos e emprestados](docs/USER_GUIDE.pt.md#offline-physical-borrowed-and-other-books), com lembretes de devolução e um espelhamento opcional, somente leitura, do [**Home Library**](docs/USER_GUIDE.pt.md#home-library-mirror).
- [Consulte e traduza palavras ou frases](docs/USER_GUIDE.pt.md#dictionary-translation-and-vocabulary) no idioma do livro usando o dicionário off-line, Wiktionary, Wikipedia ou ferramentas de tradução.
- Retome a leitura, controle a leitura em voz alta e confira o tempo de leitura de sete dias em [**widgets e atalhos da tela inicial**](docs/USER_GUIDE.pt.md#widgets-and-app-shortcuts) configuráveis.
- Registre falhas do aplicativo e de sincronização na tela [**Diagnóstico**](docs/USER_GUIDE.pt.md#diagnostics-and-support), projetada com atenção à privacidade, e compartilhe um relatório com dados sensíveis ocultados com o desenvolvedor.
- Use o Vayana em [inglês, espanhol, português, russo, alemão, francês, italiano, malaiala ou tâmil](docs/USER_GUIDE.pt.md#settings-and-languages).

<a id="screenshots"></a>

## Capturas de tela

<table>
  <tr>
    <td width="50%" align="center">
      <img src="docs/assets/screenshots/01-notes-and-highlights.png" alt="Notas e destaques de livros com filtros, busca e anotações da comunidade" width="100%"><br>
      <sub><a href="docs/USER_GUIDE.pt.md#highlights-notes-bookmarks-and-quotes"><strong>Destaques e notas</strong></a>: pesquise, filtre, edite, compartilhe e diferencie anotações pessoais de citações da comunidade.</sub>
    </td>
    <td width="50%" align="center">
      <img src="docs/assets/screenshots/02-notes-library.png" alt="Biblioteca de notas agrupadas por livro" width="100%"><br>
      <sub><a href="docs/USER_GUIDE.pt.md#notes-library"><strong>Biblioteca de notas</strong></a>: explore anotações por livro e veja rapidamente as contagens de capítulos e notas.</sub>
    </td>
  </tr>
  <tr>
    <td width="50%" align="center">
      <img src="docs/assets/screenshots/03-book-library.png" alt="Biblioteca do Vayana com cartão de leitura atual e grade de capas" width="100%"><br>
      <sub><a href="docs/USER_GUIDE.pt.md#managing-the-library"><strong>Biblioteca</strong></a>: continue lendo, pesquise e filtre uma coleção organizada pelas capas.</sub>
    </td>
    <td width="50%" align="center">
      <img src="docs/assets/screenshots/04-book-details.png" alt="Detalhes do livro com metadados do Goodreads, progresso e estatísticas de leitura" width="100%"><br>
      <sub><a href="docs/USER_GUIDE.pt.md#book-details-and-reading-status"><strong>Detalhes do livro</strong></a>: metadados, citações da comunidade, avaliação pessoal, progresso e estatísticas de leitura em um só lugar.</sub>
    </td>
  </tr>
</table>

<a id="what-makes-it-different"></a>

## O que o torna diferente

<a id="a-real-eink-mode"></a>

### [Um verdadeiro modo E‑Ink](docs/USER_GUIDE.pt.md#e-ink-and-accessibility)

O E‑Ink é um perfil de exibição próprio, e não apenas um filtro de cor:

- O movimento contínuo é removido; a linha ondulada de progresso da leitura fica estática.
- A animação de troca de página é desativada, e o relógio do leitor só é atualizado ao mudar de página.
- Escolha **Monocromático** ou **Colorido** em **Configurações → Aparência → Paleta E-ink**, ou durante a configuração inicial. Monocromático continua sendo o padrão: as capas usam tons de cinza de alto contraste e os destaques se tornam marcas pretas diferenciadas pela forma. Colorido preserva capas, ilustrações, destaques e cores de realce do tema em painéis E-Ink coloridos.
- As duas paletas mantêm o comportamento de movimento, paginação e atualização do E-Ink; o suporte a cores não reativa as animações.
- O E-Ink colorido usa uma paleta específica de alto contraste: superfícies neutras, texto preto/branco, contornos sólidos e realces em verde-azulado, azul e vinho. Os modos claro e escuro são compatíveis; as cores do papel de parede e as variantes de superfície OLED são ignoradas nesse perfil. As capas e os temas de página escolhidos são preservados. Verificações digitais de contraste não substituem testes no painel com sua iluminação frontal e configurações de atualização.
- As teclas físicas de página mudam as páginas do leitor e permitem avançar pelas telas longas do aplicativo.
- Biblioteca, Notas, Busca, Configurações, Estantes e Estatísticas recebem controles para avançar uma tela por vez.
- Atualizações de limpeza do leitor podem ocorrer a cada quantidade escolhida de páginas e nas transições de capítulos ou menus.
- Os controles tipográficos incluem texto mais espesso, alinhamento e hifenização.

<a id="reading-that-becomes-learning"></a>

### [Leitura que se transforma em aprendizado](docs/USER_GUIDE.pt.md#dictionary-translation-and-vocabulary)

- Toque duas vezes em uma palavra para abrir o dicionário off-line incluído; as palavras ausentes podem ser consultadas na Wiktionary ou na Wikipedia com um toque.
- Salve as palavras consultadas diretamente em um conjunto de revisão espaçada.
- Descubra palavras incomuns do capítulo atual com suas definições.
- Exporte o vocabulário para CSV compatível com Anki ou Markdown legível.
- Revise seus destaques em intervalos espaçados: **Ver em breve**, **Entendi** ou **Conheço bem**.
- Ao voltar depois de um tempo, veja um resumo com um destaque recente e vocabulário pendente de revisão.

<a id="read-aloud-that-follows-the-book"></a>

### [Leitura em voz alta que acompanha o livro](docs/USER_GUIDE.pt.md#read-aloud)

- A síntese de voz do Android lê frase por frase e avança entre capítulos.
- As palavras ou frases faladas são marcadas diretamente no texto.
- Inicie a reprodução a partir de um trecho selecionado; toque em qualquer lugar do leitor para pausar.
- Escolha o mecanismo TTS instalado, a voz, a velocidade e o tom.
- Use um temporizador de desligamento, gerenciamento de foco de áudio, controles de fone/Bluetooth, controles na tela de bloqueio e ações de notificação.

<a id="sync-without-a-proprietary-service"></a>

### [Sincronização sem um serviço proprietário](docs/USER_GUIDE.pt.md#github-sync)

Use um repositório GitHub sob seu controle para sincronizar:

- Posição de leitura, sessões e datas de leitura
- Livros, capas e metadados da biblioteca
- Destaques, notas, estantes e Próximas leituras
- Vocabulário e configurações portáveis
- Exclusões e restaurações entre dispositivos

Os arquivos de livros e capas são criptografados com AES-GCM usando sua frase secreta antes do envio. A sincronização leve de progresso pode ocorrer durante a leitura, e as mensagens de conflito identificam a posição mais recente com o horário da sincronização e o nome do dispositivo. A própria configuração de sincronização pode ser exportada como um arquivo de transferência criptografado para outro dispositivo.

Siga o [guia passo a passo de sincronização com o GitHub](docs/GITHUB_SYNC_SETUP.md) para criar um repositório privado, configurar um token com os menores privilégios necessários, conectar o primeiro dispositivo e adicionar outros com segurança.

<a id="features"></a>

## Recursos

| Área | Destaques |
| --- | --- |
| [**Biblioteca**](docs/USER_GUIDE.pt.md#managing-the-library) | Importação EPUB/PDF, busca em pastas, **Abrir com** do Android, detecção de duplicatas, visualizações em grade/lista, filtros, ordenação, leitura atual, Próximas leituras, estantes, pastas de séries e Excluídos recentemente |
| [**Livros sem arquivo digital e físicos**](docs/USER_GUIDE.pt.md#offline-physical-borrowed-and-other-books) | Registro de livros sem arquivo local, progresso e cronômetros, status próprio/emprestado, datas de devolução e lembretes; espelhamento opcional do catálogo compartilhado pelo Home Library |
| [**Detalhes do livro**](docs/USER_GUIDE.pt.md#book-details-and-reading-status) | Metadados, séries e tags editáveis, avaliações, datas de leitura, tempo gasto, capas personalizadas, substituição do arquivo de origem, compartilhamento de arquivos e cartões visuais de leitura |
| [**Goodreads**](docs/USER_GUIDE.pt.md#book-details-and-reading-status) | Prévia e importação de detalhes de séries, gêneros, descrição, capa, ano de publicação, avaliação e citações populares; alternativa pelo navegador quando a consulta direta é bloqueada |
| [**Descoberta de livros**](docs/USER_GUIDE.pt.md#adding-books) | Explore e baixe livros do Project Gutenberg por tema ou idioma e conecte catálogos OPDS, incluindo Calibre, Calibre-Web e Standard Ebooks |
| [**Leitor EPUB**](docs/USER_GUIDE.pt.md#reading-epub-books) | Navegação pelo sumário, posição salva, preferências por livro, fontes importadas, temas, margens, cabeçalhos/rodapés, estilos da editora, zonas de toque, teclas de volume, tela cheia e duas colunas na horizontal |
| [**Leitor PDF**](docs/USER_GUIDE.pt.md#reading-pdf-books) | Navegação por páginas e sumário, ajuste/zoom, seleção de texto, dicionário, destaques, sublinhados, notas, marcadores, cópia e cartões de citações em PDFs com texto |
| [**Tipografia**](docs/USER_GUIDE.pt.md#typography-and-page-appearance) | Família e tamanho da fonte, altura de linha, fontes personalizadas, alinhamento, hifenização, texto mais espesso e leitura biônica opcional |
| [**Anotações**](docs/USER_GUIDE.pt.md#highlights-notes-bookmarks-and-quotes) | Destaques, sublinhados, marcadores, notas, tags de anotações, mesclagem de destaques sobrepostos, pop-ups de notas de rodapé e retorno direto a um trecho |
| [**Notas e citações**](docs/USER_GUIDE.pt.md#notes-library) | Visualizações de Notas globais e por livro, importação de `My Clippings.txt` do Kindle, exportação Markdown, importação de citações do Goodreads e imagens de cartões de citações para compartilhar |
| [**Dicionário e tradução**](docs/USER_GUIDE.pt.md#dictionary-translation-and-vocabulary) | Consulta off-line conforme o idioma do livro, busca de frases, alternativas Wiktionary/Wikipedia, tradução, vocabulário salvo, palavras do capítulo, revisão espaçada, registro de palavras conhecidas e exportação Anki/Markdown |
| [**Leitura em voz alta**](docs/USER_GUIDE.pt.md#read-aloud) | TTS do Android que acompanha frases, avança capítulos, começa pela seleção e oferece temporizador, controles de voz/velocidade/tom, foco de áudio, fone/Bluetooth e notificações |
| [**Busca**](docs/USER_GUIDE.pt.md#search) | Busca rápida de texto completo em livros, metadados, texto destacado, notas e nomes de capítulos, com correspondência por prefixo e pesquisas recentes |
| [**Estatísticas**](docs/USER_GUIDE.pt.md#statistics-and-goals) | Tempo e sessões de leitura, sequências de dias, metas diárias/anuais, livros concluídos, atividade por data, histórico por livro e armazenamento usado (livros, capas, notas e citações, dicionário, cache; maior/menor livro e por formato) |
| [**Widgets e atalhos**](docs/USER_GUIDE.pt.md#widgets-and-app-shortcuts) | Continuar lendo com reprodução/pausa da leitura em voz alta, gráfico adaptável de sete dias de tempo de leitura e atalhos para os principais destinos da biblioteca |
| [**Backup**](docs/USER_GUIDE.pt.md#backup-and-restore) | Backup/restauração manual em ZIP portável e backups automáticos em uma pasta escolhida, com frequência diária, semanal ou a cada 30 dias e controles de retenção |
| [**Diagnóstico**](docs/USER_GUIDE.pt.md#diagnostics-and-support) | Histórico local de falhas e sincronização, detalhes do ambiente, ocultação de dados sensíveis, ações de copiar/compartilhar e acesso ao suporte do desenvolvedor com um toque |
| [**Idiomas**](docs/USER_GUIDE.pt.md#settings-and-languages) | Seleção de idioma no aplicativo: inglês, espanhol, português, russo, alemão, francês, italiano, malaiala e tâmil |
| [**Aparência**](docs/USER_GUIDE.pt.md#settings-and-languages) | Material 3, modos sistema/claro/escuro, preto puro, cores Material You opcionais, perfis Padrão/E‑Ink, controles de movimento e navegação adaptável a celulares/tablets |

O Vayana também permite [registrar livros físicos e emprestados](docs/USER_GUIDE.pt.md#offline-physical-borrowed-and-other-books) sem arquivo digital, incluindo progresso, datas, avaliações, notas e estatísticas.

<a id="install"></a>

## Instalação

O Vayana é compatível com **Android 8.0 (API 26) ou mais recente**.

Para conhecer o primeiro uso, veja o [guia de início rápido](docs/USER_GUIDE.pt.md#quick-start).

1. Abra a [versão mais recente no GitHub](https://github.com/rjwarrier/Vayana/releases/latest).
2. Baixe `Vayana-v0.87.apk`.
3. Se o Android solicitar, permita a instalação pelo navegador ou gerenciador de arquivos e abra o APK.

O Android pode avisar que o aplicativo veio de fora do Google Play. Os arquivos da versão incluem um arquivo `.sha256` para verificar o download antes da instalação.

Para a v0.87:

```text
SHA-256 do APK
783642D1B39F7941CE0C0A97EACB31CFE3163D50504051012F6E84D5EADEA909

SHA-256 do certificado da versão
53:2C:F4:07:D5:F0:D1:21:58:8A:5C:F1:6E:61:12:C8:F1:BB:3B:7E:D9:CF:F1:39:80:6A:7B:63:C6:43:96:C7
```

<a id="current-scope"></a>

## Escopo atual

- **Formatos de livros digitais legíveis:** [EPUB](docs/USER_GUIDE.pt.md#reading-epub-books) e [PDF](docs/USER_GUIDE.pt.md#reading-pdf-books). PDFs digitalizados sem camada de texto permitem visualização, zoom e marcadores, mas não seleção de texto. [Livros físicos](docs/USER_GUIDE.pt.md#offline-physical-borrowed-and-other-books) podem ser registrados sem um arquivo.
- **Acesso on-line:** Leitura básica, notas, dicionário e estatísticas funcionam localmente. [Project Gutenberg](docs/USER_GUIDE.pt.md#download-free-books-from-project-gutenberg), [OPDS](docs/USER_GUIDE.pt.md#connect-an-opds-catalog), Goodreads, busca de capas, tradução, consultas à Wiktionary/Wikipedia e [sincronização com o GitHub](docs/USER_GUIDE.pt.md#github-sync) precisam de Internet quando usados; o texto selecionado só é enviado ao provedor quando você escolhe essa ação. Veja o [guia de privacidade e serviços on-line](docs/USER_GUIDE.pt.md#privacy-and-online-services).
- **Atualização E‑Ink:** O [comportamento E‑Ink portável](docs/USER_GUIDE.pt.md#e-ink-display-profile) está implementado. Modos de atualização específicos de fabricantes, como os SDKs Onyx/Boox, ainda não estão integrados.
- **Histórico na nuvem:** Arquivos criptografados excluídos permanentemente podem permanecer em commits anteriores do repositório de sincronização, pois o histórico do Git é imutável, a menos que seja reescrito. Veja as [limitações atuais](docs/USER_GUIDE.pt.md#current-limitations).

<a id="build-from-source"></a>

## Compilar a partir do código-fonte

<a id="requirements"></a>

### Requisitos

- JDK 17 ou mais recente
- Android SDK 37
- Git

Clone o repositório e compile um APK de depuração com o wrapper do Gradle incluído:

```bash
git clone https://github.com/rjwarrier/Vayana.git
cd Vayana
./gradlew :app:assembleDebug
```

No Windows:

```powershell
.\gradlew.bat :app:assembleDebug
```

O APK de depuração é gerado em `app/build/outputs/apk/debug/`.

Para compilar também o aplicativo do relógio, execute `./gradlew :app:assembleDebug :wear:assembleDebug`
(ou `.\gradlew.bat :app:assembleDebug :wear:assembleDebug` no Windows).
Instale `wear/build/outputs/apk/debug/wear-debug.apk` no relógio. Os dois aplicativos devem
usar o mesmo certificado de assinatura; veja [instalação e verificação](docs/WEAR_OS.md#install-and-build).

<a id="release-signing"></a>

### Assinatura de versões

As credenciais de publicação ficam fora do Git. Configure a compilação para usar um arquivo local de propriedades Java por meio de `VAYANA_KEYSTORE_PROPERTIES`, como propriedade do Gradle ou variável de ambiente:

```properties
storeFile=/absolute/path/to/release-keystore
storePassword=...
keyAlias=...
keyPassword=...
storeType=PKCS12
```

```bash
./gradlew :app:assembleRelease \
  -PVAYANA_KEYSTORE_PROPERTIES=/absolute/path/to/keystore.properties
```

Sem um caminho externo, a compilação procura um arquivo `keystore.properties` ignorado pelo Git na raiz do repositório.

<a id="project-structure"></a>

## Estrutura do projeto

```text
app/                    Estrutura do aplicativo do celular e navegação
wear/                   Aplicativo complementar Wear OS para leitura física
core/wear/              Cronômetro, sessões e protocolo Data Layer compartilhados
core/                   Banco de dados, configurações, arquivos, backup, sincronização, diagnóstico, integração Home Library e sistema de design
feature/                Biblioteca, leitor, descoberta, notas, lembretes, busca, estatísticas, configurações e configuração inicial
reader/engine-api/      Contrato do mecanismo de leitura
reader/engine-web/      Mecanismo EPUB baseado no Foliate e ponte WebView
format/epub/            Metadados EPUB e importação
format/pdf/             Metadados PDF, renderização e texto
format/convert/         Conversão compartilhada de documentos
dictionary/             API de dicionário, implementação StarDict incluída e consultas on-line
build-logic/            Convenções compartilhadas de compilação Android e Kotlin
```

O aplicativo usa Kotlin, Jetpack Compose, Material 3, Room, DataStore, Hilt, WorkManager e uma seleção incorporada do mecanismo de renderização EPUB do Foliate.

<a id="documentation"></a>

## Documentação

- [Site do Vayana](https://ranjithj.in/vayana/)
- [Guia do usuário e ajuda detalhada dos recursos](docs/USER_GUIDE.pt.md)
- [Índice da documentação](docs/README.md)
- [Comportamento dos recursos e regras invariantes](docs/FEATURES.md)
- [Configuração e uso do aplicativo Wear OS](docs/WEAR_OS.md)
- [Mudanças desde a v0.87](docs/releases/UNRELEASED.md)
- [Configuração da sincronização com o GitHub](docs/GITHUB_SYNC_SETUP.md)
- [Decisões de arquitetura e implementação](docs/DECISIONS.md)
- [Histórico de alterações do banco de dados](docs/DATABASE_CHANGELOG.md)
- [Projeto da sincronização com o GitHub](docs/GITHUB_SYNC_IMPLEMENTATION_PLAN.md)
- [Notas da versão v0.87](https://github.com/rjwarrier/Vayana/releases/tag/v0.87)
- [Notas da versão v0.85](docs/releases/v0.85.md)

<a id="feedback"></a>

## Comentários e sugestões

Use o [GitHub Issues](https://github.com/rjwarrier/Vayana/issues) para relatar erros reproduzíveis e solicitar recursos específicos. Ao relatar problemas no leitor ou na sincronização, inclua a versão do Android, o modelo do dispositivo, o perfil de tela e a [exportação de diagnóstico](docs/USER_GUIDE.pt.md#diagnostics-and-support), quando disponível; nunca inclua um token do GitHub ou a frase secreta de sincronização.

<a id="support"></a>

## Apoio

Se o Vayana for útil para você, considere apoiar seu desenvolvimento:

<a href="https://www.buymeacoffee.com/ranjithj"><img alt="Pague-me um café" src="https://img.buymeacoffee.com/button-api/?text=Buy%20me%20a%20coffee&emoji=&slug=ranjithj&button_colour=FFDD00&font_colour=000000&font_family=Bree&outline_colour=000000&coffee_colour=ffffff" height="44"></a>
