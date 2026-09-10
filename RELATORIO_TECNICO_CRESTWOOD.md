# Relatório técnico — Crestwood Roleplay Android

**Data:** 18 de agosto de 2026  
**Pacote Android:** `com.crestwood.rp`  
**Versão de distribuição:** `2.11.0-data1`

## 1. Objetivo desta entrega

Esta entrega corrige a abertura do item **Blazer** dentro da roleta de interação. A interface agora é composta exclusivamente pelos arquivos presentes na pasta `web` do pacote `bet_system.rar` fornecido: HTML, CSS, JavaScript, logo e arquivos de áudio. Não foi criada uma página Blazer alternativa, não foi adicionada uma nova logo e não foi mantido o fallback que havia sido incluído em uma tentativa anterior.

> A única adaptação fora dos arquivos da WebView é a configuração do WebView Android para permitir que módulos JavaScript e recursos locais da própria pasta `web` sejam carregados a partir de `file:///android_asset/`.

## 2. WebView Blazer original

Os arquivos abaixo foram extraídos do arquivo fornecido e copiados para `app/src/main/assets/blazer/` sem alteração de conteúdo. A equivalência foi verificada por SHA-256 arquivo a arquivo antes da compilação.

| Arquivo original | Destino no APK | Finalidade |
|---|---|---|
| `web/index.html` | `assets/blazer/index.html` | Ponto de entrada da WebView |
| `web/config.css` | `assets/blazer/config.css` | Configuração visual original |
| `web/logo.png` | `assets/blazer/logo.png` | Marca original do sistema |
| `web/assets/index-IewgXARa.js` | `assets/blazer/assets/index-IewgXARa.js` | Lógica JavaScript original |
| `web/assets/index-DvFj1EuD.css` | `assets/blazer/assets/index-DvFj1EuD.css` | Estilos compilados originais |
| `web/assets/*.mp3` | `assets/blazer/assets/*.mp3` | Áudios originais do sistema |

O item **Blazer** da roleta chama `openInteractionPage("blazer/index.html")` na Activity `SAMP`. Dessa forma, a ação abre diretamente o `index.html` original do pacote dentro do WebView do jogo.

## 3. Ajuste de compatibilidade do Android WebView

O WebView do HUD já tinha JavaScript, armazenamento DOM e acesso a arquivos habilitados. Foi adicionado `setAllowFileAccessFromFileURLs(true)` em `SAMP.java`. Esse ajuste permite que o arquivo `index.html`, aberto localmente, carregue seus módulos JavaScript, CSS, logo e sons localizados na mesma pasta de assets.

O acesso universal entre origens não foi habilitado. A aplicação mantém `setAllowUniversalAccessFromFileURLs(false)`, portanto a compatibilidade foi limitada aos recursos locais do pacote.

## 4. Instalação e data do jogo

O Launcher abre uma tela HTML de atualização antes da Home. A data é baixada através do manifesto HTTPS, validada por SHA-256 e extraída antes de liberar o botão **JOGAR**. O destino final configurado é a pasta pública:

```text
/storage/emulated/0/GTA
```

O instalador remove cópias antigas e resíduos de staging antes de iniciar uma nova instalação, mede o espaço no volume público existente e utiliza download parcial com `Range` para retomar transferências interrompidas.

## 5. HUD e interações

O HUD é fornecido em HTML, CSS, JavaScript e SVG. As funcionalidades atualmente integradas incluem a logo Crestwood fornecida, estado de vida, escudo, fome, sede e energia, a roleta de interação, o menu de configurações, o chat nativo e as entradas de Blazer e Celular.

Os controles veiculares personalizados — velocímetro, direção, aceleração e freio — foram removidos conforme solicitado. O celular foi configurado como uma sobreposição pequena no canto inferior direito, com fundo transparente fora do aparelho e uma grade compacta de aplicações.

## 6. Inicialização nativa e estabilidade

O caminho da data é configurado antes da criação nativa da Activity do jogo. A GameThread aguarda a confirmação do instalador, e a ponte JNI recebe o diretório de dados antes da primeira inicialização do motor. Essa ordem evita que o motor tente abrir recursos GTA em uma pasta diferente da instalada pelo Launcher.

## 7. Validações executadas nesta entrega

| Verificação | Resultado |
|---|---|
| Arquivos WebView integrados idênticos ao pacote | Confirmado por SHA-256 |
| Ação Blazer na roleta | Direcionada ao `index.html` original |
| Permissão de recursos locais para WebView | Habilitada para arquivos da própria origem |
| Compilação Gradle release | Concluída com sucesso |
| Assinatura APK | V2 válida |

## 8. Limite de validação

A compilação, a assinatura e a igualdade dos arquivos da WebView foram validadas no ambiente de build. A abertura gráfica final precisa ser confirmada no aparelho Android, pois depende da implementação de Android System WebView instalada no dispositivo e da resposta do servidor ao qual o cliente SAMP se conecta.

## 9. Arquivos principais envolvidos

| Caminho | Papel |
|---|---|
| `app/src/main/java/com/gta/game/SAMP.java` | Activity, ponte do HUD e configuração do WebView |
| `app/src/main/java/com/gta/game/HudWebView.java` | Encaminhamento de toques sobre o jogo |
| `app/src/main/assets/hud/` | HUD, roleta e eventos JavaScript |
| `app/src/main/assets/blazer/` | WebView Blazer original fornecida |
| `app/src/main/assets/phone/index.html` | Celular de canto |
| `app/src/main/assets/config/index.html` | Painel de configurações |
| `app/src/main/java/com/rockstargames/oswrapper/DataInstaller.java` | Download, validação e extração da data |

## 10. Resumo de entrega

O APK desta entrega preserva o sistema Blazer recebido, usa seus recursos originais dentro do SAMP e mantém a integração limitada à abertura local pela roleta. O código de servidor ou de cliente externo presente no pacote não foi executado pelo processo de build.
